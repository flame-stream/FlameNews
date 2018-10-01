package com.expleague.util.akka;

import akka.actor.AbstractActor;
import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.expleague.commons.func.Action;

/**
 * @author vpdelta
 */
public abstract class ActorAdapter<A extends AbstractActor> {
  private static boolean unitTestEnabled = false;
  
  protected A actor;
  protected Action<Object> unhandled;

  public static Props props(Class<? extends ActorAdapter> adapter, Object... args) {
    if (unitTestEnabled && PersistentActorAdapter.class.isAssignableFrom(adapter))
      return Props.create(FakePersistentActorContainer.class, new Object[]{new AdapterProps[]{AdapterProps.create(adapter, args)}});
    else if (PersistentActorAdapter.class.isAssignableFrom(adapter))
      return Props.create(PersistentActorContainer.class, new Object[]{new AdapterProps[]{AdapterProps.create(adapter, args)}});
    else
      return Props.create(ActorContainer.class, new Object[]{new AdapterProps[]{AdapterProps.create(adapter, args)}});
  }

  public ActorAdapter() {
    // to make it possible to create adapter outside of actor system (in tests)
  }

  public final void injectActor(final A actor) {
    this.actor = actor;
    this.unhandled = actor::unhandled;
    init();
  }

  public final void injectUnhandled(final Action<Object> unhandled) {
    this.unhandled = unhandled;
  }

  protected void init() {
  }

  protected void preStart() throws Exception {
  }

  protected void postStop() {
  }

  protected void stash() {
    if (actor instanceof AbstractActorWithStash) {
      ((AbstractActorWithStash)actor).stash();
    }
  }

  protected void unstashAll() {
    if (actor instanceof AbstractActorWithStash) {
      ((AbstractActorWithStash) actor).stash();
    }
  }

  // todo: in future we can have complete delegation here
  public ActorRef self() {
    return getActor().self();
  }

  protected A getActor() {
    if (actor == null) {
      throw new IllegalStateException("Actor is not injected");
    }
    return actor;
  }

  public ActorRef sender() {
    return getActor().sender();
  }

  public AbstractActor.ActorContext context() {
    return getActor().getContext();
  }

  public void unhandled(Object message) {
    this.unhandled.invoke(message);
  }

  public void reply(final Object message) {
    replyTo(sender(), message);
  }

  public void replyTo(ActorRef recipient, final Object message) {
    recipient.tell(message, self());
  }

  public ActorRef actorOf(final Class<? extends ActorAdapter> adapter, final Object... args) {
    return context().actorOf(props(adapter, args));
  }

  public static void setUnitTestEnabled(boolean unitTestEnabled) {
    ActorAdapter.unitTestEnabled = unitTestEnabled;
  }
}
