package org.racetrack.rules.special;

import java.util.*;

import org.eclipse.collections.api.block.predicate.*;
import org.eclipse.collections.api.collection.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.track.*;

public class PrimeAccRule extends GameRule {

  public static final String TITLE = "$ Primzahl Beschleunigen $";

  private static final List<Integer> ONE_AND_PRIMES = Arrays.asList(1, 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41,
      43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163,
      167, 173, 179, 181, 191, 193, 197, 199, 211, 223, 227, 229);

  private Predicate<Move> gameRule = move -> ONE_AND_PRIMES.contains(Integer.valueOf(move.getTotalLen()))
      || (move.getPred() != null && move.getSpeed() <= move.getPred().getSpeed());

  public PrimeAccRule(Game game) {
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
