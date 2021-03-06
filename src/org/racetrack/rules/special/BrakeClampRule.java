package org.racetrack.rules.special;

import org.eclipse.collections.api.block.predicate.*;
import org.eclipse.collections.api.collection.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.track.*;

public class BrakeClampRule extends GameRule {

  public static final String TITLE = "$ Bremspedal klemmt $";

  private Predicate<Move> gameRule = move -> move.getPred() == null || move.getSpeed() >= move.getPred().getSpeed();

  public BrakeClampRule(Game game) {
    super(game);
  }

  @Override
  public Paths filterPossibles(Paths possibles) {
    Paths filtered = super.filterPossibles(possibles);

    if (filtered.isInCurrentRound() && filtered.getEndMoves().anySatisfy(gameRule))
      return filtered.filterPossibles(gameRule);

    return filtered;
  }

  @Override
  public MutableCollection<Move> filterNextMvDist(Move move) {
    return filterNextMv(move);
  }

  @Override
  public MutableCollection<Move> filterNextMv(Move move) {
    MutableCollection<Move> nextMoves = super.filterNextMv(move);

    if (nextMoves.anySatisfy(gameRule))
      return nextMoves.select(gameRule);
    return nextMoves;
  }

}
