package com.expleague.xmpp.model.control.ping;

import com.expleague.xmpp.model.Item;
import com.expleague.xmpp.model.control.XMPPQuery;
import com.expleague.xmpp.model.stanza.Iq;

import javax.xml.bind.annotation.*;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 18:19
 */
@SuppressWarnings("unused")
@XmlRootElement(name = "ping", namespace = PingQuery.NS)
public class PingQuery extends XMPPQuery {
  public static final String NS = "urn:xmpp:ping";

  @Override
  public Item reply(Iq.IqType type) {
    return this;
  }
}
