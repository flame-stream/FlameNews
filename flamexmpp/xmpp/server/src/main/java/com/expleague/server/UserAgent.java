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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserAgent extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(context().system(), self());
  private final ActorRef xmpp;
  private final JID bare;

  private final List<Presence> pendingSubscriptions = new ArrayList<>();

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
      .match(Presence.class, this::deliver)
      .match(Iq.class, this::rosterPush)
      .match(Stanza.class, this::deliver)
      .build();
  }

  private void onStatus(ConnStatus status) {
    if (status.connected) {
      connections.put(status.resource, sender());
      if (connections.size() == 1) {
        pendingSubscriptions.forEach(p -> sender().tell(p, self()));
        pendingSubscriptions.clear();
      }
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
    if (iq.get() instanceof RosterQuery && iq.type() == Iq.IqType.SET) {
      connections.forEach((resource, ref) -> {
        final Stanza to = ((Stanza) iq.copy()).to(bare.resource(resource));
        ref.tell(to, sender());
      });
    } else {
      deliver(iq);
    }
  }

  private void deliver(Presence presence) {
    if (presence.type() == Presence.PresenceType.SUBSCRIBED) {
      for (String resource : connections.keySet()) {
        xmpp.tell(new Presence(bare.resource(resource), presence.from(), Presence.PresenceType.AVAILABLE), self());
        xmpp.tell(new Presence(bare, presence.from(), Presence.PresenceType.PROBE), self());
      }
      connections.forEach((resource, ref) -> ref.tell(presence, sender()));
    } else if (presence.type() == Presence.PresenceType.PROBE) {
      connections.keySet().forEach(resource -> {
        sender().tell(new Presence(bare.resource(resource), presence.from(), Presence.PresenceType.AVAILABLE), self());
      });
    } else {
      connections.forEach((resource, ref) -> {
        final Presence to = ((Presence) presence.copy()).to(bare.resource(resource));
        ref.tell(to, sender());
      });
    }

    if (connections.isEmpty() && presence.type() == Presence.PresenceType.SUBSCRIBE) {
      log.info("There are no available connections for adding presence to pending list");
      pendingSubscriptions.add(presence);
    }
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
