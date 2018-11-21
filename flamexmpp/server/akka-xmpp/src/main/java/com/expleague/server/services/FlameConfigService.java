package com.expleague.server.services;

import akka.actor.*;
import com.expleague.server.services.FlameService.FlameServiceInstance;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.control.expleague.flame.StartFlameQuery;
import com.expleague.xmpp.stanza.Iq;
import com.spbsu.flamestream.core.Graph;
import com.spbsu.flamestream.core.graph.Sink;
import com.spbsu.flamestream.core.graph.Source;
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
import scala.runtime.AbstractFunction0;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.stream.Collectors;

import static com.spbsu.flamestream.runtime.FlameRuntime.DEFAULT_MAX_ELEMENTS_IN_GRAPH;
import static com.spbsu.flamestream.runtime.FlameRuntime.DEFAULT_MILLIS_BETWEEN_COMMITS;

public class FlameConfigService extends ActorAdapter<AbstractActor> {
  private Set<Integer> freePorts(int n) throws IOException {
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

  @ActorMethod
  public void invoke(Iq<StartFlameQuery> startIq) throws IOException {
  final StartFlameQuery startQuery = startIq.get();
  context()
      .child(FlameServiceInstance.class.getName())
          .getOrElse(new AbstractFunction0<ActorRef>() {
              @Override
              public ActorRef apply() {
                  return context().actorOf(props(FlameServiceInstance.class,
                          startQuery.getId(), startQuery.getSnapshotPath(), startQuery.getZkString(),
                          startQuery.getGuarantee(), startQuery.getSocketAddress()),
                          FlameServiceInstance.class.getName());
              }
          });
    List<Integer> ports = new ArrayList<>(freePorts(2));
    String zkString = "localhost:" + ports.get(0);
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
    try {
      curator.create().orSetData().forPath("/config", new JacksonSerializer().serialize(config));
      final RemoteRuntime runtime = new RemoteRuntime(curator, new KryoSerializer(), config);
      // приатачить фронт и ривер
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
