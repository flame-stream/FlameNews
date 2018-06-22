package com.expleague.server;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.DeleteMessagesFailure;
import akka.persistence.DeleteMessagesSuccess;
import akka.persistence.RecoveryCompleted;
import com.expleague.server.services.RosterService;
import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.control.receipts.Delivered;
import com.expleague.xmpp.model.control.roster.RosterQuery;
import com.expleague.xmpp.model.stanza.Iq;
import com.expleague.xmpp.model.stanza.Presence;
import com.expleague.xmpp.model.stanza.Stanza;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


public class UserAgent extends AbstractPersistentActor {
  private final LoggingAdapter log = Logging.getLogger(context().system(), self());
  private final JID bareJid;
  private final ActorRef xmpp;

  private final boolean reliable;

  private final Set<String> connected = new HashSet<>();

  public UserAgent(JID bareJid, ActorRef xmpp) {
    this(bareJid, xmpp, false);
  }

  public UserAgent(JID bareJid, ActorRef xmpp, boolean reliable) {
    this.bareJid = bareJid;
    this.xmpp = xmpp;
    this.reliable = reliable;
  }

  public static Props props(JID bare, ActorRef xmpp) {
    return props(bare, xmpp, false);
  }

  public static Props props(JID bare, ActorRef xmpp, boolean reliable) {
    return Props.create(UserAgent.class, bare, xmpp, reliable);
  }

  @Override
  public Receive createReceiveRecover() {
    return ReceiveBuilder.create().matchAny(m -> {}).build();
  }

  @Override
  public Receive createReceive() {
    return ReceiveBuilder.create()
      .match(ConnStatus.class, this::onStatus)
      .match(Presence.class, this::onPresence)
      .match(Iq.class, iq -> iq.get() instanceof RosterQuery && iq.type() == Iq.IqType.SET, this::onRosterPush)
      .match(Stanza.class, s -> {
        if (!reliable) {
          toConn(s);
        } else {
          persist(s, this::toConn);
        }
      })
      .match(Delivered.class, this::onDelivered)
      .build();
  }

  public static class ConnStatus {
    public final String resource;
    public final boolean connected;

    public ConnStatus(boolean connected, String resource) {
      this.connected = connected;
      this.resource = resource;
    }
  }

