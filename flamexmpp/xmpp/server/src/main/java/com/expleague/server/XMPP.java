package com.expleague.server;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.stanza.Iq;
import com.expleague.xmpp.model.stanza.Message;
import com.expleague.xmpp.model.stanza.Presence;
import com.expleague.xmpp.model.stanza.data.Err;
import scala.Option;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

public class XMPP extends AbstractActor {
  private static final Logger log = Logger.getLogger(XMPP.class.getName());
  private final JID hostJID = JID.parse(XMPPServerApplication.config().domain());

  private ActorRef services;

  @Override
  public void preStart() {
    services = context().actorOf(XMPPServices.props(), "services");
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
    if (iq.isBroadcast()) {
      services.forward(iq, context());
    } else {
      final Optional<ActorRef> child = getContext().findChild(iq.to().local());
      if (!child.isPresent()) {
        log.warning("User is not connected");
        sender().tell(Iq.answer(iq, new Err(
            Err.Cause.SERVICE_UNAVAILABLE,
            Err.ErrType.CANCEL,
            "User is offline or doesn't exists"
          )
        ), self());
      } else {
        child.get().forward(iq, context());
      }
    }
  }

  private void onMessage(Message message) {
    final Optional<ActorRef> child = getContext().findChild(message.to().local());
    if (!child.isPresent()) {
      log.warning("User is not connected");
      final Message error = new Message(
        hostJID,
        message.from(),
        new Err(Err.Cause.SERVICE_UNAVAILABLE, Err.ErrType.CANCEL, "User is offline or doesn't exists")
      );
      error.id(message.id());
      sender().tell(error, self());
    } else {
      child.get().forward(message, context());
    }
  }

  private void onPresence(Presence presence) {
    log.info("Presence: " + presence);
  }

  private void onJID(JID jid) {
    final ActorRef sender = sender();
    findOrAllocate(jid).thenAccept(agent -> sender.tell(agent, self()));
  }

  private CompletionStage<ActorRef> findOrAllocate(JID jid) {
    final String id = jid.local();
    final Option<ActorRef> child = context().child(id);
    if (child.isDefined()) {
      return CompletableFuture.completedFuture(child.get());
    }

    // TODO: check whether user with such id exists or not
    return CompletableFuture.completedFuture(context().actorOf(UserAgent.props(jid.bare(), self()), id));
  }
}
