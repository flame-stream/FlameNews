package com.expleague.server.services;

import akka.actor.AbstractActor;
import akka.actor.ActorPath;
import akka.actor.Address;
import akka.actor.RootActorPath;
import akka.serialization.Serialization;
import akka.serialization.SerializationExtension;
import com.expleague.model.Role;
import com.expleague.server.agents.XMPP;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.expleague.flame.ConsumerQuery;
import com.expleague.xmpp.control.expleague.flame.GraphQuery;
import com.expleague.xmpp.muc.MucAdminQuery;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import com.spbsu.flamestream.core.Graph;
import com.spbsu.flamestream.runtime.FlameRuntime;
import com.spbsu.flamestream.runtime.RemoteRuntime;
import com.spbsu.flamestream.runtime.WorkerApplication;
import com.spbsu.flamestream.runtime.config.ClusterConfig;
import com.spbsu.flamestream.runtime.config.ComputationProps;
import com.spbsu.flamestream.runtime.config.HashGroup;
import com.spbsu.flamestream.runtime.config.HashUnit;
import com.spbsu.flamestream.runtime.edge.akka.AkkaFront;
import com.spbsu.flamestream.runtime.edge.akka.AkkaFrontType;
import com.spbsu.flamestream.runtime.edge.akka.AkkaRear;
import com.spbsu.flamestream.runtime.edge.akka.AkkaRearType;
import com.spbsu.flamestream.runtime.serialization.JacksonSerializer;
import com.spbsu.flamestream.runtime.serialization.KryoSerializer;
import com.spbsu.flamestream.runtime.utils.DumbInetSocketAddress;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.*;
import java.util.stream.Collectors;

import static com.expleague.model.Affiliation.MEMBER;
import static com.spbsu.flamestream.runtime.FlameRuntime.DEFAULT_MAX_ELEMENTS_IN_GRAPH;
import static com.spbsu.flamestream.runtime.FlameRuntime.DEFAULT_MILLIS_BETWEEN_COMMITS;

public class GraphLoadService extends ActorAdapter<AbstractActor> {
    private static RemoteRuntime remoteRuntime;
//    private static Set<Integer> freePorts(int n) {
//        final Set<ServerSocket> sockets = new HashSet<>();
//        final Set<Integer> ports = new HashSet<>();
//        try {
//            for (int i = 0; i < n; ++i) {
//                final ServerSocket socket = new ServerSocket(0);
//                ports.add(socket.getLocalPort());
//                sockets.add(socket);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            for (ServerSocket socket : sockets) {
//                try {
//                    socket.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//        return ports;
//    }

    static {
        List<Integer> ports = new ArrayList<Integer>();
        String zkString = FlameConfigService.getZkString();
        final Map<String, ActorPath> workersAddresses = new HashMap<>();
        final String name = "worker";
        final DumbInetSocketAddress address = new DumbInetSocketAddress("localhost", 2553);
        final WorkerApplication worker = new WorkerApplication(name, address, zkString);
        final ActorPath path = RootActorPath.apply(Address.apply(
                "akka",
                "worker",
                address.host(),
                address.port()
        ), "/").child("user").child("watcher");
        workersAddresses.put(name, path);
        worker.run();
        final String masterLocation = workersAddresses.keySet().stream().findAny()
                .orElseThrow(IllegalArgumentException::new);
        final Map<String, HashGroup> rangeMap = new HashMap<>();
        final List<HashUnit> ranges = HashUnit.covering(workersAddresses.size()).collect(Collectors.toList());
        rangeMap.put(name, new HashGroup(Collections.singleton(ranges.get(0))));
        final ClusterConfig config = new ClusterConfig(workersAddresses, masterLocation,
                new ComputationProps(rangeMap, DEFAULT_MAX_ELEMENTS_IN_GRAPH),
                DEFAULT_MILLIS_BETWEEN_COMMITS, 0);
        final CuratorFramework curator = CuratorFrameworkFactory.newClient(
                zkString,
                new ExponentialBackoffRetry(1000, 3)
        );
        curator.start();
        try {
            curator.create().orSetData().forPath("/config", new JacksonSerializer().serialize(config));
            remoteRuntime = new RemoteRuntime(curator, new KryoSerializer(), config);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // JabberIdTo -- room, ends with @muc.localhost
    private void initRoom(JID from, JID to) {
        Presence creation = new Presence(from, to, true);
        XMPP.send(creation, context());
    }

    @ActorMethod
    public void invoke(Iq<GraphQuery> graphQueryIq) {
        // first member(me@localhost) -- muc creator
        JID owner = new JID("test", "muc.localhost", null);
        JID room = new JID("me", "localhost", null);
        initRoom(owner, room);
        // sending iq for agent, so he can read the messages from muc
        Iq setter = Iq.create(owner, room,
                Iq.IqType.SET, new MucAdminQuery("tg", MEMBER, Role.PARTICIPANT));
        XMPP.send(setter, context());
        Graph graph = new KryoSerializer().deserialize(graphQueryIq.get().getSerializeGraph(), Graph.class);
        FlameRuntime.Flame flame = remoteRuntime.run(graph);
        List<AkkaFront.FrontHandle<Object>> consumers =
                flame.attachFront("muc", new AkkaFrontType<>(context().system(), true))
                        .collect(Collectors.toList());
        List<AkkaRear.Handle<String>> rears =
                flame.attachRear("mega-rear", new AkkaRearType<>(context().system(), String.class))
                        .collect(Collectors.toList());
        rears.get(0).addListener((word) -> {
            XMPP.send(new Message(owner, room, word), context());
        }); // send to room

        final Serialization serialization = SerializationExtension.get(context().getSystem());
        final byte[] data1 = serialization.serialize(consumers.get(0)).get();
        final byte[] data2 = serialization.serialize(rears.get(0)).get();

        Iq iq = Iq.create(new JID("super_room3000", "muc.localhost", null),
                new JID(), Iq.IqType.SET, new ConsumerQuery(data1, data2));
        XMPP.send(iq, context());
    }
}
