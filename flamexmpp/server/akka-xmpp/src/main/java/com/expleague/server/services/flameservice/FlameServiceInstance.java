package com.expleague.server.services.flameservice;

import akka.actor.AbstractActor;
import akka.actor.Props;
import com.expleague.util.akka.ActorAdapter;
import com.spbsu.flamestream.runtime.WorkerApplication;
import com.spbsu.flamestream.runtime.utils.DumbInetSocketAddress;
import org.jetbrains.annotations.Nullable;

import static com.spbsu.flamestream.runtime.WorkerApplication.Guarantees.AT_MOST_ONCE;

public class FlameServiceInstance extends ActorAdapter<AbstractActor> {
  @Nullable
  private WorkerApplication application = null;
  private String id;

  public static Props props(String id,
                            String snapshotPath,
                            String zkString,
                            WorkerApplication.Guarantees guarantee,
                            DumbInetSocketAddress socketAddress) {
    return Props.create(FlameServiceInstance.class, id, snapshotPath, zkString, guarantee, socketAddress);
  }

  private FlameServiceInstance(String id, String snapshotPath, String zkString,
                              WorkerApplication.Guarantees guarantee, DumbInetSocketAddress socketAddress) {

    this.id = id;
    // поля
  }

  @Override
  protected void preStart() throws Exception {
    final WorkerApplication.WorkerConfig.Builder builder = new WorkerApplication.WorkerConfig.Builder();
    switch (guarantee) {
      case AT_MOST_ONCE:
        break;
      case AT_LEAST_ONCE:
      case EXACTLY_ONCE:
        builder.snapshotPath(snapshotPath);
    }
    application = new WorkerApplication(builder.build(id, socketAddress, zkString));
    application.run();
  }

  @Override
  protected void postStop() {
    application.close();
  }
}
