package com.spbsu.flamestream.flamenews.telegram;

import com.spbsu.flamestream.flamenews.telegram.commands.HelpCommand;
import com.spbsu.flamestream.flamenews.telegram.commands.StartCommand;
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class NotificationsBot extends TelegramLongPollingCommandBot {
  public NotificationsBot() {
    // provide chosen name for bot
    super("");

    register(new StartCommand());
    register(new HelpCommand(this));
  }

  // provide a token from bot father
  @Override
  public String getBotToken() {
    return "";
  }

  public void processNonCommandUpdate(Update update) {
    if (update.hasMessage()) {
      Message message = update.getMessage();

      SendMessage echoMessage = new SendMessage();
      echoMessage.setChatId(message.getChatId());
      echoMessage.setText("Received message with text:\n" + message.getText());

      try {
        execute(echoMessage);
      } catch (TelegramApiException e) {
        e.printStackTrace();
      }
    }
  }
}