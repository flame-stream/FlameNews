package com.expleague.xmpp.model.control.ping;

import com.expleague.xmpp.model.control.XMPPQuery;

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
}
