package com.spbsu.flamestream.flamenews.telegram;


import com.spbsu.flamestream.flamenews.telegram.commands.StartCommand;
import tigase.jaxmpp.core.client.criteria.Criteria;
import tigase.jaxmpp.core.client.criteria.ElementCriteria;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.AbstractStanzaModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

// Handle messages from xmpp
public class MessageHandler extends AbstractStanzaModule<Message> {

  @Override
  public void process(Message message) throws JaxmppException {
    // push message to queue for further delivery
    StartCommand.getMessages().add(message);
  }

  @Override
  public Criteria getCriteria() {
    return new ElementCriteria("message", new String[0], new String[0]);
  }

  @Override
  public String[] getFeatures() {
    return new String[0];
  }
}
