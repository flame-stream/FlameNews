package com.expleague.server.services;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.pattern.PatternsCS;
import com.expleague.server.services.flameservice.FlameServiceInstance;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.control.expleague.flame.StartFlameQuery;
import com.expleague.xmpp.stanza.Iq;
import com.spbsu.flamestream.runtime.utils.DumbInetSocketAddress;
import scala.Option;

public class FlameConfigService extends ActorAdapter<AbstractActor> {
  private String zkString;
  private DumbInetSocketAddress socketAddress;

  @ActorMethod
  public void invoke(Iq<StartFlameQuery> startIq) {
  final StartFlameQuery startQuery = startIq.get();
  zkString = startQuery.getZkString();
  socketAddress = startQuery.getSocketAddress();


  final ActorRef service;
    final Option<ActorRef> child = context().child(FlameServiceInstance.class.getName());
    if (child.nonEmpty()) {
      service = context().actorOf(
        FlameServiceInstance.props(
          startQuery.getId(), startQuery.getSnapshotPath(), startQuery.getZkString(),
          startQuery.getGuarantee(), startQuery.getSocketAddress()
        ),
        FlameServiceInstance.class.getName()
      );
    } else {
      service = child.get();
    }

    sender().tell(Iq.answer(startIq), self());
  }

  public void invoke(GimmeZKString gimme) {
    sender().tell(zkString, self());
  }

  public static String getZKString() {
    return PatternsCS.ask('/akk-sysmet/FlCoSservice', new GimmeZKString(), 10);
  }
}
