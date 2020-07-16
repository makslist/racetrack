package org.racetrack.track;

import java.util.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public class CrashDetector {

  private static final int CRASH_DETECTOR_DEPTH = 12;

  public static boolean hasCrashHappend(Move move, int maxDepth) {
    int depth = Integer.min(move.getTotalLen(), maxDepth);
    MutableCollection<Move> prevMoves = Paths.getCompleteLowerLevel(FastList.newListWith(move));
    while (!prevMoves.isEmpty() || depth > 0) {
      if (prevMoves.anySatisfy(previous -> previous.isCrash()))
        return true;

      depth--;
      prevMoves = Paths.getCompleteLowerLevel(prevMoves);
    }
    return false;
  }

  private MapRule rule;
  private int maxDepth = CRASH_DETECTOR_DEPTH;
  private Collection<Move> moves;
  private Boolean crashAhead;

  public CrashDetector(MapRule rule, Collection<Move> moves) {
    this.rule = rule;
    this.moves = moves;
  }

  public boolean isCrashAhead(Move move) {
    return isCrashAhead() && move.getPathLen() < maxDepth && !hasCrashHappend(move, maxDepth);
  }

  public boolean isCrashAhead() {
    if (crashAhead == null) {
      for (Move move : moves) {
        if (isPathSafe(move, maxDepth)) {
          crashAhead = false;
          return false;
        }
      }
      crashAhead = true;
    }
    return crashAhead.booleanValue();
  }

  private boolean isPathSafe(Move move, int d) {
    if (d == 0)
      return true;
    else {
      for (Move next : move.getNext().select(rule.filterMap())) {
        if (isPathSafe(next, d - 1))
          return true;
      }
      return false;
    }
  }

}
