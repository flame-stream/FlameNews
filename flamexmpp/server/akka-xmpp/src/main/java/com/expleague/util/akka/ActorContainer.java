package com.expleague.util.akka;

import akka.actor.AbstractActor;
import akka.actor.Props;

/**
 * @author vpdelta
 */
public class ActorContainer extends AbstractActor {
  private final ActorInvokeDispatcher<ActorAdapter> dispatcher;

  public static Props props(
    final AdapterProps adapterProps,
    final AdapterProps overrideProps
  ) {
    return Props.create(ActorContainer.class, new Object[]{new AdapterProps[]{adapterProps, overrideProps}});
  }

  public ActorContainer(AdapterProps[] props) {
    dispatcher = new ActorInvokeDispatcher<>(this, props, this::unhandled);
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();
    getAdapterInstance().preStart();
  }

  @Override
  public void postStop() throws Exception {
    getAdapterInstance().postStop();
    super.postStop();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().match(Object.class, this::onReceive).build();
  }

  public void onReceive(final Object message) throws Exception {
    if (ActorFailureChecker.checkIfFailure(getAdapterInstance().getClass(), self().path().name(), message)) {
      return;
    }

    MessageCapture.instance().capture(sender(), self(), message);

    dispatcher.invoke(message);
  }

  private ActorAdapter getAdapterInstance() {
    return dispatcher.getDispatchSequence().get(0).getInstance();
  }
}
