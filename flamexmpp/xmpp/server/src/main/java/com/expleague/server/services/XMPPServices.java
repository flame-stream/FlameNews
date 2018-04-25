package com.expleague.server.services;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.expleague.xmpp.model.control.roster.RosterQuery;
import com.expleague.xmpp.model.stanza.Iq;
import com.expleague.xmpp.model.stanza.data.Err;

import java.util.HashMap;
import java.util.Map;

/**
 * User: solar
 * Date: 15.12.15
 * Time: 13:20
 */
public class XMPPServices extends AbstractActor {
  private final Map<String, ActorRef> knownServices = new HashMap<>();

  public XMPPServices(ActorRef roster) {
    knownServices.put(RosterQuery.NS, roster);
  }

  public static Props props(ActorRef roster) {
    return Props.create(XMPPServices.class, roster);
  }

  @Override
  public Receive createReceive() {
    return ReceiveBuilder.create()
      .match(Iq.class, this::onIq)
      .build();
  }

  private void onIq(Iq<?> iq) {
    final String ns = iq.serviceNS();
    if (knownServices.containsKey(ns)) {
      final ActorRef service = knownServices.get(iq.serviceNS());
      service.forward(iq, context());
    } else {
      sender().tell(
        Iq.answer(iq, new Err(Err.Cause.SERVICE_UNAVAILABLE, Err.ErrType.CANCEL, "Service is not supported")),
        self()
      );
    }
  }
}
