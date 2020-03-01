package org.racetrack.track;

import java.text.*;
import java.util.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.primitive.*;
import org.eclipse.collections.impl.factory.primitive.*;
import org.racetrack.karoapi.*;
import org.racetrack.track.GTS.*;

public class Strategy {

  private enum Type {
    MaxN, Paranoid, Offensive;
  }

  class Evaluation {

    private float[] ratings = null;

    private Evaluation(int players) {
      ratings = new float[players];
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (float value : ratings) {
        if (sb.length() != 0) {
          sb.append(", ");
        }
        sb.append(DECIMAL_FORMAT.format(value));
      }
      return new StringBuilder().append("[").append(sb).append("]").toString();
    }

    private int getPosition(int player) {
      float playerRating = ratings[player];
      int position = 1;
      for (int i = 0; i < ratings.length; i++) {
        if (player != i && ratings[i] > playerRating) {
          position++;
        }
      }
      return position;
    }

    private float getLead(int player) {
      float sucRating = 0;
      for (int i = 0; i < ratings.length; i++) {
        if (player != i && ratings[i] > sucRating) {
          sucRating = ratings[i];
        }
      }
      return sucRating - ratings[player];
    }

    private float getBacklog(int player) {
      float playerRating = ratings[player];
      float predRating = 0;
      for (int i = 0; i < ratings.length; i++) {
        if (player != i && ratings[i] > playerRating && ratings[i] < predRating) {
          predRating = ratings[i];
        }
      }
      return predRating - playerRating;
    }

  }

  private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("##0.00");

  public static Strategy get(Player player, MutableList<Pair<Player, Integer>> playerLength) {
    Strategy strat = new Strategy();

    strat.player = player;

    strat.playerLength = playerLength;
    for (int pos = 0; pos < playerLength.size(); pos++) {
      strat.players.put(playerLength.get(pos).key, pos);
    }

    int leadingEdge = 0;
    if (playerLength.size() >= 2) {
      playerLength.sortThis((o1, o2) -> o1.value - o2.value);
      strat.leader = playerLength.get(0).key;
      strat.gamelength = playerLength.get(0).value;
      leadingEdge = Math.abs(playerLength.get(0).value - playerLength.get(1).value);
    }

    if (strat.leader == player && leadingEdge >= 1) {
      strat.type = Type.Paranoid;
    } else if (strat.leader != player && leadingEdge >= 2) {
      strat.type = Type.Offensive;
    } else {
      strat.type = Type.MaxN;
    }

    return strat;
  }

  private Type type;
  private MutableObjectIntMap<Player> players = ObjectIntMaps.mutable.empty();
  private MutableList<Pair<Player, Integer>> playerLength;
  private Player player;
  private Player leader;
  private int gamelength;

  private Strategy() {
  }

  private Comparator<Evaluation> comp(int player) {
    return (o1, o2) -> {
      int pos1 = o1.getPosition(player);
      int pos2 = o2.getPosition(player);
      if (pos1 - pos2 != 0)
        return (pos1 - pos2) < 0 ? 1 : -1;

      if (pos1 == 1) { // && pos2 == 1
        float leadDiff = o1.getLead(player) - o2.getLead(player);
        return leadDiff == 0 ? 0 : (leadDiff < 0 ? 1 : -1);
      } else {
        float predDiff = o1.getBacklog(player) - o2.getBacklog(player);
        return predDiff == 0 ? 0 : (predDiff < 0 ? 1 : -1);
      }
    };
  }

  public Evaluation evaluate(Player pl, MutableCollection<Evaluation> evals) {
    if (evals.isEmpty())
      return null;
    if (pl == player && type == Type.Offensive)
      return evals.min(comp(players.get(leader)));
    else if (pl != player && type == Type.Paranoid)
      return evals.min(comp(players.get(player)));
    else
      return evals.max(comp(players.get(pl)));
  }

  public Evaluation merge(MutableList<Evaluation> values) {
    Evaluation avg = new Evaluation(players.size());
    for (Evaluation value : values) {
      for (int i = 0; i < avg.ratings.length; i++) {
        avg.ratings[i] += value.ratings[i];
      }
    }
    for (int i = 0; i < avg.ratings.length; i++) {
      avg.ratings[i] /= values.size();
    }
    return avg;
  }

  public Evaluation gameEnd() {
    return new Evaluation(players.size());
  }

  public Evaluation maxDepth() {
    Evaluation eval = new Evaluation(players.size());
    for (Pair<Player, Integer> length : playerLength) {
      int roundsAfterLeader = length.value - gamelength;
      eval.ratings[players.get(length.key)] = roundsAfterLeader == 0 ? 0 : -roundsAfterLeader;
    }
    return eval;
  }

  public Evaluation finish(Evaluation eval, Player player, int round) {
    int roundsAfterLeader = round - gamelength;
    eval.ratings[players.get(player)] = roundsAfterLeader == 0 ? 0 : -roundsAfterLeader;
    return eval;
  }

  public Evaluation block(Evaluation eval, Player player, int round, int zzz) {
    int blockInRoundsTillFinish = gamelength - round;
    eval.ratings[players.get(player)] = blockInRoundsTillFinish <= 0 ? blockInRoundsTillFinish : (zzz + 1) * 1.5f;
    return eval;
  }

  @Override
  public String toString() {
    return type.name();
  }

}
