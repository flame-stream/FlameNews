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
    super("flamestream_bot");

    register(new StartCommand());
    register(new HelpCommand(this));
  }

  @Override
  public String getBotToken() {
    return "700148062:AAElhoTDRl1pRT5IqpShJ0bs1u4JNw6Tv6Q";
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