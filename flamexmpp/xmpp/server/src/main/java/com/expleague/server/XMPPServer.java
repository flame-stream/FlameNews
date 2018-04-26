package com.expleague.server;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.io.TcpSO;
import akka.japi.pf.ReceiveBuilder;
import com.expleague.server.xmpp.XMPPClientConnection;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 24.12.15
 * Time: 14:47
 */
public class XMPPServer extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(context().system(), self());
  private final ActorRef xmpp;

  public XMPPServer(ActorRef xmpp) {
    this.xmpp = xmpp;
  }

  public static Props props(ActorRef xmpp) {
    return Props.create(XMPPServer.class, xmpp);
  }

  @Override
  public void preStart() {
    final ActorRef tcp = Tcp.get(context().system()).manager();
    tcp.tell(
      TcpMessage.bind(
        self(),
        new InetSocketAddress("0.0.0.0", 5222),
        100,
        Collections.singletonList(TcpSO.keepAlive(true)),
        false
      ),
      self()
    );
  }

  @Override
  public Receive createReceive() {
    return ReceiveBuilder.create()
      .match(Tcp.Event.class, this::onTcp)
      .build();
  }

  private void onTcp(Tcp.Event msg) {
    log.debug(String.valueOf(msg));
    if (msg instanceof Tcp.CommandFailed) {
      context().stop(self());
    } else if (msg instanceof Tcp.Connected) {
      context().actorOf(XMPPClientConnection.props(sender(), xmpp));
    }
  }
}
