package org.racetrack.analyzer;

import org.racetrack.karoapi.*;

public class AnalyzeMove extends LogMove {

  Player player;

  public AnalyzeMove(LogMove move, Player player) {
    super(move);

    this.player = player;
  }

  public Player getPlayer() {
    return player;
  }

}
