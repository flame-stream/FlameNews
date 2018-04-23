package com.expleague.server.xmpp.phase;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.expleague.server.xmpp.XMPPClientConnection;
import com.expleague.xmpp.model.Features;
import com.expleague.xmpp.model.control.register.Register;
import com.expleague.xmpp.model.control.register.RegisterQuery;
import com.expleague.xmpp.model.control.sasl.Auth;
import com.expleague.xmpp.model.control.sasl.Challenge;
import com.expleague.xmpp.model.control.sasl.Failure;
import com.expleague.xmpp.model.control.sasl.Mechanisms;
import com.expleague.xmpp.model.control.sasl.Response;
import com.expleague.xmpp.model.control.sasl.Success;
import com.expleague.xmpp.model.stanza.Iq;
import com.expleague.xmpp.model.stanza.Iq.IqType;
import com.expleague.xmpp.model.stanza.data.Err;

import javax.security.sasl.AuthenticationException;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 10.12.15
 * Time: 16:03
 */
@SuppressWarnings("unused")
public class AuthorizationPhase extends XMPPPhase {
  private static final Logger log = Logger.getLogger(AuthorizationPhase.class.getName());
  private final Mechanisms auth;

  private final Consumer<String> authorizedCallback;
  private SaslServer sasl;

  public AuthorizationPhase(ActorRef connection, Consumer<String> authorizedCallback) {
    super(connection);
    this.authorizedCallback = authorizedCallback;
    auth = new Mechanisms();
    auth.fillKnownMechanisms();
  }

  public static Props props(ActorRef connection, Consumer<String> authorizedCallback) {
    return Props.create(AuthorizationPhase.class, connection, authorizedCallback);
  }

  @Override
  public Receive createReceive() {
    return super.createReceive().orElse(
      ReceiveBuilder.create()
        .match(Iq.class, this::onRequest)
        .match(Response.class, this::onResponse)
        .match(Auth.class, this::onAuth)
        .build()
    );
  }

  @Override
  protected void open() {
    answer(new Features(
      auth,
      new Register()
    ));
  }

  private void onRequest(Iq<RegisterQuery> request) {
    final RegisterQuery query = request.get();
    if (query != null) {
      if (request.type() == IqType.GET && query.isEmpty()) {
        answer(Iq.answer(request, RegisterQuery.requiredFields()));
      } else if (request.type() == IqType.SET && !query.isEmpty()) {
        try {
          log.fine("Registered: " + request);
          // TODO: 4/15/18  
          answer(Iq.answer(request));
        } catch (IllegalArgumentException integrity) {
          answer(Iq.answer(request, new Err(Err.Cause.CONFLICT, Err.ErrType.AUTH, integrity.getMessage())));
        } catch (Exception e) {
          log.log(Level.FINEST, "Exception during user registration", e);
          answer(Iq.answer(request, new Err(Err.Cause.INTERNAL_SERVER_ERROR, Err.ErrType.AUTH, e.getMessage())));
        }
      }
    }
  }

  private void onResponse(Response response) {
    if (sasl == null) {
      throw new IllegalStateException();
    }
    processAuth(response.data(), false);
  }

  private void onAuth(Auth auth) {
    sasl = this.auth.get(auth.mechanism());
    processAuth(auth.challenge(), true);
  }

  private void processAuth(byte[] data, boolean start) {
    if (!sasl.isComplete()) {
      try {
        if (!sasl.isComplete()) {
          final byte[] challenge = sasl.evaluateResponse(data != null ? data : new byte[0]);
          if (challenge != null && challenge.length > 0) {
            answer(new Challenge(challenge));
          }
        } else {
          success();
        }
        if (start && sasl.isComplete()) {
          success();
        }
      } catch (AuthenticationException e) {
        answer(new Failure(Failure.Type.NOT_AUTHORIZED, e.getMessage()));
      } catch (SaslException e) {
        if (e.getCause() instanceof AuthenticationException) {
          answer(new Failure(Failure.Type.NOT_AUTHORIZED, e.getCause().getMessage()));
        } else {
          log.log(Level.WARNING, "Exception during authorization", e);
          answer(new Failure(Failure.Type.NOT_AUTHORIZED, e.getMessage()));
        }
      }
    } else {
      success();
    }
  }

  private void success() {
    authorizedCallback.accept(sasl.getAuthorizationID());
    last(new Success(), XMPPClientConnection.ConnectionState.CONNECTED);
  }
}
