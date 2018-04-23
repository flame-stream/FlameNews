package com.expleague.server;

import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.control.register.RegisterQuery;
import com.expleague.xmpp.model.control.roster.RosterQuery.RosterItem;
import com.expleague.xmpp.model.control.roster.RosterQuery.RosterItem.Subscription;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 18:37
 */
public interface Roster {
  @Nullable
  XMPPUser user(String local);

  XMPPUser register(RegisterQuery query);

  RegisterQuery required();

  Stream<RosterItem> items(String local);

  @Nullable
  RosterItem item(String local, JID jid);

  RosterItem create(String local, JID contact);

  RosterItem updateSubscription(String local, JID contact, Subscription subscription);

  void remove(String local, JID item);

  class InMemRoster implements Roster {
    private final Map<String, XMPPUser> users = new HashMap<>();
    private final Map<String, Map<JID, RosterItem>> roster = new HashMap<>();

    public InMemRoster() {
      addUser(new XMPPUser("marnikitta", "password"));
      addUser(new XMPPUser("trofimov9artem", "password"));
      addUser(new XMPPUser("solar", "password"));
      addUser(new XMPPUser("vk-grabber", "password"));
      addUser(new XMPPUser("tomat", "password"));
    }

    @Override
    @Nullable
    public XMPPUser user(String name) {
      return users.get(name);
    }

    @Override
    public XMPPUser register(RegisterQuery query) {
      Objects.requireNonNull(query.username());
      Objects.requireNonNull(query.passwd());

      final XMPPUser user = new XMPPUser(query.username(), query.passwd());
      addUser(user);
      return user;
    }

    private void addUser(XMPPUser user) {
      if (users.containsKey(user.id())) {
        throw new IllegalArgumentException("User with username " + user.id() + "already exists");
      }

      users.put(user.id(), user);
      roster.put(user.id(), new HashMap<>());
    }

    @Override
    public RegisterQuery required() {
      return RegisterQuery.requiredFields();
    }

    @Override
    public Stream<RosterItem> items(String local) {
      return roster.get(local).values().stream();
    }

    @Nullable
    @Override
    public RosterItem item(String local, JID jid) {
      return roster.get(local).get(jid);
    }

    @Override
    public RosterItem create(String local, JID contact) {
      final Map<JID, RosterItem> localRoster = roster.get(local);
      if (localRoster.containsKey(contact)) {
        throw new IllegalArgumentException("User with jid " + contact + "already exists");
      }

      final RosterItem value = new RosterItem(contact);
      localRoster.put(contact, value);
      return value;
    }

    @Override
    public RosterItem updateSubscription(String local, JID contact, Subscription subscription) {
      final Map<JID, RosterItem> localRoster = roster.get(local);
      final RosterItem item = localRoster.get(contact);

      final RosterItem updated = item.withSubscription(subscription);
      localRoster.put(contact, updated);
      return updated;
    }

    @Override
    public void remove(String local, JID item) {
      roster.get(local).remove(item);
    }
  }
}
