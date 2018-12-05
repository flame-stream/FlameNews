package com.spbsu.flamestream.flamenews.vk;

import com.spbsu.flamestream.flamenews.commons.JabberClient;

import java.time.Instant;

public class XmppStrategy implements OutputStrategy {
  private final JabberClient client;

  public XmppStrategy(String JabberId, String password) {
    final int dogIndex = JabberId.indexOf('@');
    client = new JabberClient(
            JabberId.substring(0, dogIndex),
            JabberId.substring(dogIndex + 1),
            password,
            1000
    );

    client.online();
  }

  @Override
  public void processMessage(Integer creationTime, String message) {
    client.send(Instant.ofEpochSecond(creationTime), message);
  }

  @Override
  public void close() {
    client.offline();
  }

}
