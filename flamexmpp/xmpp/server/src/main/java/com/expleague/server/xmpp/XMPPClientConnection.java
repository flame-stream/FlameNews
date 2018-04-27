package com.expleague.server.xmpp;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.expleague.commons.io.StreamTools;
import com.expleague.server.XMPPServerApplication;
import com.expleague.server.xmpp.phase.AuthorizationPhase;
import com.expleague.server.xmpp.phase.ConnectedPhase;
import com.expleague.server.xmpp.phase.HandshakePhase;
import com.expleague.server.xmpp.phase.SSLHandshake;
import com.expleague.util.xml.AsyncJAXBStreamReader;
import com.expleague.xmpp.model.Item;
import com.expleague.xmpp.model.Stream;
import com.expleague.xmpp.model.control.Close;
import com.expleague.xmpp.model.control.Open;
import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import com.relayrides.pushy.apns.P12Util;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.xml.sax.SAXException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * User: solar
 * Date: 07.12.15
 * Time: 19:50
 */
@SuppressWarnings("unused")
public class XMPPClientConnection extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(context().system(), self());
  private static boolean unitTestEnabled = true;

  private final ActorRef xmpp;
  private final ActorRef connection;

  private SSLHelper helper;
  private boolean tls = false;
  private boolean closed = false;
  private ActorRef businessLogic;

  private String id;
  private boolean opened = false;

  public XMPPClientConnection(ActorRef connection, ActorRef xmpp) {
    this.connection = connection;
    this.xmpp = xmpp;
  }

  public static Props props(ActorRef connection, ActorRef xmpp) {
    return Props.create(XMPPClientConnection.class, connection, xmpp);
  }

  @Override
  public Receive createReceive() {
    return ReceiveBuilder.create()
      .match(Tcp.Received.class, this::onReceived)
      .match(Tcp.Command.class, this::onCommand)
      .match(Item.class, this::onItem)
      .match(Ack.class, this::nextMessage)
      .match(Tcp.CommandFailed.class, this::failedToSend)
      .match(Status.Failure.class, this::onFailure)
      .match(Tcp.ConnectionClosed.class, this::onConnectionClosed)
      .match(Terminated.class, this::onTerminated)
      .match(ConnectionState.class, this::onConnectionState)
      .build();
  }

  @Override
  public void preStart() {
    onConnectionState(ConnectionState.HANDSHAKE);
    connection.tell(TcpMessage.register(self()), self());
  }

  private void onReceived(Tcp.Received msgIn) {
    if (currentState == ConnectionState.HANDSHAKE) {
      input(msgIn.data());
    } else if (currentState == ConnectionState.STARTTLS) {
      businessLogic.tell(msgIn, self());
    } else {
      helper.decrypt(msgIn.data(), this::input);
    }
  }

  private void onCommand(Tcp.Command cmd) {
    connection.tell(cmd, self());
  }

  private AsyncXMLStreamReader<AsyncByteArrayFeeder> asyncXml;
  private AsyncJAXBStreamReader reader;

  {
    final AsyncXMLInputFactory factory = new InputFactoryImpl();
    asyncXml = factory.createAsyncForByteArray();
    reader = new AsyncJAXBStreamReader(asyncXml, Stream.jaxb());
  }

  private void input(ByteString data) {
    if (data == null) {
      onConnectionState(ConnectionState.CLOSED);
      return;
    }

    final byte[] copy = new byte[data.length()];
    data.asByteBuffer().get(copy);
    log.debug("IN: " + new String(copy, StreamTools.UTF));
    try {
      asyncXml.getInputFeeder().feedInput(copy, 0, copy.length);
      if (!opened) {
        businessLogic.tell(new Open(), self());
        opened = true;
      }
      reader.drain((in) -> {
        if (in instanceof Item) {
          businessLogic.tell(in, self());
        }
      });
    } catch (XMLStreamException | SAXException e) {
      log.error(e, "Exception during message parsing");
    }
  }

  private void onItem(Item item) {
    Tcp.Event requestedAck = new Tcp.NoAck(null);
    final String xml;
    if (item instanceof Open) {
      xml = Item.XMPP_START;
    } else {
      xml = item.xmlString(false);
    }

    log.debug("OUT: " + xml);
    final ByteString data = ByteString.fromString(xml);
    if (currentState != ConnectionState.HANDSHAKE && currentState != ConnectionState.STARTTLS) {
      helper.encrypt(data, this::output);
    } else {
      output(data);
    }
  }

  private static class Ack implements Tcp.Event {
    private static volatile int ackCounter = 0;
    final int id;
    final int size;

    Ack(int size) {
      this.id = ackCounter++;
      this.size = size;
    }
  }

  private Ack current;
  private final Queue<ByteString> outQueue = new ArrayDeque<>();

  private void output(ByteString out) {
    if (current == null) {
      connection.tell(new Tcp.Write(out, current = new Ack(out.size())), self());
    } else {
      outQueue.add(out);
    }
  }

  private void nextMessage(Ack ack) {
    //    log.finest("Sent packet " + ack.id + " (" + ack.size +  " bytes) " + (outQueue.isEmpty() ? "" : (outQueue.size() + " more in queue")));
    if (current == null || ack.id == current.id) {
      current = null;
      final ByteString next = outQueue.poll();
      if (next != null) {
        connection.tell(new Tcp.Write(next, current = new Ack(next.size())), self());
      }
    }
  }

  private void failedToSend(Tcp.CommandFailed failed) {
    log.warning("Unable to send message to " + id + " stopping connection");
    onConnectionState(ConnectionState.CLOSED);
  }

  private void onFailure(Status.Failure failure) {
    log.error(failure.cause(), "Stream failure");
    onConnectionState(ConnectionState.CLOSED);
  }

  private void onConnectionClosed(Tcp.ConnectionClosed ignore) {
    log.debug("Client connection closed");
    onConnectionState(ConnectionState.CLOSED);
  }

  private void onTerminated(Terminated who) {
    log.debug("Terminated {}", who.actor());
  }

  private ConnectionState currentState;

  private void onConnectionState(ConnectionState state) {
    if (currentState == state) {
      return;
    }

    if (closed) {
      state = ConnectionState.CLOSED;
    }

    final ConnectionState finalState = state;
    ActorRef newLogic = null;
    switch (state) {
      case HANDSHAKE: {
        final Source<Tcp.Received, ActorRef> source = Source.actorRef(1000, OverflowStrategy.fail());
        newLogic = context().actorOf(HandshakePhase.props(self()), "handshake");
        break;
      }
      case STARTTLS: {
        try {
          final String domain = XMPPServerApplication.config().domain();
          final File file;
          if (unitTestEnabled) {
            final ClassLoader classLoader = getClass().getClassLoader();
            //noinspection ConstantConditions
            file = new File(classLoader.getResource(domain + ".p12").getFile());
          } else {
            file = new File("./certs/" + domain + ".p12");
          }

          if (!file.exists()) {
            synchronized (XMPPClientConnection.class) {
              if (!file.exists()) {
                final File script = File.createTempFile("create-self-signed", ".sh");
                StreamTools.transferData(
                  getClass().getResourceAsStream("/create-self-signed.sh"),
                  new FileOutputStream(script)
                );
                //noinspection ResultOfMethodCallIgnored
                file.getParentFile().mkdirs();
                final Process exec = Runtime.getRuntime().exec("/bin/bash");
                final PrintStream bash = new PrintStream(exec.getOutputStream());
                bash.println("cd " + file.getParentFile().getAbsolutePath());
                bash.println("bash " + script.getAbsolutePath() + " " + domain);
                exec.getOutputStream().close();
                exec.waitFor();
                log.info(StreamTools.readStream(exec.getInputStream()).toString());
                log.warning(StreamTools.readStream(exec.getErrorStream()).toString());
              }
            }
          }
          final SslContext context = getSslContextWithP12File(file, "");
          final SSLEngine sslEngine = context.newEngine(ByteBufAllocator.DEFAULT);
          sslEngine.setUseClientMode(false);
          //          sslEngine.setEnableSessionCreation(true);
          sslEngine.setWantClientAuth(false);
          final ActorRef handshake = context().actorOf(SSLHandshake.props(self(), sslEngine), "starttls");
          sslEngine.beginHandshake();
          helper = new SSLHelper(sslEngine);
          newLogic = handshake;
        } catch (IOException | InterruptedException ioe) {
          log.error(ioe, "Unable to create SSL context");
        }
        break;
      }
      case AUTHORIZATION: {
        { // reset factory to be able to work with <?xml?> instructions
          final AsyncXMLInputFactory factory = new InputFactoryImpl();
          asyncXml = factory.createAsyncForByteArray();
          reader = new AsyncJAXBStreamReader(asyncXml, Stream.jaxb());
        }

        businessLogic.tell(new Close(), self());
        newLogic = context().actorOf(AuthorizationPhase.props(self(), id -> this.id = id), "authorization");
        break;
      }
      case CONNECTED: {
        { // reset factory to be able to work with <?xml?> instructions
          final AsyncXMLInputFactory factory = new InputFactoryImpl();
          asyncXml = factory.createAsyncForByteArray();
          reader = new AsyncJAXBStreamReader(asyncXml, Stream.jaxb());
        }
        newLogic = context().actorOf(ConnectedPhase.props(self(), id, xmpp), "connected");
        break;
      }
      case CLOSED: {
        if (businessLogic != null) {
          businessLogic.tell(PoisonPill.getInstance(), self());
        }
        connection.tell(PoisonPill.getInstance(), self());
        self().tell(PoisonPill.getInstance(), self());
        return;
      }
    }
    connection.tell(TcpMessage.resumeReading(), self());
    opened = false;
    businessLogic = newLogic;
    currentState = state;
    log.debug("Connection state changed to: {}", state);
    log.debug("BL changed to: {}", newLogic != null ? newLogic.path() : null);
  }

  private static SslContext getSslContextWithP12File(final File p12File, final String password) throws SSLException {
    final X509Certificate x509Certificate;
    final PrivateKey privateKey;

    try {
      final KeyStore.PrivateKeyEntry privateKeyEntry = P12Util.getPrivateKeyEntryFromP12File(p12File, password);

      final Certificate certificate = privateKeyEntry.getCertificate();

      if (!(certificate instanceof X509Certificate)) {
        throw new KeyStoreException(
          "Found a certificate in the provided PKCS#12 file, but it was not an X.509 certificate.");
      }

      x509Certificate = (X509Certificate) certificate;
      privateKey = privateKeyEntry.getPrivateKey();
    } catch (final KeyStoreException | IOException | CertificateException | UnrecoverableEntryException | NoSuchAlgorithmException e) {
      throw new SSLException(e);
    }

    return getSslContextWithCertificateAndPrivateKey(x509Certificate, privateKey, password);
  }

  private static SslContext getSslContextWithCertificateAndPrivateKey(final X509Certificate certificate,
                                                                      final PrivateKey privateKey,
                                                                      final String privateKeyPassword) throws
                                                                                                       SSLException {
    return SslContextBuilder.forServer(privateKey, privateKeyPassword, certificate)
      .sslProvider(unitTestEnabled ? SslProvider.JDK : SslProvider.OPENSSL)
      //        .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
      .build();
  }

  public static void setUnitTestEnabled(boolean unitTestEnabled) {
    XMPPClientConnection.unitTestEnabled = unitTestEnabled;
  }

  public enum ConnectionState {
    HANDSHAKE,
    STARTTLS,
    AUTHORIZATION,
    CONNECTED,
    CLOSED
  }
}
