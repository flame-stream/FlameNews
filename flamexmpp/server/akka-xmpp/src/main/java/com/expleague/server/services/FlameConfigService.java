package com.expleague.server.services;

import akka.actor.*;
import com.expleague.server.services.FlameService.FlameServiceInstance;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.control.expleague.flame.StartFlameQuery;
import com.expleague.xmpp.stanza.Iq;
import com.spbsu.flamestream.runtime.utils.DumbInetSocketAddress;
import scala.runtime.AbstractFunction0;

public class FlameConfigService extends ActorAdapter<AbstractActor> {
  private static String zkString;
  private static DumbInetSocketAddress socketAddress;



  public static String getZkString() {
    return zkString;
  }

  public static DumbInetSocketAddress getSocketAddress() {
      return socketAddress;
  }

  @ActorMethod
  public void invoke(Iq<StartFlameQuery> startIq) {
  final StartFlameQuery startQuery = startIq.get();
  zkString = startQuery.getZkString();
  socketAddress = startQuery.getSocketAddress();
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
  }
}
