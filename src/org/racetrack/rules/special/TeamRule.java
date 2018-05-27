package org.racetrack.rules.special;

import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public class TeamRule extends GameRule {

  public static final String TITLE = "Team-WM";

  public TeamRule(Game game) {
    super(game);
  }

  @Override
  public boolean isOpponent(Player player) {
    return !game.getDranPlayer().equals(player) && !player.isBot();
  }

  @Override
  public boolean isFriend(Player player) {
    return !game.getDranPlayer().equals(player) && player.isBot();
  }

}
