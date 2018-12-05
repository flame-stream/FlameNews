package com.expleague.bots.utils;

import com.expleague.xmpp.AnyHolder;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.expleague.commons.filters.Filter;
import com.expleague.commons.util.Pair;
import com.expleague.xmpp.stanza.Presence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Artem
 * Date: 17.03.2017
 * Time: 14:59
 */
public class Receiving {
  private final Map<Class, Filter> filters;
  private final JID from;
  private final boolean isMessage;
  private final boolean expected;
  private boolean received = false;


  public Receiving(JID from, boolean isMessage, Map<Class, Filter> filters, boolean expected) {
    this.from = from;
    this.isMessage = isMessage;
    this.filters = new HashMap<>(filters);
    this.expected = expected;
  }

  public boolean tryReceive(Message message) {
    if (from != null && !from.equals(message.from())) {
      return false;
    }
    received = check(message);
    return received;
  }

  public boolean tryReceive(Presence message) {
    if (from != null && !from.equals(message.from())) {
      return false;
    }
    received = check(message);
    return received;
  }

  private boolean check(AnyHolder message) {
    //noinspection unchecked
    return filters.entrySet()
            .stream()
            .allMatch(entry ->
                    entry.getKey() != null ? message.has(entry.getKey(), entry.getValue()) : message.has(entry.getKey())
            );
  }

  public boolean expected() {
    return expected;
  }

  public boolean received() {
    return received;
  }

  public boolean isMessage() {
    return isMessage;
  }

  public boolean isPresence() {
    return !isMessage;
  }

  public JID from() {
    return from;
  }

  public String toString() {
    final StringBuilder stringBuilder = new StringBuilder();
    filters.forEach((clazz, filter) -> {
      stringBuilder.append(clazz.toString());
      stringBuilder.append(", ");
    });
    return stringBuilder.toString();
  }
}
