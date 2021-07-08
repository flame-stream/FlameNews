package com.spbsu.flamestream.flamenews.telegram.commands;

import com.esotericsoftware.kryonet.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.Thread.sleep;

/*
  This command executes after /start message to bot
  In this case, we cheking messages and notify the user
 */
public class StartCommand extends BotCommand {
  private static final Logger LOG = LoggerFactory.getLogger(StartCommand.class);
  private static final Queue<Message> messages = new ConcurrentLinkedQueue<>();

  public StartCommand() {
    super("start", "Starting to listen messages");
  }

  @Override
  public void execute(AbsSender absSender, User user, Chat chat, String[] strings) {
    new Thread(() -> {
      while (true) {
        while (!messages.isEmpty()) {
          Message message = messages.poll();
          try {
            sendInfoMessage(absSender, chat, message.getBody());
          } catch (XMLException e) {
            e.printStackTrace();
          }
        }
      }
    }).start();
  }

  public static Queue<Message> getMessages() {
    return messages;
  }

  private static void sendInfoMessage(AbsSender absSender, Chat chat, String text) {
    LOG.info("Sending message {}", text);
    SendMessage echoMessage = new SendMessage();
    echoMessage.setChatId(chat.getId());
    echoMessage.setText(text);

    try {
      absSender.execute(echoMessage);
    } catch (TelegramApiException e) {
      e.printStackTrace();
    }
  }
}
