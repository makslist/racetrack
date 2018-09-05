package org.racetrack.track;

import java.util.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.primitive.*;
import org.racetrack.karoapi.*;

public class Evaluation {

  public static Evaluation max(Collection<Evaluation> nodesValues, Player player) {
    Evaluation max = null;
    for (Evaluation value : nodesValues) {
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

  public static Evaluation min(Collection<Evaluation> nodesValues, Player player) {
    Evaluation min = null;
    for (Evaluation value : nodesValues) {
      int playerPos = value.players.get(player);
      if (min == null || value.ratings[playerPos] < min.ratings[playerPos]) {
        min = value;
      } else if (value.ratings[playerPos] == min.ratings[playerPos]) {
        double playerOpponents = 0d;
        double maxOpponents = 0d;
        for (int i = 0; i < value.ratings.length; i++) {
          if (i != playerPos) {
            playerOpponents += value.ratings[i];
            maxOpponents += min.ratings[i];
          }
        }
        if (playerOpponents > maxOpponents) {
          min = value;
        }
      }
    }
    return min;
  }

  public static Evaluation avg(MutableList<Evaluation> values, ObjectIntMap<Player> players) {
    Evaluation avg = new Evaluation(players);
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

  private ObjectIntMap<Player> players = null;
  private float[] ratings = null;

  public Evaluation(ObjectIntMap<Player> players) {
    this.players = players;
    ratings = new float[players.size()];
  }

  public void setValue(Player player, float value) {
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
