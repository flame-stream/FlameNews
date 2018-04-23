package com.expleague.server.services;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.expleague.server.Roster;
import com.expleague.xmpp.model.control.roster.RosterQuery;
import com.expleague.xmpp.model.stanza.Iq;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * User: solar
 * Date: 15.12.15
 * Time: 13:54
 */
public class RosterService extends AbstractActor {
  private static final Logger log = Logger.getLogger(RosterService.class.getName());
  private final Roster roster = new Roster.InMemRoster();

  public static Props props() {
    return Props.create(RosterService.class);
  }

  @Override
  public Receive createReceive() {
    return ReceiveBuilder.create()
      .match(Iq.class, this::onIq)
      .build();
  }

  private void onIq(Iq<RosterQuery> rosterIq) {
    log.fine(rosterIq.toString());
    switch (rosterIq.type()) {
      case GET:
        final List<RosterQuery.RosterItem> items = roster.items(rosterIq.from().local())
          .collect(Collectors.toList());
        sender().tell(Iq.answer(rosterIq, new RosterQuery(items)), self());
        break;
      case SET:
        break;
    }
  }
}
