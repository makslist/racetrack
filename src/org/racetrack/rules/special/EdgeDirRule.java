package org.racetrack.rules.special;

import org.eclipse.collections.api.block.predicate.*;
import org.eclipse.collections.api.collection.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.track.*;

public class EdgeDirRule extends GameRule {

  public static final String TITLE = "$ Richtungswechsel nur am Rand $";

  private Predicate<Move> gameRule = move -> map.isMoveNeighborOf(move.getPred(), MapTile.OFF_TRACK)
      || move.getPred().getAngle() == move.getAngle();

  public EdgeDirRule(Game game) {
    super(game);
  }

  @Override
  public Paths filterPossibles(Paths possibles) {
    Paths filtered = super.filterPossibles(possibles);

    if (filtered.isInCurrentRound())
      return filtered.filterPossibles(gameRule);

    return filtered;
  }

  @Override
  public MutableCollection<Move> filterNextMvDist(Move move) {
    return filterNextMv(move);
  }

  @Override
  public MutableCollection<Move> filterNextMv(Move move) {
    return super.filterNextMv(move).select(gameRule);
  }

}
