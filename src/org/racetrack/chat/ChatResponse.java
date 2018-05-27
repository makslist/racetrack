package org.racetrack.chat;

import org.racetrack.karoapi.*;

public class ChatResponse {

  public static ChatResponse empty() {
    return new ChatResponse();
  }

  private String text;
  private Game game;

  private ChatResponse() {
  }

  public ChatResponse(Game game) {
    this.game = game;
  }

  public ChatResponse(String answer) {
    text = answer;
  }

  public ChatResponse(String answer, Game game) {
    text = answer;
    this.game = game;
  }

  public boolean isAnswered() {
    return isText() || isGameCreated();
  }

  public boolean isText() {
    return text != null;
  }

  public boolean isGameCreated() {
    return game != null;
  }

  public String getText() {
    return text;
  }

  public Game getGame() {
    return game;
  }

  @Override
  public String toString() {
    return text;
  }

}
