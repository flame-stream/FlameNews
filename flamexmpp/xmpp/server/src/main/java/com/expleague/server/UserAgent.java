package com.expleague.server;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.stanza.Iq;
import com.expleague.xmpp.model.stanza.Message;
import com.expleague.xmpp.model.stanza.Presence;
import com.expleague.xmpp.model.stanza.Stanza;
import com.expleague.xmpp.model.stanza.data.Err;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class UserAgent extends AbstractActor {
  private static final Logger log = Logger.getLogger(UserAgent.class.getName());
  private final ActorRef xmpp;
  private final JID bare;

  // Resource -> Connection
  private final Map<String, ActorRef> connections = new HashMap<>();

  public UserAgent(JID bare, ActorRef xmpp) {
    this.bare = bare;
    this.xmpp = xmpp;
  }

  public static Props props(JID bare, ActorRef xmpp) {
    return Props.create(UserAgent.class, bare, xmpp);
  }

  @Override
  public Receive createReceive() {
    return ReceiveBuilder.create()
      .match(ConnStatus.class, this::onStatus)
      .match(Message.class, this::onMessage)
      .match(Iq.class, this::onIq)
      .match(Presence.class, this::onPresence)
      .build();
  }

  private void onStatus(ConnStatus status) {
    if (status.connected) {
      connections.put(status.resource, sender());
    } else {
      connections.remove(status.resource);
      if (connections.isEmpty()) {
        log.fine("All connections are closed, stopping user agent");
        xmpp.tell(new Presence(bare, false), self());
        context().stop(self());
      }
    }
  }

  private void onPresence(Presence presence) {
    if (!presence.from().bareEq(presence.from())) {
      connections.values().forEach(c -> c.forward(presence, context()));
    }
  }

  private void onIq(Iq<?> iq) {
    if (!online()) {
      final Iq<Err> error = Iq.answer(iq, new Err(
          Err.Cause.SERVICE_UNAVAILABLE,
          Err.ErrType.CANCEL,
          "User is offline or doesn't exists"
        )
      );
      error.id(iq.id());
      sender().tell(error, self());
      return;
    }

    deliverStanza(iq);
  }

  private void onMessage(Message message) {
    if (!online()) {
      final Message error = new Message(
        bare,
        message.from(),
        new Err(Err.Cause.SERVICE_UNAVAILABLE, Err.ErrType.CANCEL, "User is offline or doesn't exists")
      );
      error.id(message.id());
      sender().tell(error, self());
      return;
    }

    deliverStanza(message);
  }

  private void deliverStanza(Stanza stanza) {
    final String resource = stanza.to().resource();
    final ActorRef connection;

    if (resource.isEmpty() || !connections.containsKey(resource)) {
      connection = connections.values()
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("There should be at least one connection"));
    } else {
      connection = connections.get(resource);
    }

    connection.forward(stanza, context());
  }

  private boolean online() {
    return !connections.isEmpty();
  }

  public static class ConnStatus {
    public final boolean connected;
    public final String resource;

    public ConnStatus(boolean connected, String resource) {
      this.connected = connected;
      this.resource = resource;
    }
  }
}
