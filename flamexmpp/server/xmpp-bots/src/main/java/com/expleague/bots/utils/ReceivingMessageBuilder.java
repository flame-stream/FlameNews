package com.expleague.bots.utils;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.commons.filters.Filter;
import com.expleague.commons.util.Pair;
import tigase.jaxmpp.core.client.BareJID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Artem
 * Date: 10.03.2017
 * Time: 16:17
 */
public class ReceivingMessageBuilder {
  private Map<Class, Filter> filters = new HashMap<>();
  private JID from = null;
  private boolean isMessage = true;
  private boolean expected = true;

  public <T extends Item> ReceivingMessageBuilder has(Class<T> clazz) {
    filters.put(clazz, null);
    return this;
  }

  public <T extends Item> ReceivingMessageBuilder has(Class<T> clazz, Filter<T> filter) {
    filters.put(clazz, filter);
    return this;
  }

  public ReceivingMessageBuilder from(BareJID from) {
    this.from = JID.parse(from.toString());
    return this;
  }

  public ReceivingMessageBuilder from(JID from) {
    this.from = from;
    return this;
  }

  public ReceivingMessageBuilder isMessage() {
    this.isMessage = true;
    return this;
  }

  public ReceivingMessageBuilder isPresence() {
    this.isMessage = false;
    return this;
  }

  public ReceivingMessageBuilder expected(boolean expected) {
    this.expected = expected;
    return this;
  }

  public Receiving build() {
    return new Receiving(from, isMessage, filters, expected);
  }
}
