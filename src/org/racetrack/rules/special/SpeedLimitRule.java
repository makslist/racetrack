package org.racetrack.rules.special;

import java.util.regex.*;

import org.eclipse.collections.api.block.predicate.*;
import org.eclipse.collections.api.collection.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.track.*;

public class SpeedLimitRule extends GameRule {

  public static final String TITLE = "$ Geschwindigkeitsbegrenzung $";

  private int speedlimit = 10; // default

  private Predicate<Move> gameRule = move -> move.getSpeed() <= speedlimit;

  public SpeedLimitRule(Game game) {
    super(game);

    String title = game.getName();
    int startIndex = title.indexOf(RuleFactory.RuleType.SPEED_LIMIT.toString());

    Matcher matcher = Pattern.compile("-?\\d+").matcher(title);
    if (matcher.find(startIndex)) {
      speedlimit = Integer.parseInt(matcher.group());
    }
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
