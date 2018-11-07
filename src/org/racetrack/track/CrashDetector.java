package org.racetrack.track;

import java.util.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public class CrashDetector {

  private static final int CRASH_DETECTOR_DEPTH = 12;

  public static boolean hasCrashHappend(Move move, int maxDepth) {
    int depth = move.getTotalLen();
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
  private int depth = CRASH_DETECTOR_DEPTH;
  private Collection<Move> moves;
  private Boolean crashAhead;

  public CrashDetector(MapRule rule, Collection<Move> moves) {
    this.rule = rule;
    this.moves = moves;
  }

  public boolean isCrashAhead(Move move) {
    return isCrashAhead() && move.getPathLen() < depth && !hasCrashHappend(move);
  }

  public boolean isCrashAhead() {
    if (crashAhead == null) {
      for (Move move : moves) {
        if (isPathSave(move, depth)) {
          crashAhead = false;
          return false;
        }
      }
      crashAhead = true;
    }
    return crashAhead;
  }

  private boolean isPathSave(Move move, int d) {
    if (d == 0)
      return true;
    else {
      for (Move next : move.getNext().select(rule.filterMap())) {
        if (isPathSave(next, d - 1))
          return true;
      }
      return false;
    }
  }

  private boolean hasCrashHappend(Move move) {
    int depth = this.depth;
    MutableCollection<Move> currentMoves = Paths.getCompleteLowerLevel(FastList.newListWith(move));
    while (!currentMoves.isEmpty() || depth > 0) {
      for (Move previous : currentMoves) {
        if (previous.getPathLen() <= 0)
          return false;
        else if (previous.isCrash())
          return true;
      }
      depth--;
      currentMoves = Paths.getCompleteLowerLevel(currentMoves);
    }
    return false;
  }

}
