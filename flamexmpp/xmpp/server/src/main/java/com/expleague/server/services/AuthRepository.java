package com.expleague.server.services;

import com.expleague.server.XMPPUser;
import com.expleague.xmpp.model.control.register.RegisterQuery;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public interface AuthRepository {
  @Nullable
  XMPPUser user(String local);

  XMPPUser register(RegisterQuery query);

  RegisterQuery required();

  class InMemAuthRepository implements AuthRepository {
    private final Map<String, XMPPUser> users = new HashMap<>();

    public InMemAuthRepository() {
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
    }

    @Override
    public RegisterQuery required() {
      return RegisterQuery.requiredFields();
    }
  }
}
