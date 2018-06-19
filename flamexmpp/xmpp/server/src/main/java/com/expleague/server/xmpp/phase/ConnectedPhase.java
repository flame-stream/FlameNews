package com.expleague.server.xmpp.phase;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import com.expleague.server.UserAgent;
import com.expleague.server.XMPPServerApplication;
import com.expleague.xmpp.model.Features;
import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.control.Bind;
import com.expleague.xmpp.model.control.Close;
import com.expleague.xmpp.model.control.receipts.Delivered;
import com.expleague.xmpp.model.control.receipts.Received;
import com.expleague.xmpp.model.control.receipts.Request;
import com.expleague.xmpp.model.stanza.Iq;
import com.expleague.xmpp.model.stanza.Message;
import com.expleague.xmpp.model.stanza.Presence;
import com.expleague.xmpp.model.stanza.Stanza;
import org.jetbrains.annotations.Nullable;
import scala.concurrent.duration.Duration;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 16:37
 */
public class ConnectedPhase extends XMPPPhase {
  private final LoggingAdapter log = Logging.getLogger(context().system(), self());
  private final ActorRef xmpp;

  private boolean bound = false;
  private long clientTsDiff;
  private boolean synced;

  // Is updated upon bind
  private JID jid;

  @Nullable
  private ActorRef userAgent;

  public ConnectedPhase(ActorRef connection, String authId, ActorRef xmpp) {
    super(connection);
    this.xmpp = xmpp;
    this.jid = new JID(authId, XMPPServerApplication.config().domain(), null);
  }

  public static Props props(ActorRef self, String id, ActorRef xmpp) {
    return Props.create(ConnectedPhase.class, self, id, xmpp);
  }

  @Override
  public void postStop() {
    if (userAgent != null) {
      userAgent.tell(new UserAgent.ConnStatus(false, jid.resource()), self());
      log.debug("Connection to {} is now closed", jid);
    }
  }

  @Override
  public Receive createReceive() {
    return super.createReceive().orElse(
      ReceiveBuilder.create()
        .match(Stanza.class, this::onStanza)
        .match(Close.class, this::onClose)
        .build()
    );
  }

  @Override
  public void open() {
    answer(new Features(new Bind()));
  }

  /**
   * All stanzas originated at server including responses from services
   * MUST have {@code from} attribute in format {@code domainpart}
   * <p>
   * All stanzas directed to client
   * MUST have {@code to} attribute set to full (user@domain/resource) JID
   * <p>
   * This is done so we can differentiate incoming and outgoing stanzas
   */
  private void onStanza(Stanza stanza) throws ExecutionException, InterruptedException {
    if (!bound && !tryBind(stanza)) {
      log.warning("There shouldn't be any messages before bind. Got {}", stanza);
      return;
    }

    if (stanza.to().bareEq(jid)) {
      // to connection
      if (stanza instanceof Message) {
        answer(tryRequestMessageReceipt((Message) stanza));
      } else {
        answer(stanza);
      }
    } else {
      // from connection
      if (stanza instanceof Message) {
        final Message message = (Message) stanza;
        final Message.Timestamp ts = new Message.Timestamp(synced ? message.ts() + clientTsDiff : System.currentTimeMillis());
        message.append(ts);
        tryProcessMessageReceipt(message);
      }

      if (!isDeliveryReceipt(stanza)) {
        xmpp.tell(stampWithFrom(stanza), self());
      }
    }
  }

  private boolean tryBind(Stanza stanza) throws ExecutionException, InterruptedException {
    if (stanza instanceof Iq) {
      final Iq<?> iq = ((Iq<?>) stanza);
      if (iq.type() == Iq.IqType.SET && iq.get() instanceof Bind) {
        if (iq.hasTs()) { //ts diff between client and server
          clientTsDiff = System.currentTimeMillis() - iq.ts();
          synced = true;
        }
        else synced = false;
        final Bind payload = (Bind) iq.get();
        final String resource;
        {
          final String providedResource = payload.resource();
          if (providedResource != null && !providedResource.isEmpty()) {
            resource = providedResource;
          } else {
            resource = UUID.randomUUID().toString();
          }
        }
        jid = jid.resource(resource);
        answer(Iq.answer(iq, new Bind(jid)));

        bound = true;
        userAgent = (ActorRef) PatternsCS.ask(
          xmpp,
          jid,
          Timeout.apply(Duration.create(60, TimeUnit.SECONDS))
        ).toCompletableFuture().get();
        userAgent.tell(new UserAgent.ConnStatus(true, jid.resource()), self());
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  private Stanza tryRequestMessageReceipt(Message message) {
    if (!message.has(Received.class) && !message.has(Request.class)) {
      message.append(new Request());
    }
    return message;
  }

  private void tryProcessMessageReceipt(Message message) {
    if (message.has(Received.class)) {
      final String messageId = message.get(Received.class).id();
      userAgent.tell(new Delivered(messageId, jid.bare(), jid.resource()), self());
    } else if (message.has(Request.class)) {
      final Message ack = new Message(message.from(), new Received(message.id()));
      answer(ack);
      message.remove(Request.class);
    }
  }

  private boolean isDeliveryReceipt(Stanza stanza) {
    return stanza instanceof Message && ((Message) stanza).has(Received.class);
  }

  private Stanza stampWithFrom(Stanza stanza) {
    if (stanza instanceof Presence
      && ((Presence) stanza).type() != null
      && ((Presence) stanza).type().isSubscriptionManagement()) {
      stanza.from(jid.bare());
    } else {
      stanza.from(jid);
    }

    return stanza;
  }

  private void onClose(Close close) {
    if (userAgent != null) {
      userAgent.tell(new UserAgent.ConnStatus(false, jid.resource()), self());
    }
  }
}
