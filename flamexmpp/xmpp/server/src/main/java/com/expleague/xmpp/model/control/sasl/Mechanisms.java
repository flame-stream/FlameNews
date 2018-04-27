package com.expleague.xmpp.model.control.sasl;

import com.expleague.commons.func.Functions;
import com.expleague.server.XMPPServerApplication;
import com.expleague.server.XMPPUser;
import com.expleague.server.services.AuthRepository;
import com.expleague.xmpp.model.control.XMPPFeature;
import com.expleague.xmpp.model.control.sasl.plain.PlainServer;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.sasl.AuthenticationException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 10.12.15
 * Time: 16:14
 */
@XmlRootElement
public class Mechanisms extends XMPPFeature {
  @XmlElement(name = "mechanism", namespace = "urn:ietf:params:xml:ns:xmpp-sasl")
  @XmlJavaTypeAdapter(AuthMechanismXmlAdapter.class)
  private final List<SaslServer> mechanisms = new ArrayList<>();

  public void fillKnownMechanisms() {
    final Enumeration<SaslServerFactory> factories = Sasl.getSaslServerFactories();
    final AuthMechanismXmlAdapter adapter = new AuthMechanismXmlAdapter();
    while (factories.hasMoreElements()) {
      final SaslServerFactory saslServerFactory = factories.nextElement();
      for (final String mech : saslServerFactory.getMechanismNames(Collections.emptyMap())) {
        //        System.out.println(mech);
        if ("GSSAPI".equals(mech)) // skip kerberos from MS
        {
          continue;
        }
        mechanisms.add(adapter.unmarshal(mech));
      }
    }
    mechanisms.add(adapter.unmarshal("PLAIN"));
  }

  public SaslServer get(String mechanism) {
    return mechanisms.stream().filter(sasl -> mechanism.equals(sasl.getMechanismName())).findAny().get();
  }

  public static class AuthMechanismXmlAdapter extends XmlAdapter<String, SaslServer> {
    private static final Logger log = Logger.getLogger(AuthMechanismXmlAdapter.class.getName());

    @Override
    public SaslServer unmarshal(String mechanism) {
      try {
        final CallbackHandler callbackHandler = callbacks -> {
          final Optional<NameCallback> nameO = Stream.of(callbacks)
            .flatMap(Functions.instancesOf(NameCallback.class))
            .findAny();
          final Optional<PasswordCallback> passwdO = Stream.of(callbacks)
            .flatMap(Functions.instancesOf(PasswordCallback.class))
            .findAny();
          final Optional<AuthorizeCallback> authO = Stream.of(callbacks)
            .flatMap(Functions.instancesOf(AuthorizeCallback.class))
            .findAny();
          if (passwdO.isPresent() && nameO.isPresent()) {
            final PasswordCallback passwd = passwdO.get();
            final XMPPUser user = new AuthRepository.InMemAuthRepository().user(nameO.get().getDefaultName());
            if (user != null) {
              passwd.setPassword(user.password().toCharArray());
            } else {
              throw new AuthenticationException("No such user");
            }
          }
          if (authO.isPresent()) {
            final AuthorizeCallback auth = authO.get();
            if (auth.getAuthenticationID().equals(auth.getAuthorizationID())) {
              auth.setAuthorized(true);
            }
          }
        };
        if ("PLAIN".equals(mechanism)) {
          return new PlainServer("xmpp", XMPPServerApplication.config().domain(), callbackHandler);
        } else {
          return Sasl.createSaslServer(
            mechanism,
            "xmpp",
            XMPPServerApplication.config().domain(),
            Collections.emptyMap(),
            callbackHandler
          );
        }
      } catch (SaslException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public String marshal(SaslServer v) {
      return v.getMechanismName();
    }
  }
}
