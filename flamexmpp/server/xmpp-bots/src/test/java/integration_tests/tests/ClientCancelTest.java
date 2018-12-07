package integration_tests.tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import com.expleague.bots.utils.Receiving;
import com.expleague.bots.utils.ReceivingMessageBuilder;
import com.expleague.model.Answer;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.model.RoomState;
import com.expleague.xmpp.stanza.Message;
import com.expleague.commons.util.sync.StateLatch;
import integration_tests.BaseRoomTest;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

/**
 * User: Artem
 * Date: 04.04.2017
 * Time: 16:18
 */
public class ClientCancelTest extends BaseRoomTest {

  @Test
  public void testClientCancelsAfterOrderAdminOff() throws JaxmppException {
    //Arrange
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot);
    clientBot.sendGroupchat(roomJID, new Operations.Cancel());
    final Receiving roomInfo = new ReceivingMessageBuilder()
        .from(groupChatJID(roomJID))
        .has(Offer.class)
        .has(Operations.RoomStateChanged.class, rsc -> RoomState.CLOSED == rsc.state())
        .build();

    //Act
    final AdminBot adminBot = botsManager.nextAdmin();
    final Receiving[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(), roomInfo);

    //Assert
    assertThereAreNoFailedMessages(notReceivedMessages);
  }

  @Test
  public void testClientCancelsAfterOrderAdminOn() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);

    //Act/Assert
    checkAdminHandlesCancel(roomJID, clientBot, adminBot);
  }

  @Test
  public void testClientCancelsAfterAdminMessage() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();

    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);
    final Message.Body body = new Message.Body(generateRandomString());
    final Receiving message = new ReceivingMessageBuilder().from(botRoomJID(roomJID, adminBot)).has(Message.Body.class, b -> body.value().equals(b.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, body);
    final Receiving[] notReceivedMessagesByClient = clientBot.tryReceiveMessages(new StateLatch(), message);
    //Assert
    assertThereAreNoFailedMessages(notReceivedMessagesByClient);

    //Act/Assert
    checkAdminHandlesCancel(roomJID, clientBot, adminBot);
  }

  @Test
  public void testClientCancelsAfterShortAnswer() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();

    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);
    final Answer answer = new Answer(generateRandomString());
    final Receiving expectedAnswer = new ReceivingMessageBuilder().from(botRoomJID(roomJID, adminBot)).has(Answer.class, a -> answer.value().equals(a.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, answer);
    final Receiving[] notReceivedMessagesByClient = clientBot.tryReceiveMessages(new StateLatch(), expectedAnswer);
    //Assert
    assertThereAreNoFailedMessages(notReceivedMessagesByClient);

    //Act/Assert
    checkAdminHandlesCancel(roomJID, clientBot, adminBot);
  }

  @Test
  public void testClientCancelsInWorkState() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ExpertBot expertBot = botsManager.nextExpert();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomWorkState(testName(), clientBot, adminBot, expertBot);

    //Act/Assert
    checkAdminHandlesCancel(roomJID, clientBot, adminBot);
  }

//  @Test
//  public void testClientCancelsInDeliverState() throws JaxmppException { // client in delivery state is not connected to the room
//    //Arrange
//    final AdminBot adminBot = botsManager.nextAdmin();
//    final ExpertBot expertBot = botsManager.nextExpert();
//    final ClientBot clientBot = botsManager.nextClient();
//    final BareJID roomJID = obtainRoomDeliverState(testName(), clientBot, adminBot, expertBot);
//
//    //Act/Assert
//    checkAdminAndExpertHandleCancel(roomJID, clientBot, adminBot, expertBot);
//  }

  @Test
  public void testClientCancelsAfterAnswer() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ExpertBot expertBot = botsManager.nextExpert();
    final ClientBot clientBot = botsManager.nextClient();

    final BareJID roomJID = obtainRoomFeedbackState(testName(), clientBot, adminBot, expertBot);
    final Receiving cancel = new ReceivingMessageBuilder().from(botRoomJID(roomJID, clientBot)).has(Operations.Cancel.class).build();

    //Act
    clientBot.sendGroupchat(roomJID, new Operations.Cancel());

    //Assert
    assertThereAreNoFailedMessages(adminBot.tryReceiveMessages(new StateLatch(), cancel));
  }

  private void checkAdminHandlesCancel(BareJID roomJID, ClientBot clientBot, AdminBot adminBot) throws JaxmppException {
    //Arrange
    final Receiving roomStateChanged = new ReceivingMessageBuilder().from(groupChatJID(roomJID)).has(Operations.RoomStateChanged.class, rsc -> RoomState.CLOSED == rsc.state()).build();

    //Act
    clientBot.sendGroupchat(roomJID, new Operations.Cancel());
    final Receiving[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(), roomStateChanged);

    //Assert
    assertThereAreNoFailedMessages(notReceivedMessages);
  }
}
