package org.racetrack.track;

import org.racetrack.karoapi.*;

public class GameAction {

  private Game game;
  private Move move;
  private Paths paths;
  private boolean quitGame;

  public static GameAction quitGame(Game game) {
    GameAction action = new GameAction(game);
    action.quitGame = true;
    return action;
  }

  private GameAction(Game game) {
    this.game = game;
  }

  public GameAction(Game game, Paths paths, Move move) {
    this.game = game;
    this.paths = paths;
    this.move = move;
  }

  public Game getGame() {
    return game;
  }

  public boolean isQuitGame() {
    return quitGame;
  }

  public Move getMove() {
    return move;
  }

  public boolean hasPathComment() {
    return paths.hasComment();
  }

  public String getPathComment() {
    return paths.getComment();
  }

  public boolean isCrashAhead() {
    return paths.isCrashAhead();
  }

}
