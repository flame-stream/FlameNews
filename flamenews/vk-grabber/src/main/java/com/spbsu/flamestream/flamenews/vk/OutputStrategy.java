package com.spbsu.flamestream.flamenews.vk;

public interface OutputStrategy {
  void processMessage(Integer creationTime, String message);

  void close();
}
