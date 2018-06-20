package com.expleague.server.services;

import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.control.roster.RosterQuery.RosterItem;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 18:37
 */
public interface Roster {
  @Nullable
  RosterItem item(String local, JID jid);

  Stream<RosterItem> items(String local);

  void create(String local, RosterItem item);

  void update(String local, RosterItem item);

  void remove(String local, JID contact);

  class InMemRoster implements Roster {
    private final Map<String, Map<JID, RosterItem>> roster = new HashMap<>();

    public InMemRoster() {
      create("marnikitta", new RosterItem(new JID("tomat", "marnikitta.com", null), RosterItem.Subscription.BOTH));
      create("tomat", new RosterItem(new JID("marnikitta", "marnikitta.com", null), RosterItem.Subscription.BOTH));
    }

    @Override
    public Stream<RosterItem> items(String local) {
      return roster.getOrDefault(local, Collections.emptyMap()).values().stream();
    }

    @Override
    public void create(String local, RosterItem item) {
      roster.putIfAbsent(local, new HashMap<>());
      if (roster.get(local).containsKey(item.jid())) {
        throw new IllegalArgumentException("Item already exists, jid='" + item.jid() + "'");
      }

      roster.get(local).put(item.jid(), item);
    }

    @Override
    public void update(String local, RosterItem item) {
      roster.putIfAbsent(local, new HashMap<>());
      if (!roster.get(local).containsKey(item.jid())) {
        throw new NoSuchElementException("There is no item with jid='" + item.jid() + "'");
      }

      roster.get(local).put(item.jid(), item);
    }

    @Nullable
    @Override
    public RosterItem item(String local, JID jid) {
      return roster.getOrDefault(local, new HashMap<>()).get(jid);
    }

    @Override
    public void remove(String local, JID contact) {
      roster.get(local).remove(contact);
    }
  }
}
