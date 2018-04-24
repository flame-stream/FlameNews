package com.expleague.server;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import com.expleague.server.services.RosterService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.stanza.Iq;
import com.expleague.xmpp.model.stanza.Message;
import com.expleague.xmpp.model.stanza.Presence;
import com.expleague.xmpp.model.stanza.Presence.PresenceType;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class XMPP extends AbstractActor {
  private static final Logger log = Logger.getLogger(XMPP.class.getName());
  private final JID hostJID = JID.parse(XMPPServerApplication.config().domain());

  private final ActorRef services;
  private final ActorRef roster;

  public XMPP() {
    this.roster = context().actorOf(RosterService.props(), "roster");
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
      .build();
  }

  private void onIq(Iq<?> iq) {
    if (iq.isBroadcast() || iq.to().bareEq(hostJID)) {
      services.forward(iq, context());
    } else {
      findOrAllocate(iq.to()).forward(iq, context());
    }
  }

  private void onMessage(Message message) {
    if (message.to() == null) {
      log.warning("Message without destination " + message);
      return;
    }

    findOrAllocate(message.to()).forward(message, context());
  }

  private void onPresence(Presence presence) {
    if (presence.to() == null) {
      // Broadcast to subscribers
      PatternsCS.ask(roster, presence.from(), 10000)
        .thenApply(list -> (List<JID>) list)
        .thenAccept(subscribers -> {
          subscribers.forEach(subscriber -> {
            findOrAllocate(subscriber).tell(presence, self());
          });
        });
    } else if (presence.type() == PresenceType.SUBSCRIBED || presence.type() == PresenceType.UNSUBSCRIBED) {
      roster.tell(presence, self());
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
