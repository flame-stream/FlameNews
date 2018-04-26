package com.expleague.server.services;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.control.roster.RosterQuery;
import com.expleague.xmpp.model.stanza.Iq;
import com.expleague.xmpp.model.stanza.Presence;
import com.expleague.xmpp.model.stanza.Presence.PresenceType;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.expleague.xmpp.model.control.roster.RosterQuery.RosterItem;
import static com.expleague.xmpp.model.control.roster.RosterQuery.RosterItem.Subscription;

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
      .match(Iq.class, iq -> iq.type() == Iq.IqType.SET, this::onSet)
      .match(Iq.class, iq -> iq.type() == Iq.IqType.GET, this::onGet)
      .match(JID.class, this::getSubscribers)
      .match(
        Presence.class,
        p -> p.type() == PresenceType.SUBSCRIBED || p.type() == PresenceType.UNSUBSCRIBED,
        this::onSubscription
      )
      .build();
  }

  private void onGet(Iq<RosterQuery> rosterIq) {
    final List<RosterItem> items = roster.items(rosterIq.from().local())
      .collect(Collectors.toList());
    sender().tell(Iq.answer(rosterIq, new RosterQuery(items)), self());
  }

  private void onSet(Iq<RosterQuery> setQuery) {
    final RosterItem item = setQuery.get().items().get(0);
    if (item == null) {
      log.warning("Query must contain exactly one item");
      return;
    }

    if (item.subscription() == Subscription.REMOVE) {
      log.fine("Removing roster item: local='" + setQuery + "'" + ", contact='" + item.jid() + ";");

      if (roster.item(setQuery.from().local(), item.jid().bare()) != null) {
        roster.remove(setQuery.from().local(), item.jid().bare());

        sender().tell(Iq.answer(setQuery), self());
        rosterPush(setQuery.from(), new RosterItem(item.jid().bare(), Subscription.REMOVE));
      }
    } else {
      // Ignore other kinds of subscription

      final RosterItem existingItem = roster.item(setQuery.from().local(), item.jid().bare());
      if (existingItem == null) {
        log.fine("Creating new roster item: local='" + setQuery + "'" + ", contact='" + item.jid() + ";");
        roster.create(setQuery.from().local(), new RosterItem(item.jid().bare(), Subscription.NONE));

        sender().tell(Iq.answer(setQuery), self());
        rosterPush(setQuery.from(), new RosterItem(item.jid().bare(), Subscription.NONE));
      } else {
        // There is nothing to change. Yet :)
        // No name, no group
        sender().tell(Iq.answer(setQuery), self());
        rosterPush(setQuery.from(), existingItem);
      }
    }
  }

  private void onSubscription(Presence presence) {
    // There is a roster per user. NOT PER RESOURCE
    // Subscriptions are also on users, not resources

    final JID contact = presence.from().bare();
    final JID requester = presence.to().bare();

    @Nullable final RosterItem requesterView = roster.item(requester.local(), contact);
    @Nullable final RosterItem contactView = roster.item(contact.local(), requester);

    switch (presence.type()) {
      case SUBSCRIBED:
        if (requesterView == null) {
          roster.create(requester.local(), new RosterItem(contact, Subscription.TO));
        } else if (requesterView.subscription() == Subscription.NONE) {
          roster.update(requester.local(), new RosterItem(contact, Subscription.TO));
        } else if (requesterView.subscription() == Subscription.FROM) {
          roster.update(requester.local(), new RosterItem(contact, Subscription.BOTH));
        }

        if (contactView == null) {
          roster.create(contact.local(), new RosterItem(requester, Subscription.FROM));
        } else if (contactView.subscription() == Subscription.NONE) {
          roster.update(contact.local(), new RosterItem(requester, Subscription.FROM));
        } else if (contactView.subscription() == Subscription.TO) {
          roster.update(contact.local(), new RosterItem(requester, Subscription.BOTH));
        }
        break;
      case UNSUBSCRIBED:
        if (requesterView == null) {
          throw new IllegalStateException("Before unsubscribe there should be a subscription");
        }

        if (requesterView.subscription() == Subscription.TO) {
          roster.remove(requester.local(), contact);
        } else if (requesterView.subscription() == Subscription.BOTH) {
          roster.update(requester.local(), new RosterItem(contact, Subscription.FROM));
        }

        if (contactView.subscription() == Subscription.FROM) {
          roster.update(contact.local(), new RosterItem(requester, Subscription.NONE));
        } else if (contactView.subscription() == Subscription.BOTH) {
          roster.update(contact.local(), new RosterItem(requester, Subscription.TO));
        }
        break;
    }
    rosterPush(presence.to(), roster.item(requester.local(), contact));
  }

  private void rosterPush(JID jid, RosterItem item) {
    final RosterQuery query = new RosterQuery(Collections.singletonList(item));
    final Iq<RosterQuery> rosterQueryIq = Iq.create(
      jid,
      null,
      Iq.IqType.SET,
      query
    );
    context().parent().tell(rosterQueryIq, self());
  }

  private void getSubscribers(JID requester) {
    final List<JID> collect = roster.items(requester.local())
      .filter(i -> i.subscription() == Subscription.TO || i.subscription() == Subscription.BOTH)
      .map(RosterItem::jid)
      .collect(Collectors.toList());
    sender().tell(collect, self());
  }
}
