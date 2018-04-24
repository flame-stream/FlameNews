package com.expleague.xmpp.model.control.receipts;

import com.expleague.xmpp.model.Item;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author vpdelta
 */
@XmlRootElement(namespace = Received.NS)
public class Received extends Item {
  public static final String NS = "urn:xmpp:receipts";

  @XmlAttribute
  private String id;

  public Received() {
  }

  public Received(final String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }
}