  private void onStatus(ConnStatus status) {
    final String resource = status.resource;
    final String actorResourceAddr = courierActorName(resource);
    final Optional<ActorRef> optional = getContext().findChild(actorResourceAddr);

    if (status.connected) {
      final ActorRef courier;
      if (optional.isPresent()) {
        log.warning("Concurrent connectors for the same resource: {} for {} !", resource, bareJid);
        courier = optional.get();
        courier.tell(PoisonPill.getInstance(), self());
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ignore) {
        }
        self().forward(status, context());
      } else {
        connected.add(resource);
        if (reliable) {
          courier = context().actorOf(
            ReliableCourier.props(bareJid.resource(status.resource), sender(), xmpp),
            actorResourceAddr
          );
        } else {
          courier = context().actorOf(
            Courier.props(bareJid.resource(status.resource), sender(), xmpp),
            actorResourceAddr
          );
        }
        requestPresences();
        sender().tell(courier, self());
      }
    } else {
      optional.ifPresent(actorRef -> context().stop(actorRef));

      connected.remove(status.resource);
      if (!online()) {
        log.debug("All connections are closed, stopping user agent");
        xmpp.tell(new Presence(bareJid, false), self());
      }
    }
  }

  private void onPresence(Presence presence) {
    if (presence.type() == Presence.PresenceType.SUBSCRIBED) {
      for (String resource : connected) {
        xmpp.tell(new Presence(bareJid.resource(resource), presence.from(), Presence.PresenceType.AVAILABLE), self());
        xmpp.tell(new Presence(bareJid, presence.from(), Presence.PresenceType.PROBE), self());
      }
      couriers().forEach(ref -> ref.tell(presence, sender()));
    } else if (presence.type() == Presence.PresenceType.PROBE) {
      connected.forEach(resource -> {
        sender().tell(new Presence(bareJid.resource(resource), presence.from(), true), self());
      });
    } else {
      couriers().forEach(ref -> ref.tell(presence.to(bareJid), sender()));
    }

    if (!online() && presence.type() == Presence.PresenceType.SUBSCRIBE) {
      log.info("There are no available connections for adding presence to pending list");
      persist(presence, this::toConn);
    }
  }

  private void onRosterPush(Iq<?> iq) {
    connected.forEach(resource -> {
      final Stanza to = ((Stanza) iq.copy()).to(bareJid.resource(resource));
      toConn(to);
    });
  }

  private void onDelivered(Delivered delivered) {
    // Why do we need to store _delivered_ in this actor
    if (delivered.resource() != null) { // delivered to the concrete device
      persist(delivered, d -> {});
    }
  }

  @Override
  public String persistenceId() {
    return bareJid.bare().getAddr();
  }

  private String courierActorName(String resource) {
    return resource.isEmpty() ? "@@empty@@" : resource.replace('/', '&');
  }

  private void toConn(Stanza stanza) {
    // TODO: 5/4/18 Stamp with to
    final String resource = stanza.to().resource();
    if (!resource.isEmpty()) {
      final Optional<ActorRef> child = getContext().findChild(courierActorName(resource));
      if (child.isPresent()) {
        child.get().forward(stanza, context());
      } else {
        log.info("Stanza {}  was not delivered: no courier found", stanza.xmlString());
      }
    } else {
      final List<ActorRef> couriers = couriers();
      if (couriers.isEmpty()) {
        log.info("Stanza {}  was not delivered: no courier found", stanza.xmlString());
      }

      // Unlike standard, we send messages with no destination to random courier
      couriers.get(0).forward(stanza.to(bareJid), context());
    }
  }

  private boolean online() {
    return !connected.isEmpty();
  }

  private void requestPresences() {
    PatternsCS.ask(xmpp, new RosterService.GetSubsriptions(bareJid), 10000)
      .thenApply(a -> (List<JID>) a)
      .thenAccept(subscriptions -> {
        for (JID s : subscriptions) {
          xmpp.tell(new Presence(bareJid, s, Presence.PresenceType.PROBE), self());
        }
      });
  }

  private List<ActorRef> couriers() {
    final List<ActorRef> result = new ArrayList<>();
    for (ActorRef ref : getContext().getChildren()) {
      result.add(ref);
    }
    return result;
  }

  private static final class Courier extends AbstractActor {
    private final ActorRef connection;
    private final JID resourceJID;
    private final ActorRef xmpp;

    private Courier(JID resourceJID, ActorRef connection, ActorRef xmpp) {
      this.connection = connection;
      this.resourceJID = resourceJID;
      this.xmpp = xmpp;
    }

    public static Props props(JID resourceJID, ActorRef connection, ActorRef xmpp) {
      return Props.create(Courier.class, resourceJID, connection, xmpp);
    }

    @Override
    public Receive createReceive() {
      return ReceiveBuilder.create()
        .match(Delivered.class, d -> {})
        .matchAny(a -> connection.forward(a, context()))
        .build();
    }
  }

  private static final class ReliableCourier extends AbstractPersistentActor {
    private final LoggingAdapter log = Logging.getLogger(context().system(), self());

    private final ActorRef xmpp;
    private final ActorRef connection;
    private final JID resourceJID;
    private final Deque<Stanza> deliveryQueue = new ArrayDeque<>();
    private final Set<String> confirmationAwaitingStanzas = new HashSet<>();

    private final Map<String, Stanza> inFlight = new HashMap<>();
    //private final Subscription subscription;
    private int totalMessages = 0;

    private final Set<String> delivered = new HashSet<>();

    private ReliableCourier(JID resourceJID, ActorRef connection, ActorRef xmpp) {
      this.connection = connection;
      this.resourceJID = resourceJID;
      this.xmpp = xmpp;
    }

    public static Props props(JID resourceJID, ActorRef connection, ActorRef xmpp) {
      return Props.create(ReliableCourier.class, resourceJID, connection, xmpp);
    }

    @Override
    public Receive createReceiveRecover() {
      return ReceiveBuilder.create()
        .match(Stanza.class, stanza -> {
          totalMessages++;
          deliveryQueue.add(stanza);

        })
        .match(Delivered.class, d -> delivered.add(d.id()))
        .match(RecoveryCompleted.class, recoveryCompleted -> {
          deliveryQueue.removeIf(stanza -> delivered.contains(stanza.id()));
          delivered.clear();
          if (totalMessages > 100 && deliveryQueue.size() < 50) {
            // TODO: snapshot
            deleteMessages(lastSequenceNr()); // clear the mailbox
          }

          nextChunk();
          xmpp.tell(new Presence(resourceJID, true), self());
        })
        .build();
    }

    private boolean border = false;

    private void nextChunk() {
      if (border && !confirmationAwaitingStanzas.isEmpty()) {
        return;
      }

      border = false;
      Stanza poll;
      do {
        poll = deliveryQueue.poll();
        if (poll == null) {
          break;
        }
        confirmationAwaitingStanzas.add(poll.id());
        connection.tell(poll, self());
        inFlight.put(poll.id(), poll);
      }
      while (!(border = isDeliveryOrderRequired(poll)));
    }

    private boolean isDeliveryOrderRequired(Stanza stanza) {
      return false;
    }

    @Override
    public Receive createReceive() {
      return ReceiveBuilder.create()
        .match(Presence.class, p -> {
          connection.forward(p, context());
        })
        .match(Stanza.class, s -> {
          deliveryQueue.add(s);
          nextChunk();
        })
        .match(Delivered.class, d -> {
          final String id = d.id();
          if (confirmationAwaitingStanzas.remove(id)) {

            // What is this code supposed to do?
            // Something related to notifications?

            // final Stanza remove = inFlight.remove(id);
            // XMPP.whisper(remove.from(), delivered, context());

            nextChunk();
            context().parent().forward(d, context());
            // NotificationsManager.delivered(id, connectedDevice, context());
          } else {
            log.warning("Unexpected delivery message id " + d.id());
          }
        })
        .match(DeleteMessagesSuccess.class, success -> {
          deliveryQueue.forEach(stanza -> persist(stanza, s -> {}));
          totalMessages = deliveryQueue.size();
        })
        .match(DeleteMessagesFailure.class, failure -> {
          log.warning("Unable to clear out user mailbox {}", resourceJID);
        })
        .build();
    }

    @Override
    public String persistenceId() {
      return resourceJID.bare().getAddr();
    }
  }
}
