package org.racetrack.analyzer;

import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public class AnalyzeGame extends Game {

  public static Game get(int id, String playerName, int round) {
    Game game = Game.get(id);
    GameRule rule = RuleFactory.getInstance(game);
    game.finished = false;

    Player player = game.getActivePlayers(round).detect(p -> p.getName().equals(playerName));
    LogMove refMove = player.getMove(round);

    for (Player p : game.getActivePlayers(round)) {
      Player pl = new AnalyzePlayer(p, refMove, rule);
      game.players.put(pl.getId(), pl);
    }
    game.next = game.players.get(player.getId());

    return game;
  }

}
