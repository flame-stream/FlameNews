package com.expleague.xmpp.model.control;

import com.expleague.xmpp.model.Item;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Experts League
 * Created by solar on 21.02.17.
 */
@SuppressWarnings("unused")
@XmlRootElement(name = "query", namespace = DeliveryReceit.NS)
public class DeliveryReceit extends Item {
  public static final String NS = "http://expleague.com/delivery";

  @XmlElementRef(namespace = NS)
  private DeliveryReceit.Item item;

  public DeliveryReceit() {}

  public DeliveryReceit(String id, String resource) {
    item = new Item(id, resource);
  }

  public String id() {
    return item != null ? item.id : null;
  }

  public String resource() {
    return item != null ? item.resource : null;
  }

  @XmlRootElement(name = "item", namespace = NS)
  @XmlType(name = "delivery-item")
  public static class Item extends com.expleague.xmpp.model.Item {
    @XmlAttribute(name = "id", namespace = NS)
    private String id;

    @XmlAttribute(name = "resource", namespace = NS)
    private String resource;

    @XmlAttribute(name = "user", namespace = NS)
    private String user;

    public Item() {}

    public Item(String id, String resource) {
      this.id = id;
      this.resource = resource;
    }
  }
}
