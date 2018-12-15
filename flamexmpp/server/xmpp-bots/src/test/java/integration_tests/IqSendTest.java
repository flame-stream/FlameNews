package integration_tests;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.expleague.bots.Bot;
import com.expleague.bots.utils.Receiving;
import com.expleague.bots.utils.ReceivingMessageBuilder;
import com.expleague.commons.util.sync.StateLatch;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.XMPPServer;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.server.notifications.NotificationsManager;
import com.expleague.server.services.XMPPServices;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.expleague.flame.GraphQuery;
import com.expleague.xmpp.control.expleague.flame.StartFlameQuery;
import com.expleague.xmpp.control.receipts.Request;
import com.expleague.xmpp.muc.MucAdminQuery;
import com.expleague.xmpp.muc.MucItem;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import com.spbsu.flamestream.core.Graph;
import com.spbsu.flamestream.core.graph.Sink;
import com.spbsu.flamestream.core.graph.Source;
import com.spbsu.flamestream.runtime.WorkerApplication;
import com.spbsu.flamestream.runtime.edge.akka.AkkaFront;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.zookeeper.server.ZooKeeperApplication;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import com.spbsu.flamestream.runtime.serialization.KryoSerializer;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static akka.actor.TypedActor.context;
import static com.expleague.model.Affiliation.MEMBER;
import static com.expleague.model.Role.PARTICIPANT;

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
    final JID realJID = new JID(jid.getLocalpart(), jid.getDomain(), null);
    final JID room = new JID("front", "muc.localhost", null);
    Presence pres = new Presence(realJID, room,  true);
    XMPP.send(pres, system);
    StateLatch stateLatch = new StateLatch();
    final Receiving roomCreated = new ReceivingMessageBuilder()
            .from(room)
            .isPresence()
            .build();
    Receiving[] receivedPresence = bot.tryReceiveMessages(stateLatch, 6000000000L, roomCreated);
    if (receivedPresence.length != 1) {
      throw new RuntimeException("Presence wasn't delivered");
    }
    bot.sendIq(null, StanzaType.set,
              new StartFlameQuery(id, zkString, "localhost", 2552, WorkerApplication.Guarantees.AT_MOST_ONCE));
    final Source source = new Source();
    final Sink sink = new Sink();

    final JID rear_room = new JID("rear", "muc.localhost", null);
    Presence creation = new Presence(realJID, rear_room, true);
    XMPP.send(creation, system);
    // sending iq for agent, so he can read the messages from muc
    stateLatch = new StateLatch();
    final Receiving rearRoomCreated = new ReceivingMessageBuilder()
            .from(rear_room)
            .isPresence()
            .build();
    Receiving[] receivedRearPresence = bot.tryReceiveMessages(stateLatch, 6000000000L, rearRoomCreated);
    if (receivedRearPresence.length != 1) {
      throw new RuntimeException("Presence wasn't delivered");
    }
    Iq setter = Iq.create(rear_room, realJID,
            Iq.IqType.SET, new MucAdminQuery("tg", MEMBER, PARTICIPANT));
    XMPP.send(setter, system);
    Presence tg_pres = new Presence(new JID("tg", "localhost", "tg"), rear_room,  true);
    XMPP.send(tg_pres, system);

    bot.sendIq(null, StanzaType.set,
            new GraphQuery(new Graph.Builder().link(source, sink).build(source, sink)));
    Thread.sleep(15000); // тут бы тоже слип убрать
    Message mes = new Message(realJID, room, Message.MessageType.GROUP_CHAT, new Message.Body("Test is OK!"), new Request());
    XMPP.send(mes, system);
    Thread.sleep(50000000);
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
