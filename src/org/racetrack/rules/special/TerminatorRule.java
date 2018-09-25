package org.racetrack.rules.special;

import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.track.*;

public class TerminatorRule extends GameRule {

  public static final String TITLE = "Terminator-WM";

  public TerminatorRule(Game game) {
    super(game);
  }

  @Override
  public boolean isOpponent(Player player) {
    return !game.getNext().equals(player) && !player.isBot();
  }

  @Override
  public boolean isFriend(Player player) {
    return !game.getNext().equals(player) && player.isBot();
  }

  @Override
  public Paths filterPossibles(Paths possibles) {
    Paths filtered = possibles.filterPossibles(mapRule);

    return filtered;
  }

}
