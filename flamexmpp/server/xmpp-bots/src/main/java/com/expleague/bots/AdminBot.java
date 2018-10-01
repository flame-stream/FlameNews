package com.expleague.bots;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

/**
 * User: Artem
 * Date: 14.02.2017
 * Time: 16:27
 */
public class AdminBot extends ExpertBot {
  public AdminBot(final BareJID jid, final String passwd) {
    super(jid, passwd, "expert", "/admin/expert");
  }
}