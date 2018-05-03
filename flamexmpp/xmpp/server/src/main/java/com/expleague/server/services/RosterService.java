package com.expleague.server.services;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.control.roster.RosterQuery;
import com.expleague.xmpp.model.stanza.Iq;
import com.expleague.xmpp.model.stanza.Presence;
import com.expleague.xmpp.model.stanza.Presence.PresenceType;
import com.expleague.xmpp.model.stanza.data.Err;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.expleague.xmpp.model.control.roster.RosterQuery.RosterItem;
import static com.expleague.xmpp.model.control.roster.RosterQuery.RosterItem.Ask;
import static com.expleague.xmpp.model.control.roster.RosterQuery.RosterItem.Subscription;

/**
 * User: solar
 * Date: 15.12.15
 * Time: 13:54
 */
public class RosterService extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(context().system(), self());

  private final Roster roster;
  private final ActorRef xmpp;

  public RosterService(ActorRef xmpp, Roster roster) {
    this.roster = roster;
    this.xmpp = xmpp;
  }

  public static Props props(ActorRef xmpp, Roster roster) {
    return Props.create(RosterService.class, xmpp, roster);
  }

  @Override
  public Receive createReceive() {
    return ReceiveBuilder.create()
      .match(Iq.class, iq -> iq.type() == Iq.IqType.GET, this::onGet)
      .match(Iq.class, iq -> iq.type() == Iq.IqType.SET, this::onSet)
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
      remove(setQuery);
    } else {
      final JID requester = setQuery.from();
      // Ignore other kinds of subscription

      final RosterItem existingItem = roster.item(requester.local(), item.jid().bare());
      if (existingItem == null) {
        log.debug("Creating new roster item: local='{}', contact='{}'", requester, item.jid());
        roster.create(requester.local(), new RosterItem(item.jid().bare(), Subscription.NONE));

        sender().tell(Iq.answer(setQuery), self());
        rosterPush(requester, new RosterItem(item.jid().bare(), Subscription.NONE));
      } else {
        // There is nothing to change. Yet :)
        // No name, no group
        sender().tell(Iq.answer(setQuery), self());
        rosterPush(requester, existingItem);
      }
    }
  }

  private void remove(Iq<RosterQuery> setQuery) {
    final JID contact = setQuery.get().items().get(0).jid().bare();
    final JID requester = setQuery.from();
    final RosterItem item = roster.item(requester.local(), contact);

    log.info("Removing roster item: local='{}', contact='{}'", requester, contact);

    if (item == null) {
      sender().tell(
        Iq.error(setQuery, new Err(Err.Cause.ITEM_NOT_FOUND, Err.ErrType.MODIFY, "No such element")),
        self()
      );
      return;
    }

    roster.remove(requester.local(), contact);
    sender().tell(Iq.answer(setQuery), self());
    rosterPush(requester, new RosterItem(contact, Subscription.REMOVE));

    if (item.subscription() == Subscription.TO || item.subscription() == Subscription.BOTH) {
      unsubscribe(requester, contact);
      //xmpp.tell(new Presence(requester, contact, PresenceType.UNSUBSCRIBE), self());
    }

    if (item.subscription() == Subscription.FROM || item.subscription() == Subscription.BOTH) {
      unsubscribe(contact, requester.bare());
      //xmpp.tell(new Presence(requester.bare(), contact, PresenceType.UNSUBSCRIBED), self());
    }
  }

  private void onSubscription(Presence presence) {
    final JID from = presence.from();
    final JID to = presence.to();
    if (to.hasResource() || from.hasResource()) {
      log.error("Presence has resource: {}", presence);
      return;
    }

    switch (presence.type()) {
      case SUBSCRIBE:
        askSubscribe(from, to);
        rosterPush(from, roster.item(from.local(), to));
        break;
      case UNSUBSCRIBE:
        askUnsubscribe(from, to);
        rosterPush(from, roster.item(from.local(), to));
        break;
      case SUBSCRIBED:
        subscribe(to, from);
        rosterPush(to, roster.item(to.local(), from));
        rosterPush(from, roster.item(from.local(), to));
        break;
      case UNSUBSCRIBED:
        unsubscribe(to, from);
        rosterPush(to, roster.item(to.local(), from));
        rosterPush(from, roster.item(from.local(), to));
        break;
      default:
        unhandled(presence);
    }
  }

  private void askSubscribe(JID requester, JID contact) {
    @Nullable final RosterItem requesterView = roster.item(requester.local(), contact);

    if (requesterView == null) {
      roster.create(requester.local(), new RosterItem(contact, Subscription.NONE, Ask.SUBSCRIBE));
    } else if (requesterView.subscription() == Subscription.NONE) {
      roster.update(requester.local(), new RosterItem(contact, Subscription.NONE, Ask.SUBSCRIBE));
    } else {
      log.warning("AskSubscribe unhandled case requester: {}, contact: {}", requester, contact);
    }
  }

  private void askUnsubscribe(JID requester, JID contact) {
    @Nullable final RosterItem requesterView = roster.item(requester.local(), contact);

    if (requesterView == null) {
      // do nothing
    } else if (requesterView.subscription() == Subscription.NONE) {
      roster.update(requester.local(), new RosterItem(contact, Subscription.NONE, Ask.UNSUBSCRIBE));
    } else {
      log.warning("AskUnsubscribe unhandled case requester: {}, contact: {}", requester, contact);
    }
  }

  private void subscribe(JID requester, JID contact) {
    @Nullable final RosterItem requesterView = roster.item(requester.local(), contact);
    @Nullable final RosterItem contactView = roster.item(contact.local(), requester);

    if (requesterView == null) {
      roster.create(requester.local(), new RosterItem(contact, Subscription.TO));
    } else if (requesterView.subscription() == Subscription.NONE) {
      roster.update(requester.local(), new RosterItem(contact, Subscription.TO));
    } else if (requesterView.subscription() == Subscription.FROM) {
      roster.update(requester.local(), new RosterItem(contact, Subscription.BOTH));
    } else {
      log.warning("Subscribe unhandled case requester: {}, contact: {}", requester, contact);
    }

    if (contactView == null) {
      roster.create(contact.local(), new RosterItem(requester, Subscription.FROM));
    } else if (contactView.subscription() == Subscription.NONE) {
      roster.update(contact.local(), new RosterItem(requester, Subscription.FROM));
    } else if (contactView.subscription() == Subscription.TO) {
      roster.update(contact.local(), new RosterItem(requester, Subscription.BOTH));
    } else {
      log.warning("Subscribe unhandled case requester: {}, contact: {}", requester, contact);
    }
  }

  private void unsubscribe(JID requester, JID contact) {
    @Nullable final RosterItem requesterView = roster.item(requester.local(), contact);
    @Nullable final RosterItem contactView = roster.item(contact.local(), requester);

    if (requesterView == null) {
      // do nothing
    } else if (requesterView.subscription() == Subscription.NONE) {
      // Remove possible ask
      roster.update(requester.local(), new RosterItem(contact, Subscription.NONE));
    } else if (requesterView.subscription() == Subscription.TO) {
      roster.update(requester.local(), new RosterItem(contact, Subscription.NONE));
    } else if (requesterView.subscription() == Subscription.BOTH) {
      roster.update(requester.local(), new RosterItem(contact, Subscription.FROM));
    } else {
      log.warning("Unsubscribe unhandled case requester: {}, contact: {}", requester, contact);
    }

    if (contactView == null) {
      // do nothing
    } else if (contactView.subscription() == Subscription.FROM) {
      roster.update(contact.local(), new RosterItem(requester, Subscription.NONE));
    } else if (contactView.subscription() == Subscription.BOTH) {
      roster.update(contact.local(), new RosterItem(requester, Subscription.TO));
    } else {
      log.warning("Unsubscribe unhandled case requester: {}, contact: {}", requester, contact);
    }
  }

  private void rosterPush(JID jid, RosterItem item) {
    final RosterQuery query = new RosterQuery(Collections.singletonList(item));
    final Iq<RosterQuery> rosterQueryIq = Iq.create(
      jid,
      null,
      Iq.IqType.SET,
      query
    );
    xmpp.tell(rosterQueryIq, self());
  }

  private void getSubscribers(JID requester) {
    final List<JID> collect = roster.items(requester.local())
      .filter(i -> i.subscription() == Subscription.TO || i.subscription() == Subscription.BOTH)
      .map(RosterItem::jid)
      .collect(Collectors.toList());
    sender().tell(collect, self());
  }
}