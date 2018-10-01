package com.expleague.xmpp.control.ping;

import com.expleague.server.services.PingService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.control.XMPPQuery;
import com.expleague.xmpp.stanza.Iq;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 18:19
 */
@SuppressWarnings("unused")
@XmlRootElement(name = "ping", namespace = PingQuery.NS)
public class PingQuery extends XMPPQuery {
  public static final String NS = "urn:xmpp:ping";

  static {
    XMPPServices.register(NS, PingService.class, "ping");
  }

  @Override
  public Item reply(Iq.IqType type) {
    return this;
  }
}
