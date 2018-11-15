package com.expleague.server.services.FlameService;

import akka.actor.AbstractActor;
import com.expleague.util.akka.ActorAdapter;
import com.spbsu.flamestream.runtime.WorkerApplication;
import com.spbsu.flamestream.runtime.utils.DumbInetSocketAddress;

public class FlameServiceInstance extends ActorAdapter<AbstractActor> {
  public FlameServiceInstance(String id, String snapshotPath, String zkString,
                              WorkerApplication.Guarantees guarantee, DumbInetSocketAddress socketAddress) {
    if (guarantee == WorkerApplication.Guarantees.AT_MOST_ONCE) {
      new WorkerApplication(id, socketAddress, zkString).run();
    } else {
      new WorkerApplication(id, socketAddress, zkString, snapshotPath).run();
    }
  }
}
