package org.racetrack.track;

import org.racetrack.karoapi.*;

public class GameAction {

  private Game game;
  private boolean quitGame;
  private boolean skipGame;
  private boolean notNext;
  private boolean crash;
  private Move move;
  private String comment;

  public static GameAction quitGame(Game game) {
    GameAction action = new GameAction(game);
    action.quitGame = true;
    return action;
  }

  public static GameAction notNext(Game game) {
    GameAction action = new GameAction(game);
    action.notNext = true;
    return action;
  }

  public static GameAction skipGame(Game game, String reason) {
    GameAction action = new GameAction(game);
    action.skipGame = true;
    action.comment = reason != null ? reason : "";
    return action;
  }

  public static GameAction crash(Game game) {
    GameAction action = new GameAction(game);
    action.crash = true;
    return action;
  }

  private GameAction(Game game) {
    this.game = game;
  }

  public GameAction(Game game, Move move, String comment) {
    this.game = game;
    this.comment = comment;
    this.move = move;
  }

  public Game getGame() {
    return game;
  }

  public boolean quitGame() {
    return quitGame;
  }

  public boolean isNotNext() {
    return notNext;
  }

  public boolean skipGame() {
    return skipGame;
  }

  public boolean isCrash() {
    return crash;
  }

  public Move getMove() {
    return move;
  }

  public boolean hasComment() {
    return comment != null && !"".equals(comment);
  }

  public String getComment() {
    return comment;
  }

}
