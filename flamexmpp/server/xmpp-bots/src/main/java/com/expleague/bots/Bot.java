package com.expleague.bots;

import com.expleague.bots.utils.ItemToTigaseElementParser;
import com.expleague.bots.utils.Receiving;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.xmpp.Item;
import com.expleague.commons.util.sync.StateLatch;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.criteria.Criteria;
import tigase.jaxmpp.core.client.criteria.ElementCriteria;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.AbstractStanzaModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.registration.InBandRegistrationModule;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.*;
import tigase.jaxmpp.j2se.J2SEPresenceStore;
import tigase.jaxmpp.j2se.J2SESessionObject;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * User: solar
 * Date: 18.10.15
 * Time: 16:17
 */
public class Bot {
  private final String passwd;
  private final String resource;
  private final String email;
  private final BareJID jid;

  protected final Jaxmpp jaxmpp = new Jaxmpp(new J2SESessionObject());
  private final BlockingQueue<com.expleague.xmpp.stanza.Stanza> stanzasQueue = new LinkedBlockingQueue<>();
  private final StateLatch latch = new StateLatch();

  private boolean registered = false;
  private boolean offerCheckReceived = false;

  public Bot(final BareJID jid, final String passwd, String resource) {
    this(jid, passwd, resource, null);
  }

  public Bot(final BareJID jid, final String passwd, String resource, String email) {
    this.jid = jid;
    this.passwd = passwd;
    this.resource = resource;
    this.email = email;

    jaxmpp.getProperties().setUserProperty(SessionObject.DOMAIN_NAME, jid.getDomain());
    jaxmpp.getProperties().setUserProperty(SocketConnector.HOSTNAME_VERIFIER_DISABLED_KEY, true);
//    jaxmpp.getSessionObject().setProperty(SocketConnector.TLS_DISABLED_KEY, true);
    jaxmpp.getModulesManager().register(new MucModule());
    PresenceModule.setPresenceStore(jaxmpp.getSessionObject(), new J2SEPresenceStore());
    jaxmpp.getModulesManager().register(new PresenceModule());
    jaxmpp.getModulesManager().register(new RosterModule());
  }

  public void start() throws JaxmppException {
    jaxmpp.getModulesManager().register(new InBandRegistrationModule());
    jaxmpp.getSessionObject().setProperty(InBandRegistrationModule.IN_BAND_REGISTRATION_MODE_KEY, Boolean.TRUE);
    latch.state(1);
    jaxmpp.getEventBus().addHandler(
        InBandRegistrationModule.ReceivedRequestedFieldsHandler.ReceivedRequestedFieldsEvent.class,
        new InBandRegistrationModule.ReceivedRequestedFieldsHandler() {
          public void onReceivedRequestedFields(SessionObject sessionObject, IQ responseStanza) {
            try {
              final InBandRegistrationModule module = jaxmpp.getModule(InBandRegistrationModule.class);
              module.register(jid.toString(), passwd, email, new PrinterAsyncCallback("register", latch) {
                @Override
                protected void transactionComplete() {
                  super.transactionComplete();
                  try {
                    jaxmpp.getConnector().stop();
                  } catch (JaxmppException e) {
                    throw new RuntimeException(e);
                  }
                }
              });
            } catch (JaxmppException e) {
              throw new RuntimeException(e);
            }
          }
        });

    final long loginSleepTimeoutInMillis = 100L;
    final int maxTriesNumber = 10;
    boolean login = false;
    int triesNumber = 0;
    while (!login && triesNumber < maxTriesNumber) {
      try {
        jaxmpp.login();
        login = true;
      } catch (JaxmppException je) {
        try {
          Thread.sleep(loginSleepTimeoutInMillis);
        } catch (InterruptedException ignored) {
        }
      } finally {
        triesNumber++;
      }
    }
    if (!login) {
      throw new RuntimeException("Cannot start server");
    }

    latch.state(2, 1);
    System.out.println("Registration phase passed");
    jaxmpp.getSessionObject().setProperty(InBandRegistrationModule.IN_BAND_REGISTRATION_MODE_KEY, Boolean.FALSE);
    jaxmpp.getProperties().setUserProperty(SessionObject.USER_BARE_JID, jid);
    jaxmpp.getProperties().setUserProperty(SessionObject.PASSWORD, passwd);
    jaxmpp.getProperties().setUserProperty(SessionObject.RESOURCE, resource);
//    jaxmpp.getModulesManager().register(new )
    jaxmpp.getEventBus().addHandler(JaxmppCore.ConnectedHandler.ConnectedEvent.class, sessionObject -> latch.advance());
    jaxmpp.getModulesManager().register(new AbstractStanzaModule<Message>() {
      @Override
      public Criteria getCriteria() {
        return new ElementCriteria("message", new String[0], new String[0]);
      }

      @Override
      public String[] getFeatures() {
        return new String[0];
      }

      @Override
      public void process(Message stanza) throws JaxmppException {
        onStanza(stanza);
      }
    });

    jaxmpp.getEventBus().addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, (sessionObject, message, room, s, date) -> {
      try {
        System.out.println("Group: " + message.getAsString());
        latch.advance();
      } catch (XMLException e) {
        throw new RuntimeException(e);
      }
    });

