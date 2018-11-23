package com.spbsu.flamestream.flamenews.telegram;

import com.spbsu.flamestream.flamenews.commons.JabberClient;
import com.spbsu.flamestream.flamenews.telegram.commands.StartCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.util.concurrent.CountDownLatch;

public class TelegramAgent extends JabberClient {
  private static final Logger LOG = LoggerFactory.getLogger(TelegramAgent.class);

  public static TelegramAgent getAgent(String JabberId, String password, MessageHandler handler) throws TelegramApiRequestException {
    final int dogIndex = JabberId.indexOf('@');

    return new TelegramAgent(
            JabberId.substring(0, dogIndex),
            JabberId.substring(dogIndex + 1),
            password,
            handler
    );
  }

  public TelegramAgent(String id, String domain, String password, MessageHandler handler) throws TelegramApiRequestException {
    super(id, domain, password, handler);
    startBot();

    online();
  }

  private void startBot() throws TelegramApiRequestException {
    ApiContextInitializer.init();
    TelegramBotsApi telegramBotsApi = new TelegramBotsApi();

    telegramBotsApi.registerBot(new NotificationsBot());
  }

  public static void main(String[] args) throws TelegramApiRequestException {
    if (args.length != 2) {
      throw new IllegalArgumentException(
              "Parameters number is invalid. Please set {Jabber JID} {Jabber password}");
    }

    String jabberId = args[0];
    String password = args[1];
    MessageHandler handler = new MessageHandler();

    TelegramAgent agent = getAgent(jabberId, password, handler);
    LOG.info("The bot has been started");
  }
}
