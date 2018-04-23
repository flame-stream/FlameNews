package com.expleague.xmpp.model.control.discovery;

import com.expleague.xmpp.model.Item;
import com.expleague.xmpp.model.control.XMPPQuery;
import com.expleague.xmpp.model.stanza.Iq;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 18:49
 */
@XmlRootElement
public class Query extends XMPPQuery {
  @Override
  public Item reply(Iq.IqType type) {
    return this;
  }
}