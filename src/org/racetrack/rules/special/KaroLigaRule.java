package org.racetrack.rules.special;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.factory.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public class KaroLigaRule extends GameRule {

  public static final String TITLE = "KaroLiga";

  public KaroLigaRule(Game game) {
    super(game);
  }

  @Override
  public MutableList<Move> filterStartMoves(MutableList<Move> possibles, MutableList<Move> playerMoves, int round) {
    if (round == 1 && possibles.size() != playerMoves.size()) {
      MutableList<Move> filterPossibles = Lists.mutable.ofAll(possibles);
      return filterPossibles.withoutAll(playerMoves);
    } else
      return playerMoves;
  }

}
