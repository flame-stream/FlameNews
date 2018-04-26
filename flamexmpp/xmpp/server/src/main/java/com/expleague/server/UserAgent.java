package com.expleague.server;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.stanza.Presence;
import com.expleague.xmpp.model.stanza.Stanza;

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
      .match(Presence.class, this::deliverPresence)
      .match(Stanza.class, this::deliverStanza)
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

  private void deliverPresence(Presence presence) {
    connections.forEach((resource, ref) -> {
      final Stanza to = ((Presence) presence.copy()).to(bare.resource(resource));
      ref.tell(to, sender());
    });
  }

  private void deliverStanza(Stanza stanza) {
    if (!online()) {
      log.info("User is offline, discarding stanza. id = '"
        + (stanza.id() == null ? "null" : stanza.id())
        + "'");
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
