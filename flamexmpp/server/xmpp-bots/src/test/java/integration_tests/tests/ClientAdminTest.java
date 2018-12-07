package integration_tests.tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import com.expleague.bots.utils.Receiving;
import com.expleague.bots.utils.ReceivingMessageBuilder;
import com.expleague.model.Answer;
import com.expleague.model.Operations;
import com.expleague.xmpp.stanza.Message;
import com.expleague.commons.util.Pair;
import com.expleague.commons.util.sync.StateLatch;
import integration_tests.BaseRoomTest;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.expleague.bots.utils.FunctionalUtils.throwableConsumer;
import static com.expleague.bots.utils.FunctionalUtils.throwableSupplier;

/**
 * User: Artem
 * Date: 15.02.2017
 * Time: 11:51
 */
public class ClientAdminTest extends BaseRoomTest {

  @Test
  public void testAdminHandlesMultipleRooms() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();

    final int roomCount = 3;
    final List<BareJID> rooms = Stream.generate(throwableSupplier(() -> obtainRoomOpenState(testName(), clientBot, adminBot))).limit(roomCount).collect(Collectors.toList());
    final Operations.Progress.MetaChange.Target[] targets = Operations.Progress.MetaChange.Target.values();

    final List<Pair<BareJID, Operations.Progress>> progresses = rooms.stream().flatMap(roomJID -> Arrays.stream(targets).map(target -> new Pair<>(roomJID,
        new Operations.Progress(
            generateRandomString(),
            new Operations.Progress.MetaChange(generateRandomString(),
                Operations.Progress.MetaChange.Operation.ADD,
                target)))))
        .collect(Collectors.toList());
    final Receiving[] expectedProgresses = progresses.stream().map(roomProgress -> new ReceivingMessageBuilder()
        .from(roomProgress.first)
        .has(Operations.Progress.class,
            p -> roomProgress.second.meta().findFirst().orElseGet(Operations.Progress.MetaChange::new).equals(p.meta().findFirst().orElse(null)) &&
                roomProgress.second.order().equals(p.order()))
        .build()).toArray(Receiving[]::new);

    final List<Pair<BareJID, Message.Body>> messages = rooms.stream().map(roomJID -> new Pair<>(roomJID, new Message.Body(generateRandomString()))).collect(Collectors.toList());
    final Receiving[] receivings = messages.stream().map(roomBody -> new ReceivingMessageBuilder()
        .from(botRoomJID(roomBody.first, adminBot))
        .has(Message.Body.class, body -> roomBody.second.value().equals(body.value()))
        .build()).toArray(Receiving[]::new);

    final List<Pair<BareJID, Answer>> answers = rooms.stream().map(roomJID -> new Pair<>(roomJID, new Answer(generateRandomString()))).collect(Collectors.toList());
    final Receiving[] expectedAnswers = answers.stream().map(roomAnswer -> new ReceivingMessageBuilder()
        .from(botRoomJID(roomAnswer.first, adminBot))
        .has(Answer.class, answer -> roomAnswer.second.value().equals(answer.value()))
        .build()).toArray(Receiving[]::new);

    //Act
    progresses.forEach(throwableConsumer(roomProgress -> adminBot.sendGroupchat(roomProgress.first, roomProgress.second)));
    final Receiving[] notReceivedProgress = clientBot.tryReceiveMessages(new StateLatch(), expectedProgresses);
    //Assert
    assertThereAreNoFailedMessages(notReceivedProgress);

    //Act
    messages.forEach(throwableConsumer(roomMessage -> adminBot.sendGroupchat(roomMessage.first, roomMessage.second)));
    final Receiving[] notReceivedMessages = clientBot.tryReceiveMessages(new StateLatch(), receivings);
    //Assert
    assertThereAreNoFailedMessages(notReceivedMessages);

    //Act
    answers.forEach(throwableConsumer(roomAnswer -> adminBot.sendGroupchat(roomAnswer.first, roomAnswer.second)));
    final Receiving[] notReceivedAnswers = clientBot.tryReceiveMessages(new StateLatch(), expectedAnswers);
    //Assert
    assertThereAreNoFailedMessages(notReceivedAnswers);
  }

  @Test
  public void testAdminClosesRoom() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();

    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);
    final Answer answer = new Answer(generateRandomString());
    final Receiving expectedAnswer = new ReceivingMessageBuilder()
        .from(botRoomJID(roomJID, adminBot))
        .has(Answer.class, a -> answer.value().equals(a.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, answer);
    final Receiving[] notReceivedMessages = clientBot.tryReceiveMessages(new StateLatch(), expectedAnswer);
    roomCloseStateByClientFeedback(roomJID, clientBot, adminBot);

    //Assert
    assertThereAreNoFailedMessages(notReceivedMessages);
  }

  @Test
  public void testClientReceivesMessageInOpenRoomState() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();

    //Act/Assert
    testClientReceivesMessage(throwableSupplier(() -> obtainRoomOpenState(testName(), clientBot, adminBot)), true, adminBot, clientBot);
  }

  @Test
  public void testClientReceivesMessageInWorkRoomState() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ExpertBot expertBot = botsManager.nextExpert();
    final ClientBot clientBot = botsManager.nextClient();

    //Act/Assert
    testClientReceivesMessage(throwableSupplier(() -> obtainRoomWorkState(testName(), clientBot, adminBot, expertBot)), true, adminBot, clientBot);
  }

  @Test
  public void testClientReceivesMessageInCloseRoomState() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ExpertBot expertBot = botsManager.nextExpert();
    final ClientBot clientBot = botsManager.nextClient();

    //Act/Assert
    testClientReceivesMessage(throwableSupplier(() -> {
      final BareJID roomJID = obtainRoomWorkState(testName(), clientBot, adminBot, expertBot);
      roomCloseStateByClientCancel(roomJID, clientBot, adminBot);
      return roomJID;
    }), false, adminBot, clientBot);
  }

  private void testClientReceivesMessage(Supplier<BareJID> obtainState, boolean closeRoom, AdminBot adminBot, ClientBot clientBot) throws JaxmppException {
    //Arrange
    final BareJID roomJID = obtainState.get();
    final Message.Body body = new Message.Body(generateRandomString());
    final Receiving receivingFromAdmin = new ReceivingMessageBuilder()
        .from(botRoomJID(roomJID, adminBot))
        .has(Message.Body.class, b -> body.value().equals(b.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, body);
    final Receiving[] notReceivedMessages = clientBot.tryReceiveMessages(new StateLatch(), receivingFromAdmin);
    if (closeRoom) {
      roomCloseStateByClientCancel(roomJID, clientBot, adminBot);
    }

    //Assert
    assertThereAreNoFailedMessages(notReceivedMessages);
  }
}