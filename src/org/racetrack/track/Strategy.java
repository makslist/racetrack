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

  }

  private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("##0.00");

  public static Strategy get(Player player, MutableList<Pair<Player, Integer>> playerLength) {
    Strategy strat = new Strategy();

    strat.player = player;

    for (int pos = 0; pos < playerLength.size(); pos++) {
      strat.players.put(playerLength.get(pos).key, pos);
    }

    int leadingEdge = 0;
    if (playerLength.size() >= 2) {
      playerLength.sortThis((o1, o2) -> o1.value - o2.value);
      strat.leader = playerLength.get(0).key;
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
  private Player player;
  private Player leader;

  private Strategy() {
  }

  private Comparator<Evaluation> comp(int player) {
    return (o1, o2) -> {
      float maxOp1 = Float.NEGATIVE_INFINITY, maxOp2 = Float.NEGATIVE_INFINITY;
      for (int i = 0; i < players.size(); i++) {
        if (player != i) {
          maxOp1 = Math.max(maxOp1, o1.ratings[i]);
          maxOp2 = Math.max(maxOp2, o2.ratings[i]);
        }
      }

      float diff1 = o1.ratings[player] - maxOp1;
      float diff2 = o2.ratings[player] - maxOp2;
      return diff1 == diff2 ? 0 : (diff1 > diff2 ? 1 : -1);
    };
  }

  public Evaluation evaluate(Player pl, MutableCollection<Evaluation> evals) {
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

  public Evaluation initEval() {
    return new Evaluation(players.size());
  }

  public Evaluation setValue(Evaluation eval, Player player, float value) {
    eval.ratings[players.get(player)] = value;
    return eval;
  }

  @Override
  public String toString() {
    return type.name();
  }

}
