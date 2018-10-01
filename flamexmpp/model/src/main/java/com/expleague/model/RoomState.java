package com.expleague.model;

/**
 * Experts League
 * Created by solar on 25.12.16.
 */
public enum RoomState {
  OPEN(0),
  CHAT(1),
  RESPONSE(2),
  CONFIRMATION(3),
  OFFER(4),
  WORK(5),
  DELIVERY(6),
  FEEDBACK(7),
  CLOSED(8),
  VERIFY(9),;

  private int code;

  RoomState(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }

  public static RoomState fromCode(int code) {
    for (RoomState type : RoomState.values()) {
      if (type.code() == code) {
        return type;
      }
    }
    return null;
  }
}
