package com.expleague.util.akka;

import akka.actor.Props;
import akka.persistence.AbstractPersistentActor;

/**
 * @author vpdelta
 */
public class PersistentActorContainer extends AbstractPersistentActor {
  private final ActorInvokeDispatcher<PersistentActorAdapter> dispatcher;

  public static Props props(
    final AdapterProps adapterProps,
    final AdapterProps overrideProps
  ) {
    return Props.create(PersistentActorContainer.class, new Object[] {new AdapterProps[] {adapterProps, overrideProps}});
  }

  public PersistentActorContainer(final AdapterProps[] props) {
    dispatcher = new ActorInvokeDispatcher<>(this, props, this::unhandled);
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();
    getAdapterInstance().preStart();
  }

  @Override
  public void postStop() {
    getAdapterInstance().postStop();
    super.postStop();
  }

  public void onReceiveRecover(final Object msg) throws Exception {
    getAdapterInstance().onReceiveRecover(msg);
  }

  public void onReceiveCommand(final Object message) throws Exception {
    if (ActorFailureChecker.checkIfFailure(getAdapterInstance().getClass(), self().path().name(), message)) {
      return;
    }

    MessageCapture.instance().capture(sender(), self(), message);

    dispatcher.invoke(message);
  }

  @Override
  public String persistenceId() {
    return getAdapterInstance().persistenceId();
  }

  private PersistentActorAdapter getAdapterInstance() {
    return dispatcher.getDispatchSequence().get(0).getInstance();
  }

  @Override
  public Receive createReceiveRecover() {
    return receiveBuilder().match(Object.class, this::onReceiveRecover).build();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().match(Object.class, this::onReceiveCommand).build();
  }
}
