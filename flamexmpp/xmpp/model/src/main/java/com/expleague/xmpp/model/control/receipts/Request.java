package com.expleague.xmpp.model.control.receipts;

import com.expleague.xmpp.model.Item;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author vpdelta
 */
@XmlRootElement(namespace = Request.NS)
public class Request extends Item {
  public static final String NS = "urn:xmpp:receipts";
}
