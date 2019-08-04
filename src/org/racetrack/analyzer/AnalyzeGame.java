package org.racetrack.analyzer;

import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public class AnalyzeGame extends Game {

  public static Game get(int id, String playerLogin, int round) {
    Game game = Game.get(id);
    Player player = null;
    for (Player p : game.getActivePlayers(round)) {
      if (p.getName().equals(playerLogin)) {
        player = p;
      }
    }
    game.finished = false;
    game.next = player;

    GameRule rule = RuleFactory.getInstance(game);

    LogMove refMove = player.getMove(round);
    player.motion = player.getMove(round - 1);
    player.moveCount = player.getMotion() != null ? player.getMotion().getTotalLen() + 1 : 0;
    player.possibles = player.getMotion() != null ? player.getMotion().getNext()
        : game.getMap().getTilesAsMoves(MapTile.START);
    player.rank = 0;
    if (game.withCps()) {
      for (MapTile cp : game.getMap().getCps())
        if (rule.hasXdCp(player.motion, cp)) {
          player.checkedCps.add(cp);
          player.missingCps.remove(cp);
        } else {
          player.checkedCps.remove(cp);
          player.missingCps.add(cp);
        }
    }

    for (Player p : game.getActivePlayers()) {
      if (p != player) {
        LogMove move = p.getMove(round);
        if (move != null) {
          p.motion = move.isBefore(refMove) ? move : p.getMove(round - 1);
          p.moveCount = p.motion != null ? p.motion.getTotalLen() + 1 : 0;
          p.rank = 0;

          if (game.withCps()) {
            for (MapTile cp : game.getMap().getCps())
              if (rule.hasXdCp(player.motion, cp)) {
                player.checkedCps.add(cp);
                player.missingCps.remove(cp);
              } else {
                player.checkedCps.remove(cp);
                player.missingCps.add(cp);
              }
          }
        } else {
          p.motion = null;
          p.moveCount = 0;
          p.rank = 0;
        }
      }
    }

    return game;
  }

}
