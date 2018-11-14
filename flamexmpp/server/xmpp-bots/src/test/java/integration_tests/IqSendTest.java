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
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;

import java.util.HashMap;
import java.util.Map;

public class IqSendTest {
  @Test
  public void testAdminHandlesMultipleRooms() throws JaxmppException {
    //init zk, zk is unique
    this.zooKeeperApplication = new ZooKeeperApplication(ports.get(0));
    zooKeeperApplication.run();
    zkString = "localhost:" + ports.get(0);

    //send
    for (int i = 0; i < 3; /*parallelism param*/ i++) {
      { //start server
        final Config load = ConfigFactory.load();
        try {
          final ExpLeagueServer.ServerCfg serverCfg = new ExpLeagueServer.ServerCfg(load);
          domain = serverCfg.domain();
          ExpLeagueServer.setConfig(serverCfg);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }

        final ActorSystem system = ActorSystem.create("ExpLeague_" + i, load);
        system.actorOf(ActorAdapter.props(LaborExchange.class), "labor-exchange");
        system.actorOf(ActorAdapter.props(XMPPServices.class), "services");
        system.actorOf(ActorAdapter.props(XMPPServer.class), "comm");
        system.actorOf(ActorAdapter.props(NotificationsManager.class, null, null), "notifications");
        system.actorOf(ActorAdapter.props(XMPP.class), "xmpp");
      }


      final String id = "worker" + i;
      final DumbInetSocketAddress address = new DumbInetSocketAddress("localhost", ports.get(i + 1));

      //create bot
      final Bot bot = new Bot();
      bot.send(null, StanzaType.normal, new StartFlameQuery());
    }
  }
}
