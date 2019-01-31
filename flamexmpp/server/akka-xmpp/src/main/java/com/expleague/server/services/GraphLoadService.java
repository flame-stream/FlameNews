package com.expleague.server.services;

import akka.actor.AbstractActor;
import akka.serialization.Serialization;
import akka.serialization.SerializationExtension;
import com.expleague.server.agents.XMPP;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.expleague.flame.ConsumerQuery;
import com.expleague.xmpp.control.expleague.flame.GraphQuery;
import com.expleague.xmpp.control.receipts.Request;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.flamestream.core.Graph;
import com.spbsu.flamestream.runtime.FlameRuntime;
import com.spbsu.flamestream.runtime.RemoteRuntime;
import com.spbsu.flamestream.runtime.config.*;
import com.spbsu.flamestream.runtime.edge.akka.AkkaFront;
import com.spbsu.flamestream.runtime.edge.akka.AkkaFrontType;
import com.spbsu.flamestream.runtime.edge.akka.AkkaRear;
import com.spbsu.flamestream.runtime.edge.akka.AkkaRearType;
import com.spbsu.flamestream.runtime.serialization.KryoSerializer;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.*;
import java.util.stream.Collectors;

public class GraphLoadService extends ActorAdapter<AbstractActor> {
    public static final JID ROOM = new JID("rear", "muc.localhost", null);
    public static final JID OWNER = new JID("worker", "localhost", null);

    private FlameRuntime remoteRuntime;

    @Override
    protected void preStart() {
        final String zkString = FlameConfigService.getZKString();
        final CuratorFramework curator = CuratorFrameworkFactory.newClient(
                zkString,
                new ExponentialBackoffRetry(1000, 3)
        );
        curator.start();
        final ZookeeperWorkersNode workersNode = new ZookeeperWorkersNode(curator, "/workers");
        final ClusterConfig config = ClusterConfig.fromWorkers(workersNode.workers());
        remoteRuntime = new RemoteRuntime(curator, new KryoSerializer(), config);
    }

    @ActorMethod
    public void invoke(Iq<GraphQuery> graphQueryIq) {
        // first member(me@localhost) -- muc creator
        final Graph graph = graphQueryIq.get().graph();
        final FlameRuntime.Flame flame = remoteRuntime.run(graph);
        final List<AkkaFront.FrontHandle<Object>> consumers =
                flame.attachFront("front-room", new AkkaFrontType<>(context().system(), true))
                        .collect(Collectors.toList());
        final List<AkkaRear.Handle<String>> rears =
                flame.attachRear("rear-room", new AkkaRearType<>(context().system(), String.class))
                        .collect(Collectors.toList());
        rears.get(0).addListener(word -> {
          XMPP.send(new Message(OWNER, ROOM, Message.MessageType.GROUP_CHAT, new Message.Body(word), new Request()), context()); // send to room
        });

        final Serialization serialization = SerializationExtension.get(context().getSystem());

        // Адок
        final byte[] front = serialization.serialize(consumers.get(0)).get();
        final byte[] rear = serialization.serialize(rears.get(0)).get();

        final Iq iq = Iq.create(new JID("front", "muc.localhost", null),
                new JID(), Iq.IqType.SET, new ConsumerQuery(front, rear));

        XMPP.send(iq, context());
    }
}
