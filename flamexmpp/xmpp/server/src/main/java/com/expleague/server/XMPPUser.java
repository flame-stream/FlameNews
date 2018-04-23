package com.expleague.server;

/**
 * Experts League
 * Created by solar on 02/03/16.
 */
public final class XMPPUser {
  private final String id;
  private final String password;

  public XMPPUser(String id, String password) {
    this.id = id;
    this.password = password;
  }

  public String id() {
    return id;
  }

  public String password() {
    return password;
  }
}
