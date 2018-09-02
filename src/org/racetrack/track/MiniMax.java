package org.racetrack.track;

import java.util.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.primitive.*;
import org.racetrack.karoapi.*;

public class MiniMax {

  public static MiniMax max(Collection<MiniMax> nodesValues, Player player) {
    MiniMax max = null;
    for (MiniMax value : nodesValues) {
      int playerPos = value.players.get(player);
      if (max == null || value.ratings[playerPos] > max.ratings[playerPos]) {
        max = value;
      } else if (value.ratings[playerPos] == max.ratings[playerPos]) {
        double playerOpponents = 0d;
        double maxOpponents = 0d;
        for (int i = 0; i < value.ratings.length; i++) {
          if (i != playerPos) {
            playerOpponents += value.ratings[i];
            maxOpponents += max.ratings[i];
          }
        }
        if (playerOpponents < maxOpponents) {
          max = value;
        }
      }
    }
    return max;
  }

  public static MiniMax avg(MutableList<MiniMax> values, ObjectIntMap<Player> players) {
    MiniMax avg = new MiniMax(players);
    for (MiniMax value : values) {
      for (int i = 0; i < avg.ratings.length; i++) {
        avg.ratings[i] += value.ratings[i];
      }
    }
    for (int i = 0; i < avg.ratings.length; i++) {
      avg.ratings[i] /= values.size();
    }
    return avg;
  }

  private ObjectIntMap<Player> players = null;
  private double[] ratings = null;

  public MiniMax(ObjectIntMap<Player> players) {
    this.players = players;
    ratings = new double[players.size()];
  }

  public void setValue(Player player, double value) {
    ratings[players.get(player)] = value;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (double value : ratings) {
      if (sb.length() != 0) {
        sb.append(", ");
      }
      sb.append(value);
    }
    return new StringBuilder().append("[").append(sb).append("]").toString();
  }

}
