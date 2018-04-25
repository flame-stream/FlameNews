package com.spbsu.flamestream.flamenews.commons;

import com.expleague.commons.util.sync.StateLatch;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.criteria.Criteria;
import tigase.jaxmpp.core.client.criteria.ElementCriteria;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
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

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Logger;

public class JabberClient {
    private static final String RESOURCE = "grabber";
    private static final int RETRY_LOGIN_COUNT = 10;
    private static final long RETRY_LOGIN_SLEEP_MILLIS = 100L;
    private static final Logger logger = Logger.getLogger(JabberClient.class.getName());

    private final Jaxmpp jaxmpp = new Jaxmpp(new J2SESessionObject());
    private final StateLatch latch = new StateLatch();
    private final Set<JID> liveSubscribers = new ConcurrentSkipListSet<>();

    private final String password;
    private final JID jid;

    private boolean registered = false;

    public JabberClient(String id, String domain, String password) {
        this.password = password;
        this.jid = JID.jidInstance(id, domain, RESOURCE);

        jaxmpp.getProperties().setUserProperty(SessionObject.DOMAIN_NAME, domain);
        jaxmpp.getProperties().setUserProperty(SocketConnector.HOSTNAME_VERIFIER_DISABLED_KEY, true);

        PresenceModule.setPresenceStore(jaxmpp.getSessionObject(), new J2SEPresenceStore());
        jaxmpp.getModulesManager().register(new MucModule());
        jaxmpp.getModulesManager().register(new PresenceHandler(jaxmpp, liveSubscribers));
        jaxmpp.getModulesManager().register(new RosterModule());
    }

    private void start() {
        //register
        {
            jaxmpp.getModulesManager().register(new InBandRegistrationModule());
            jaxmpp.getSessionObject().setProperty(InBandRegistrationModule.IN_BAND_REGISTRATION_MODE_KEY, Boolean.TRUE);
            latch.state(1);
            jaxmpp.getEventBus().addHandler(
                    InBandRegistrationModule.ReceivedRequestedFieldsHandler.ReceivedRequestedFieldsEvent.class,
                    new InBandRegistrationModule.ReceivedRequestedFieldsHandler() {
                        public void onReceivedRequestedFields(SessionObject sessionObject, IQ responseStanza) {
                            try {
                                final InBandRegistrationModule module = jaxmpp.getModule(InBandRegistrationModule.class);
                                module.register(jid.getLocalpart(), password, null, new PrinterAsyncCallback("register", latch) {
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

            boolean login = false;
            int triesNumber = 0;
            while (!login && triesNumber < RETRY_LOGIN_COUNT) {
                try {
                    jaxmpp.login();
                    login = true;
                } catch (JaxmppException je) {
                    try {
                        Thread.sleep(RETRY_LOGIN_SLEEP_MILLIS);
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
            logger.info("Registration phase passed");
        }

        //login
        {
            jaxmpp.getSessionObject().setProperty(InBandRegistrationModule.IN_BAND_REGISTRATION_MODE_KEY, Boolean.FALSE);
            jaxmpp.getProperties().setUserProperty(SessionObject.USER_BARE_JID, jid.getBareJid());
            jaxmpp.getProperties().setUserProperty(SessionObject.PASSWORD, password);
            jaxmpp.getProperties().setUserProperty(SessionObject.RESOURCE, RESOURCE);

            jaxmpp.getEventBus().addHandler(JaxmppCore.ConnectedHandler.ConnectedEvent.class, sessionObject -> latch.advance());
            jaxmpp.getModulesManager().register(new PresenceHandler(jaxmpp, liveSubscribers));

            jaxmpp.getEventBus().addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, (sessionObject, message, room, s, date) -> {
                try {
                    logger.info("Group: " + message.getAsString());
                    latch.advance();
                } catch (XMLException e) {
                    throw new RuntimeException(e);
                }
            });

            jaxmpp.getEventBus().addHandler(PresenceModule.ContactAvailableHandler.ContactAvailableEvent.class,
                    (sessionObject, presence, jid, show, s, integer) -> System.out.println(jid + " available with message " + presence.getStatus()));
            logger.info("Logged in");
        }

        registered = true;
        online();
    }

    public void online() {
        if (jaxmpp.isConnected())
            return;
        if (!registered) {
            start();
        } else {
            try {
                jaxmpp.login();
                latch.state(2, 1);
                jaxmpp.send(Presence.create());
                logger.info("Online presence sent");
            } catch (JaxmppException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void offline() {
        if (jaxmpp.isConnected()) {
            final JaxmppCore.DisconnectedHandler disconnectedHandler = sessionObject -> latch.advance();
            jaxmpp.getEventBus().addHandler(JaxmppCore.DisconnectedHandler.DisconnectedEvent.class, disconnectedHandler);
            try {
                jaxmpp.disconnect();
            } catch (JaxmppException e) {
                throw new RuntimeException(e);
            }

            latch.state(2, 1);
            jaxmpp.getEventBus().remove(disconnectedHandler);
            logger.info("Sent offline presence");
        }
    }

    public void send(String text) {
        online();
        liveSubscribers.forEach(subscriber -> {
            try {
                final Message message = Message.createMessage();
                message.setFrom(jid);
                message.setTo(subscriber);
                message.setBody(text);
                jaxmpp.send(message);
            } catch (JaxmppException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static class PrinterAsyncCallback implements AsyncCallback {
        private final String name;
        private final StateLatch lock;

        PrinterAsyncCallback(String name, StateLatch lock) {
            this.name = name;
            this.lock = lock;
        }

        public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
            logger.info("Error [" + name + "]: " + error + " [" + responseStanza.getAsString() + "]");
            transactionComplete();
        }

        public void onSuccess(Stanza responseStanza) throws JaxmppException {
            logger.info("Response  [" + name + "]: " + responseStanza.getAsString());
            transactionComplete();
        }

        public void onTimeout() {
            logger.info("Timeout " + name);
            transactionComplete();
        }

        protected void transactionComplete() {
            lock.advance();
        }
    }

    private static class PresenceHandler extends AbstractStanzaModule<Presence> {
        private final Jaxmpp jaxmpp;
        private final Set<JID> liveSubscribers;

        private PresenceHandler(Jaxmpp jaxmpp, Set<JID> liveSubscribers) {
            this.jaxmpp = jaxmpp;
            this.liveSubscribers = liveSubscribers;
        }

        @Override
        public Criteria getCriteria() {
            return new ElementCriteria("message", new String[0], new String[0]);
        }

        @Override
        public String[] getFeatures() {
            return new String[0];
        }

        @Override
        public void process(Presence presence) {
            try {
                if (presence.getType() == StanzaType.subscribe) {
                    final Presence stanza = Presence.create();
                    stanza.setType(StanzaType.subscribed);
                    stanza.setTo(presence.getFrom());
                    jaxmpp.send(stanza);
                } else if (presence.getType() == StanzaType.unavailable) {
                    liveSubscribers.remove(presence.getFrom());
                } else {
                    liveSubscribers.add(presence.getFrom());
                }
            } catch (JaxmppException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
