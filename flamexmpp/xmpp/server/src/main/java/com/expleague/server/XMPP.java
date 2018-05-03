package com.expleague.server;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import com.expleague.server.services.Roster;
import com.expleague.server.services.RosterService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.stanza.Iq;
import com.expleague.xmpp.model.stanza.Message;
import com.expleague.xmpp.model.stanza.Presence;

import java.util.List;
import java.util.Optional;

public class XMPP extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(context().system(), self());

  private final ActorRef services;
  private final ActorRef roster;

  public XMPP() {
    this.roster = context().actorOf(RosterService.props(self(), new Roster.InMemRoster()), "roster");
    this.services = context().actorOf(XMPPServices.props(roster), "services");
  }

  public static Props props() {
    return Props.create(XMPP.class);
  }

  @Override
  public Receive createReceive() {
    return ReceiveBuilder.create()
      .match(Iq.class, this::onIq)
      .match(Message.class, this::onMessage)
      .match(Presence.class, this::onPresence)
      .match(JID.class, this::onJID)
      // Forward
      .match(RosterService.GetSubscribers.class, g -> roster.forward(g, context()))
      .match(RosterService.GetSubsriptions.class, g -> roster.forward(g, context()))
      .build();
  }

  /**
   * If there is no sender than iq is sent from the service to client (e.g., roster)
   * If the destination is full JID (local@domain/resource) it is directed to client
   * <p>
   * In all other cases the destination is the local service
   */
  private void onIq(Iq<?> iq) {
    if (iq.from() == null || iq.to() != null && iq.to().hasResource()) {
      findOrAllocate(iq.to()).forward(iq, context());
    } else {
      services.forward(iq, context());
    }
  }

  private void onMessage(Message message) {
    if (message.to() == null) {
      log.warning("Message without destination {}", message);
      return;
    }

    findOrAllocate(message.to()).forward(message, context());
  }

  private void onPresence(Presence presence) {
    if (presence.to() == null) {
      // Broadcast to subscribers
      PatternsCS.ask(roster, new RosterService.GetSubscribers(presence.from()), 10000)
        .thenApply(list -> (List<JID>) list)
        .thenAccept(subscribers -> {
          subscribers.forEach(subscriber -> {
            findOrAllocate(subscriber).tell(presence, self());
          });
        });
    } else if (presence.type().isSubscriptionManagement()) {
      roster.tell(presence, self());
      // TODO: 4/27/18 check if there is really pending subscription
      findOrAllocate(presence.to()).forward(presence, context());
    } else {
      findOrAllocate(presence.to()).forward(presence, context());
    }
  }

  private void onJID(JID jid) {
    sender().tell(findOrAllocate(jid), self());
  }

  private ActorRef findOrAllocate(JID jid) {
    final String id = jid.local();
    final Optional<ActorRef> child = getContext().findChild(id);
    return child.orElseGet(() -> context().actorOf(UserAgent.props(jid.bare(), self()), id));
  }
}
