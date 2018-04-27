package com.expleague.server.xmpp.phase;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.io.TcpMessage;
import akka.japi.pf.ReceiveBuilder;
import com.expleague.server.xmpp.XMPPClientConnection;
import com.expleague.xmpp.model.Item;
import com.expleague.xmpp.model.control.Open;

/**
 * User: solar
 * Date: 08.12.15
 * Time: 17:15
 */
public abstract class XMPPPhase extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(context().system(), self());
  private final ActorRef connection;

  protected XMPPPhase(ActorRef connection) {
    this.connection = connection;
  }

  @Override
  public Receive createReceive() {
    return ReceiveBuilder.create()
      .match(Open.class, this::onOpen)
      .build();
  }

  @Override
  public void unhandled(Object msg) {
    log.warning("Unexpected xmpp item: {}", msg);
  }

  protected void answer(Item item) {
    connection.tell(item, self());
  }

  private void onOpen(Open ignore) {
    connection.tell(ignore, self());
    open();
  }

  protected abstract void open();

  public void last(Item msg, XMPPClientConnection.ConnectionState state) {
    log.debug("Finishing phase {}", self().path().name());
    connection.tell(TcpMessage.suspendReading(), self());
    connection.tell(msg, self());
    connection.tell(state, self());
  }
}
