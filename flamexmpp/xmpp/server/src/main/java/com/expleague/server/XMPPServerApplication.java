package com.expleague.server;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

/**
 * User: solar
 * Date: 24.11.15
 * Time: 17:42
 */
@SuppressWarnings("unused")
public class XMPPServerApplication {
  private static Cfg config;

  public static void main(String[] args) throws Exception {
    final Config load = ConfigFactory.load();
    setConfig(new ServerCfg(load));

    final ActorSystem system = ActorSystem.create("xmpp-server", load);
    final ActorRef xmpp = system.actorOf(XMPP.props(), "xmpp");

    system.actorOf(XMPPServer.props(xmpp), "comm");
  }

  public static Cfg config() {
    return config;
  }

  @VisibleForTesting
  public static void setConfig(Cfg cfg) {
    config = cfg;
  }

  public interface Cfg {
    String domain();

    Config config();

    default FiniteDuration timeout(String name) {
      final Config config = config().getConfig(name);
      if (config == null) {
        throw new IllegalArgumentException("No timeout configured for: " + name);
      }
      return Duration.create(
        config.getLong("length"),
        TimeUnit.valueOf(config.getString("unit"))
      );
    }

    default TimeUnit timeUnit(String name) {
      return TimeUnit.valueOf(config().getString(name + ".unit"));
    }
  }

  public static class ServerCfg implements Cfg {
    private final Config config;

    private final String domain;

    public ServerCfg(Config load) {
      config = load.getConfig("xmpp");
      domain = config.getString("domain");
    }

    @Override
    public Config config() {
      return config;
    }

    @Override
    public String domain() {
      return domain;
    }
  }
}
