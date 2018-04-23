package com.expleague.server.xmpp.phase;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.expleague.server.xmpp.XMPPClientConnection;
import com.expleague.xmpp.model.Features;
import com.expleague.xmpp.model.control.tls.Proceed;
import com.expleague.xmpp.model.control.tls.StartTLS;

/**
 * User: solar
 * Date: 09.12.15
 * Time: 14:38
 */
public class HandshakePhase extends XMPPPhase {

  protected HandshakePhase(ActorRef connection) {
    super(connection);
  }

  public static Props props(ActorRef self) {
    return Props.create(HandshakePhase.class, self);
  }

  @Override
  public Receive createReceive() {
    return super.createReceive().orElse(
      ReceiveBuilder.create()
        .match(StartTLS.class, s -> startTls())
        .build()
    );
  }

  private void startTls() {
    last(new Proceed(), XMPPClientConnection.ConnectionState.STARTTLS);
  }

  @Override
  protected void open() {
    answer(new Features(new StartTLS()));
  }
}
