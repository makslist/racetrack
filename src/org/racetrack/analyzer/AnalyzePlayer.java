package org.racetrack.analyzer;

import org.eclipse.collections.impl.factory.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public class AnalyzePlayer extends Player {

  public AnalyzePlayer(Player player, LogMove reference, GameRule rule) {
    id = player.id;
    name = player.name;
    game = player.game;
    user = player.user;
    rank = 0;
    status = Status.OK;
    moves = player.moves.select(m -> m.isBefore(reference));
    motion = moves.maxOptional().orElse(null);
    moveCount = motion != null ? motion.getTotalLen() + 1 : 0;
    KaroMap map = game.getMap();
    possibles = motion != null ? motion.getNext() : map.getTilesAsMoves(MapTile.START);
    crashCount = moves.count(m -> m.isCrash());
    if (game.withCps()) {
      checkedCps = map.getCps().select(cp -> rule.hasXdCp(motion, cp));
      missingCps = map.getCps().withoutAll(checkedCps);
    } else {
      checkedCps = Sets.mutable.empty();
      missingCps = Sets.mutable.empty();
    }
  }

}
