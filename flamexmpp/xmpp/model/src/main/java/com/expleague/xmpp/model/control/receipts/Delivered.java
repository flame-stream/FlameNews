package com.expleague.xmpp.model.control.receipts;

import com.expleague.xmpp.model.Item;
import com.expleague.xmpp.model.JID;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Delivered extends Item {
  @XmlAttribute
  private String id;

  @XmlAttribute
  private JID user;

  @XmlAttribute
  private String resource;


  @SuppressWarnings("unused")
  public Delivered() {}

  public Delivered(String id, JID user, String resource) {
    this.id = id;
    this.user = user;
    this.resource = resource;
  }

  public Delivered(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }

  public String resource() {
    return resource;
  }

  public JID user() {
    return user;
  }
}

