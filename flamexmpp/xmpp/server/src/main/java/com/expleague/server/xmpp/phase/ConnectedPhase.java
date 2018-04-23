package com.expleague.server.xmpp.phase;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import com.expleague.server.UserAgent;
import com.expleague.server.XMPPServerApplication;
import com.expleague.xmpp.model.Features;
import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.control.Bind;
import com.expleague.xmpp.model.control.Close;
import com.expleague.xmpp.model.stanza.Iq;
import com.expleague.xmpp.model.stanza.Stanza;
import org.jetbrains.annotations.Nullable;
import scala.concurrent.duration.Duration;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 16:37
 */
public class ConnectedPhase extends XMPPPhase {
  private static final Logger log = Logger.getLogger(ConnectedPhase.class.getName());
  private final ActorRef xmpp;

  private boolean bound = false;

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
      log.fine("Connection to " + jid + " is now closed");
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

  private void onStanza(Stanza stanza) throws ExecutionException, InterruptedException {
    if (stanza.from() == null) {
      stanza.from(jid);
    }

    if (!bound) {
      final JID jidWithResource = tryBind(stanza);
      if (jidWithResource != null) {
        this.jid = jidWithResource;
        bound = true;
        userAgent = (ActorRef) PatternsCS.ask(
          xmpp,
          jidWithResource,
          Timeout.apply(Duration.create(60, TimeUnit.SECONDS))
        ).toCompletableFuture().get();
        userAgent.tell(new UserAgent.ConnStatus(true, jid.resource()), self());
      } else {
        log.warning("There shouldn't be any messages before bind. Got " + stanza);
        return;
      }
    }

    if (jid.bareEq(stanza.to())) {
      // incoming
      answer(stanza);
    } else {
      // outgoing
      stanza.from(jid);
      xmpp.tell(stanza, self());
    }
  }

  @Nullable
  private JID tryBind(Stanza stanza) {
    if (stanza instanceof Iq) {
      final Iq<?> iq = ((Iq<?>) stanza);
      if (iq.type() == Iq.IqType.SET && iq.get() instanceof Bind) {
        final Bind payload = ((Bind) iq.get());
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
        return jid;
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  private void onClose(Close close) {
    if (userAgent != null) {
      userAgent.tell(new UserAgent.ConnStatus(false, jid.resource()), self());
    }
  }
}
