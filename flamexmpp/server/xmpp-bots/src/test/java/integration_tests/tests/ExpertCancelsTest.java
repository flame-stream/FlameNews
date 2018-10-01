package integration_tests.tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import com.expleague.bots.utils.ReceivingMessage;
import com.expleague.bots.utils.ReceivingMessageBuilder;
import com.expleague.model.*;
import com.expleague.xmpp.JID;
import com.expleague.commons.util.sync.StateLatch;
import integration_tests.BaseRoomTest;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.Collections;

/**
 * User: Artem
 * Date: 06.04.2017
 * Time: 18:34
 */
public class ExpertCancelsTest extends BaseRoomTest {

  @Test
  public void testOneExpertCancelsInWorkStateAnotherAnswers() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ExpertBot firstExpertBot = botsManager.nextExpert();
    final ExpertBot secondExpertBot = botsManager.nextExpert();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomWorkState(testName(), clientBot, adminBot, firstExpertBot, secondExpertBot);

    final ReceivingMessageBuilder invite = new ReceivingMessageBuilder().from(roomJID).has(Offer.class).has(Operations.Invite.class);
    final ReceivingMessage cancel = new ReceivingMessageBuilder().from(botRoomJID(roomJID, firstExpertBot)).has(Operations.Cancel.class).build();

    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Ok());
    secondExpertBot.sendGroupchat(roomJID, new Operations.Ok());
    //Assert
    assertThereAreNoFailedMessages(firstExpertBot.tryReceiveMessages(new StateLatch(), invite.build()));
    assertThereAreNoFailedMessages(secondExpertBot.tryReceiveMessages(new StateLatch(), invite.build()));

    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Cancel());
    //Assert
    assertThereAreNoFailedMessages(adminBot.tryReceiveMessages(new StateLatch(), cancel));

    //Act/Assert
    checkExpertStartsAndAnswers(roomJID, secondExpertBot, clientBot);
  }

  @Test
  public void testOneExpertCancelsInDeliverStateAnotherAnswers() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ExpertBot firstExpertBot = botsManager.nextExpert();
    final ExpertBot secondExpertBot = botsManager.nextExpert();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomWorkState(testName(), clientBot, adminBot, firstExpertBot, secondExpertBot);

    final ReceivingMessageBuilder invite = new ReceivingMessageBuilder().from(roomJID).has(Offer.class).has(Operations.Invite.class);
    final ReceivingMessage cancel = new ReceivingMessageBuilder().from(botRoomJID(roomJID, firstExpertBot)).has(Operations.Cancel.class).build();
    final ReceivingMessageBuilder startAndExpert = new ReceivingMessageBuilder().from(roomJID).has(Operations.Start.class).has(ExpertsProfile.class);
    final ReceivingMessage offerCheck = new ReceivingMessageBuilder().from(domainJID()).has(Offer.class).has(Operations.Check.class).build();

    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Ok());
    //Assert
    assertThereAreNoFailedMessages(firstExpertBot.tryReceiveMessages(new StateLatch(), invite.build()));

    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Start());
    //Assert
    assertThereAreNoFailedMessages(clientBot.tryReceiveMessages(new StateLatch(), startAndExpert.build()));

    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Cancel());
    //Assert
    assertThereAreNoFailedMessages(clientBot.tryReceiveMessages(new StateLatch(), cancel));
    assertThereAreNoFailedMessages(secondExpertBot.tryReceiveMessages(new StateLatch(), offerCheck));

    //Act
    secondExpertBot.sendGroupchat(roomJID, new Operations.Ok());
    //Assert
    assertThereAreNoFailedMessages(secondExpertBot.tryReceiveMessages(new StateLatch(), invite.build()));

    //Act/Assert
    checkExpertStartsAndAnswers(roomJID, secondExpertBot, clientBot);
  }

  @Test
  public void testChosenExpertCancels() throws JaxmppException, InterruptedException { // cancel won't be sent if user have not been introduced to the client
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ExpertBot firstExpertBot = botsManager.nextExpert();
    final ExpertBot secondExpertBot = botsManager.nextExpert();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);

    final Filter expertFilter = new Filter(Collections.singletonList(JID.parse(firstExpertBot.jid().toString())), null, null);
    final Offer offer = new Offer(JID.parse(roomJID.toString()), expertFilter);

    final ReceivingMessageBuilder offerCheck = new ReceivingMessageBuilder().from(domainJID()).has(Offer.class).has(Operations.Check.class);
    final ReceivingMessageBuilder invite = new ReceivingMessageBuilder().from(roomJID).has(Offer.class).has(Operations.Invite.class);
    //final ReceivingMessageBuilder sync = new ReceivingMessageBuilder().has(Operations.Sync.class);
    final ReceivingMessageBuilder cancel = new ReceivingMessageBuilder().from(roomJID).has(Operations.Cancel.class);
    final ReceivingMessageBuilder cancelByFirstExpert = new ReceivingMessageBuilder().from(botRoomJID(roomJID, firstExpertBot)).has(Operations.Cancel.class);
    final ReceivingMessage offerChange = new ReceivingMessageBuilder().from(groupChatJID(roomJID)).has(Operations.OfferChange.class).build();

    //Act
    adminBot.send(roomJID, offer);
    //Assert
    assertThereAreNoFailedMessages(firstExpertBot.tryReceiveMessages(new StateLatch(), offerCheck.build()));

    Thread.sleep(SYNC_WAIT_TIMEOUT_IN_MILLIS); //wait because sync does not work in this case
    if (secondExpertBot.offerCheckReceivedAndReset()) {
      //Act
      secondExpertBot.sendGroupchat(roomJID, new Operations.Ok());
      //Assert
      assertThereAreNoFailedMessages(secondExpertBot.tryReceiveMessages(new StateLatch(), cancel.build()));
    }

    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Ok());
    //Assert
    assertThereAreNoFailedMessages(firstExpertBot.tryReceiveMessages(new StateLatch(), invite.build()));

    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Cancel());
    //Assert
    assertThereAreNoFailedMessages(adminBot.tryReceiveMessages(new StateLatch(), cancelByFirstExpert.build(), offerChange));
    assertThereAreNoFailedMessages(secondExpertBot.tryReceiveMessages(new StateLatch(), offerCheck.build()));

    //Act
    secondExpertBot.sendGroupchat(roomJID, new Operations.Ok());
    //Assert
    assertThereAreNoFailedMessages(secondExpertBot.tryReceiveMessages(new StateLatch(), invite.build()));

    //Act/Assert
    checkExpertStartsAndAnswers(roomJID, secondExpertBot, clientBot);
  }

  private void checkExpertStartsAndAnswers(BareJID roomJID, ExpertBot expertBot, ClientBot clientBot) throws JaxmppException {
    //Arrange
    final Answer answer = new Answer(generateRandomString());
    final ReceivingMessage receivingAnswer = new ReceivingMessageBuilder()
        .from(botRoomJID(roomJID, expertBot))
        .has(Answer.class, a -> answer.value().equals(a.value()))
        .build();
    final ReceivingMessageBuilder startAndExpert = new ReceivingMessageBuilder().from(roomJID).has(Operations.Start.class).has(ExpertsProfile.class);

    //Act
    expertBot.sendGroupchat(roomJID, new Operations.Start());
    //Assert
    assertThereAreNoFailedMessages(clientBot.tryReceiveMessages(new StateLatch(), startAndExpert.build()));

    //Act
    expertBot.sendGroupchat(roomJID, answer);
    //Assert
    assertThereAreNoFailedMessages(clientBot.tryReceiveMessages(new StateLatch(), receivingAnswer));
  }
}
