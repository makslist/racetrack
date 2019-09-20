package org.racetrack.rules;

import java.util.*;

import org.eclipse.collections.api.block.predicate.*;
import org.racetrack.karoapi.*;
import org.racetrack.track.*;

public class GameRule extends MapRule {

  protected static Move getPredecessor(Collection<Move> moves) {
    for (Move move : moves) {
      for (Move predecessor : move.getPreds()) {
        if (!predecessor.isCrash())
          return predecessor;
        else
          return getPredecessor(Collections.singletonList(predecessor));
      }
    }
    return null;
  }

  protected Game game;

  public GameRule(Game game) {
    super(game.getMap());

    this.game = game;
  }

  public boolean hasForbidXdFinishline(Move move) {
    return isMapCircuit() && hasXdFinishline(move) && !isXingFinishlineAllowed(move);
  }

  public boolean hasNotXdFinishlineOnF1Circuit(LogMove lastMove) {
    return isMapCircuit() && game.isFormula1() && !hasXdFinishline(lastMove);
  }

  public boolean hasXdFinishlineForDist(Move move) {
    return isMapCircuit() && (game.isFormula1() || game.isClassic()) && hasXdFinishline(move);
  }

  protected boolean isXingFinishlineAllowed(Move move) {
    if (isMapCircuit()) {
      switch (game.getDirection()) {
      case classic:
        return finishAngle.isClassicRange(move);
      case formula1:
        return finishAngle.isF1Range(move);
      default: // Dir.free
        return true;
      }
    }
    return true;
  }

  public Predicate<Move> filterPossibles() {
    return mapRule;
  }

  public Paths filterPossibles(Paths possibles) {
    return possibles.filterPossibles(mapRule);
  }

}
