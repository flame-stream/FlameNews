package com.expleague.server;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.control.roster.RosterQuery;
import com.expleague.xmpp.model.stanza.Iq;
import com.expleague.xmpp.model.stanza.Presence;
import com.expleague.xmpp.model.stanza.Stanza;

import java.util.HashMap;
import java.util.Map;

public class UserAgent extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(context().system(), self());
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
      .match(Presence.class, this::deliverPresence)
      .match(Iq.class, this::rosterPush)
      .match(Stanza.class, this::deliver)
      .build();
  }

  private void onStatus(ConnStatus status) {
    if (status.connected) {
      connections.put(status.resource, sender());
    } else {
      connections.remove(status.resource);
      if (connections.isEmpty()) {
        log.debug("All connections are closed, stopping user agent");
        xmpp.tell(new Presence(bare, false), self());
        context().stop(self());
      }
    }
  }

  private void rosterPush(Iq<?> iq) {
    if (iq.get() instanceof RosterQuery && iq.type() == Iq.IqType.SET)  {
      broadcast(iq);
    } else {
      deliver(iq);
    }
  }

  private void deliverPresence(Presence presence) {
    connections.forEach((resource, ref) -> {
      final Stanza to = ((Presence) presence.copy()).to(bare.resource(resource));
      ref.tell(to, sender());
    });
  }

  private void broadcast(Stanza message) {
    connections.forEach((resource, ref) -> {
      final Stanza to = ((Presence) message.copy()).to(bare.resource(resource));
      ref.tell(to, sender());
    });
  }

  private void deliver(Stanza stanza) {
    if (!online()) {
      log.info("User is offline, discarding stanza. id = '{}'", stanza.id() == null ? "null" : stanza.id());
      return;
    }

    final String resource = stanza.to().resource();
    final ActorRef connection;

    if (resource.isEmpty() || !connections.containsKey(resource)) {
      final String r = connections.keySet()
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("There should be at least one connection"));
      connection = connections.get(r);
      stanza.to(bare.resource(r));
    } else {
      connection = connections.get(resource);
      stanza.to(bare.resource(resource));
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
