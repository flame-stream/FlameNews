package com.expleague.server.services;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import com.expleague.server.services.FlameService.FlameServiceInstance;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.control.expleague.flame.StartFlameQuery;
import com.expleague.xmpp.stanza.Iq;
import scala.runtime.AbstractFunction0;

public class FlameConfigService extends ActorAdapter<AbstractActor> {
    @ActorMethod
    public void invoke(Iq<StartFlameQuery> startIq) {
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
    }
}
