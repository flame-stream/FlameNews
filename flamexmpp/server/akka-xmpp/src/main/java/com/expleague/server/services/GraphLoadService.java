package com.expleague.server.services;

import akka.actor.AbstractActor;
import akka.actor.ActorPath;
import akka.actor.Address;
import akka.actor.RootActorPath;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.control.expleague.flame.GraphQuery;
import com.expleague.xmpp.stanza.Iq;
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
import com.spbsu.flamestream.runtime.serialization.JacksonSerializer;
import com.spbsu.flamestream.runtime.serialization.KryoSerializer;
import com.spbsu.flamestream.runtime.utils.DumbInetSocketAddress;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.stream.Collectors;

import static com.spbsu.flamestream.runtime.FlameRuntime.DEFAULT_MAX_ELEMENTS_IN_GRAPH;
import static com.spbsu.flamestream.runtime.FlameRuntime.DEFAULT_MILLIS_BETWEEN_COMMITS;

public class GraphLoadService extends ActorAdapter<AbstractActor> {
    private static RemoteRuntime remoteRuntime;
    private static Set<Integer> freePorts(int n) {
        final Set<ServerSocket> sockets = new HashSet<>();
        final Set<Integer> ports = new HashSet<>();
        try {
            for (int i = 0; i < n; ++i) {
                final ServerSocket socket = new ServerSocket(0);
                ports.add(socket.getLocalPort());
                sockets.add(socket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            for (ServerSocket socket : sockets) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return ports;
    }

    static {
        List<Integer> ports = new ArrayList<>(freePorts(2));
        String zkString = FlameConfigService.getZkString();
        final Map<String, ActorPath> workersAddresses = new HashMap<>();
        final String name = "worker";
        final DumbInetSocketAddress address = new DumbInetSocketAddress("localhost", ports.get(1));
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

    @ActorMethod
    public void invoke(Iq<GraphQuery> graphQueryIq) {
        Graph graph = new KryoSerializer().deserialize(graphQueryIq.get().getSerializeGraph(), Graph.class);
        FlameRuntime.Flame flame = remoteRuntime.run(graph);
        final List<AkkaFront.FrontHandle<Object>> consumers =
                flame.attachFront("muc", new AkkaFrontType<>(context().system(), true))
                        .collect(Collectors.toList());
        
    }
}
