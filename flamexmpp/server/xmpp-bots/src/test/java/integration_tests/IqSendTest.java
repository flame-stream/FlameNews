package integration_tests;

import akka.actor.ActorPath;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.actor.RootActorPath;
import com.expleague.bots.Bot;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.XMPPServer;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.server.notifications.NotificationsManager;
import com.expleague.server.services.XMPPServices;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.xmpp.control.expleague.flame.StartFlameQuery;
import com.spbsu.flamestream.runtime.WorkerApplication;
import com.spbsu.flamestream.runtime.utils.DumbInetSocketAddress;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.zookeeper.server.ZooKeeperApplication;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class IqSendTest {
  @Test
  public void testStartFlamestreamFromXMPP() throws JaxmppException, TimeoutException, InterruptedException {
    //init zk, zk is unique
    final List<Integer> ports;
    try {
      ports = new ArrayList<>(freePorts(2));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    ZooKeeperApplication zooKeeperApplication = new ZooKeeperApplication(ports.get(0));
    zooKeeperApplication.run();
    String zkString = "localhost:" + ports.get(0);

    //send
      //start server
    final Config load = ConfigFactory.load();
    ExpLeagueServer.ServerCfg serverCfg;
    try {
      serverCfg = new ExpLeagueServer.ServerCfg(load);
      ExpLeagueServer.setConfig(serverCfg);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    final ActorSystem system = ActorSystem.create("ExpLeague", load);
    system.actorOf(ActorAdapter.props(LaborExchange.class), "labor-exchange");
    system.actorOf(ActorAdapter.props(XMPPServices.class), "services");
    system.actorOf(ActorAdapter.props(XMPPServer.class), "comm");
    system.actorOf(ActorAdapter.props(NotificationsManager.class, null, null), "notifications");
    system.actorOf(ActorAdapter.props(XMPP.class), "xmpp");

    final String id = "worker";
      //create bot
    BareJID jid = BareJID.bareJIDInstance(id, serverCfg.domain());
    final Bot bot = new Bot(jid, "password", null);
    bot.start();
    bot.sendIq(null, StanzaType.set,
              new StartFlameQuery(id, zkString, "localhost", ports.get(1), WorkerApplication.Guarantees.AT_MOST_ONCE));

    Await.ready(system.terminate(), Duration.Inf());
  }

  public static Set<Integer> freePorts(int n) throws IOException {
    final Set<ServerSocket> sockets = new HashSet<>();
    final Set<Integer> ports = new HashSet<>();
    try {
      for (int i = 0; i < n; ++i) {
        final ServerSocket socket = new ServerSocket(0);
        ports.add(socket.getLocalPort());
        sockets.add(socket);
      }
    } finally {
      for (ServerSocket socket : sockets) {
        socket.close();
      }
    }
    return ports;
  }
}
