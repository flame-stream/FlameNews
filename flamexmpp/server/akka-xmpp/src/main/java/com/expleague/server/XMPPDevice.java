package com.expleague.server;

import com.expleague.model.ExpertsProfile;
import com.expleague.xmpp.JID;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.expleague.server.XMPPDevice.Role.*;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 18:39
 */
public abstract class XMPPDevice {
  public enum Role {
    CLIENT,
    EXPERT,
    ADMIN
  }

  public static final XMPPDevice NO_SUCH_DEVICE = new XMPPDevice(XMPPUser.NO_SUCH_USER, "", "", false, "", "") {
    @Override
    public void updateDevice(String token, String clientVersion) {}
  };

  private XMPPUser user;
  private final String passwd;
  private final String name;
  private final Role role;
  protected String clientVersion;
  protected String token;

  public XMPPDevice(XMPPUser user, String name, String passwd, boolean expert, String clientVersion, String token) {
    this.user = user;
    this.passwd = passwd;
    this.name = name;
    if (!expert)
      role = CLIENT;
    else if (user.authority() != ExpertsProfile.Authority.ADMIN)
      role = EXPERT;
    else
      role = ADMIN;
    this.clientVersion = clientVersion;
    this.token = token;
  }

  public String name() {
    return name;
  }

  public String passwd() {
    return passwd;
  }

  public XMPPUser user() {
    return user;
  }

  public String token() {
    return token;
  }

  public abstract void updateDevice(String token, String clientVersion);

  public Role role() {
    return role;
  }

  public boolean expert() {
    return role != CLIENT;
  }

  public static Pattern versionPattern = Pattern.compile("(.+) ([\\d\\.]+) build (\\d+) @(.+)");
  public int build() {
    if (clientVersion == null)
      return 0;
    final Matcher matcher = versionPattern.matcher(clientVersion);
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(3));
    }
    return 0;
  }

  public String platform() {
    if (clientVersion == null)
      return "iOS";
    final Matcher matcher = versionPattern.matcher(clientVersion);
    if (matcher.find()) {
      return matcher.group(4);
    }
    return "unknown";
  }

  public void updateUser(XMPPUser user) {
    this.user = user;
  }

  @Nullable
  public static XMPPDevice fromJid(JID from) {
    if (from.resource().isEmpty())
      return null;
    final String[] split = from.resource().split("/");
    return Roster.instance().device(split[0]);
  }
}