    jaxmpp.getEventBus().addHandler(PresenceModule.ContactAvailableHandler.ContactAvailableEvent.class,
        (sessionObject, presence, jid, show, s, integer) -> System.out.println(jid + " available with message " + presence.getStatus()));

    System.out.println("Logged in");

    registered = true;
    online();
  }

  public void online() throws JaxmppException {
    if (jaxmpp.isConnected())
      return;
    if (!registered) {
      start();
    }
    else {
      final Presence stanza = Presence.create();
      jaxmpp.login();
      latch.state(2, 1);
      jaxmpp.send(stanza);
      System.out.println("Online presence sent");
    }
    final Operations.Token token = new Operations.Token("60", UUID.randomUUID().toString());
    send(token);
  }

  public void offline() throws JaxmppException {
    offerCheckReceived = false;
    if (jaxmpp.isConnected()) {
      final JaxmppCore.DisconnectedHandler disconnectedHandler = sessionObject -> latch.advance();
      jaxmpp.getEventBus().addHandler(JaxmppCore.DisconnectedHandler.DisconnectedEvent.class, disconnectedHandler);
      jaxmpp.disconnect();
      latch.state(2, 1);
      jaxmpp.getEventBus().remove(disconnectedHandler);
      System.out.println("Sent offline presence");
    }
  }

  public BareJID jid() {
    return jid;
  }

  public void sendGroupchat(BareJID to, Item... items) throws JaxmppException {
    send(to, StanzaType.groupchat, items);
  }

  public void send(Item... items) throws JaxmppException {
    send(null, null, items);
  }

  public void send(BareJID to, Item... items) throws JaxmppException {
    send(to, null, items);
  }

  public void send(BareJID to, StanzaType type, Item... items) throws JaxmppException {
    final Message message = Message.create();
    for (Item item : items) {
      message.addChild(ItemToTigaseElementParser.parse(item));
    }
    if (type != null) {
      message.setType(type);
    }
    if (to != null) {
      message.setTo(JID.jidInstance(to));
    }
    jaxmpp.send(message);
  }

  public void sendIq(BareJID to, StanzaType type, Item... items) throws JaxmppException {
    final IQ iq = IQ.create();
    for (Item item : items) {
      iq.addChild(ItemToTigaseElementParser.parse(item));
    }
    if (type != null) {
      iq.setType(type);
    }
    if (to != null) {
      iq.setTo(JID.jidInstance(to));
    }
    jaxmpp.send(iq);
  }

  public Receiving[] tryReceiveMessages(StateLatch stateLatch, Receiving... messages) throws JaxmppException {
    final long defaultTimeoutInNanos = 30L * 1000L * 1000L * 1000L;
    return tryReceiveMessages(stateLatch, defaultTimeoutInNanos, messages);
  }

  public Receiving[] tryReceiveMessages(StateLatch stateLatch, long timeoutInNanos, Receiving... messages) throws JaxmppException {
    final int initState = stateLatch.state();
    final int finalState = initState << Arrays.stream(messages).filter(Receiving::expected).count();
    final Thread messagesConsumer = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          final com.expleague.xmpp.stanza.Stanza stanza = stanzasQueue.take();
          if (stanza instanceof com.expleague.xmpp.stanza.Message) {
            com.expleague.xmpp.stanza.Message message = (com.expleague.xmpp.stanza.Message) stanza;
            for (Receiving receivingMessage : messages) {
              if (!receivingMessage.received() && receivingMessage.tryReceive(message) && receivingMessage.expected() && receivingMessage.isMessage()) {
                stateLatch.advance();
                if (stateLatch.state() == initState) {
                  return;
                }
                break;
              }
            }
          } else if (stanza instanceof com.expleague.xmpp.stanza.Presence) {
            com.expleague.xmpp.stanza.Presence presence = (com.expleague.xmpp.stanza.Presence) stanza;
            for (Receiving receivingMessage : messages) {
              if (!receivingMessage.received() && receivingMessage.tryReceive(presence) && receivingMessage.expected() && receivingMessage.isPresence()) {
                stateLatch.advance();
                if (stateLatch.state() == initState) {
                  return;
                }
                break;
              }
            }
          } else {
            throw new RuntimeException();
          }
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    });

    messagesConsumer.start();
    stateLatch.state(finalState, initState, timeoutInNanos);
    messagesConsumer.interrupt();
    if (stateLatch.state() != initState) {
      stateLatch.state(initState);
    }
    return Arrays.stream(messages).filter(em -> em.received() ^ em.expected()).toArray(Receiving[]::new);
  }

  private synchronized void onStanza(Stanza message) throws JaxmppException {
    { //sending receipts
      final String receivedXMLNS = "urn:xmpp:receipts";
      final Element request = message.getFirstChild("request");
      if (request != null && receivedXMLNS.equals(request.getXMLNS())) {
        final Message receivedMessage = Message.create();
        receivedMessage.setType(StanzaType.normal);

        final Element received = receivedMessage.addChild(ElementFactory.create("received"));
        received.setAttribute("id", message.getId());
        received.setXMLNS(receivedXMLNS);
        jaxmpp.send(receivedMessage);
      }
    }
    final com.expleague.xmpp.stanza.Message stanza = Item.create(message.getAsString());
    if (stanza != null) {
      if (stanza.has(Offer.class) && stanza.has(Operations.Check.class)) {
        offerCheckReceived = true;
      }
      stanzasQueue.offer(stanza);
    }
  }

  public boolean offerCheckReceivedAndReset() {
    final boolean result = offerCheckReceived;
    offerCheckReceived = false;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Bot bot = (Bot) o;
    return jid.equals(bot.jid);
  }

  @Override
  public int hashCode() {
    return jid.hashCode();
  }

  public static class PrinterAsyncCallback implements AsyncCallback {
    private final String name;

    private StateLatch lock;

    public PrinterAsyncCallback(String name, StateLatch lock) {
      this.name = name;
      this.lock = lock;
    }

    public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
      System.out.println("Error [" + name + "]: " + error + " [" + responseStanza.getAsString() + "]");
      transactionComplete();
    }

    public void onSuccess(Stanza responseStanza) throws JaxmppException {
      System.out.println("Response  [" + name + "]: " + responseStanza.getAsString());
      transactionComplete();
    }

    public void onTimeout() throws JaxmppException {
      System.out.println("Timeout " + name);
      transactionComplete();
    }

    protected void transactionComplete() {
      lock.advance();
    }
  }
}
