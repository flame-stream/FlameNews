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
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.J2SEPresenceStore;
import tigase.jaxmpp.j2se.J2SESessionObject;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;

public class JabberClient {
    protected final Jaxmpp jaxmpp = new Jaxmpp(new J2SESessionObject());
    private final StateLatch latch = new StateLatch();

    private final String id;
    private final String domain;
    private final String password;
    private final BareJID jid;

    public JabberClient(String id, String domain, String password) {
        this.id = id;
        this.domain = domain;
        this.password = password;
        this.jid = BareJID.bareJIDInstance(id, domain);

        jaxmpp.getProperties().setUserProperty(SessionObject.DOMAIN_NAME, domain);
        jaxmpp.getProperties().setUserProperty(SocketConnector.HOSTNAME_VERIFIER_DISABLED_KEY, true);

        PresenceModule.setPresenceStore(jaxmpp.getSessionObject(), new J2SEPresenceStore());
        jaxmpp.getModulesManager().register(new MucModule());
        jaxmpp.getModulesManager().register(new PresenceModule());
        jaxmpp.getModulesManager().register(new RosterModule());
    }

    public void start() {
        jaxmpp.getModulesManager().register(new InBandRegistrationModule());
        jaxmpp.getSessionObject().setProperty(InBandRegistrationModule.IN_BAND_REGISTRATION_MODE_KEY, Boolean.TRUE);
        latch.state(1);
        jaxmpp.getEventBus().addHandler(
                InBandRegistrationModule.ReceivedRequestedFieldsHandler.ReceivedRequestedFieldsEvent.class,
                new InBandRegistrationModule.ReceivedRequestedFieldsHandler() {
                    public void onReceivedRequestedFields(SessionObject sessionObject, IQ responseStanza) {
                        try {
                            final InBandRegistrationModule module = jaxmpp.getModule(InBandRegistrationModule.class);
                            module.register(jid.toString(), password, null, new PrinterAsyncCallback("register", latch) {
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
        jaxmpp.getProperties().setUserProperty(SessionObject.PASSWORD, password);
        jaxmpp.getProperties().setUserProperty(SessionObject.RESOURCE, "java-client");

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
                //onMessage(stanza);
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

        //registered = true;
        //online();
    }

    public static class PrinterAsyncCallback implements AsyncCallback {
        private final String name;

        private StateLatch lock;

        PrinterAsyncCallback(String name, StateLatch lock) {
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

    public static void main(String[] args) {
        final JabberClient client = new JabberClient("tomat", "dani-da-latitude", "password");
        client.start();
    }
}
