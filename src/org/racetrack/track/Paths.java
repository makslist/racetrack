package org.racetrack.track;

import java.util.*;

import org.eclipse.collections.api.*;
import org.eclipse.collections.api.block.predicate.*;
import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.primitive.*;
import org.eclipse.collections.api.multimap.*;
import org.eclipse.collections.api.set.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.eclipse.collections.impl.map.mutable.primitive.*;
import org.racetrack.karoapi.*;

public class Paths {

  public static Paths empty() {
    return new Paths();
  }

  public static Paths getCopy(Paths path) {
    Paths copy = new Paths();
    copy.comment = path.comment;
    return copy;
  }

  public static Paths onlyFiltered(Paths path, MutableCollection<Move> filtered) {
    Paths copy = new Paths();
    copy.comment = path.comment;
    copy.moves = filtered;
    return copy;
  }

  /**
   * Returns the next lower level in path including crashs belonging to that level
   *
   * @param moves
   *          the reference moves from a single level
   */
  public static MutableCollection<Move> getCompleteLowerLevel(MutableCollection<Move> moves) {
    MutableCollection<Move> previousLevel = Sets.mutable.empty();
    for (Move move : moves) {
      if (move.isCrash()) {
        continue;
      }
      for (Move predecessor : move.getPreds()) {
        if (predecessor.isCrash()) {
          previousLevel.addAll(predecessor.getPreds());
        }
        previousLevel.add(predecessor);
      }
    }
    return previousLevel;
  }

  private MutableCollection<Move> moves = new FastList<>(0);
  private String comment = "";

  private MutableIntObjectMap<MutableList<Move>> roundMoves = new IntObjectHashMap<>(192);

  private Paths() {
  }

  public Paths(MutableCollection<Move> moves) {
    this.moves = moves;
  }

  public boolean add(Move move) {
    return moves.add(move);
  }

  public void merge(Paths paths) {
    if (comment.isEmpty()) {
      comment = paths.comment;
    }

    if (moves.isEmpty()) {
      moves.addAll(paths.getEndMoves());
    } else {
      int newMinimumLength = moves.withAll(paths.getEndMoves()).minBy(move -> move.getTotalLen()).getTotalLen();
      moves = moves.select(move -> move.getTotalLen() == newMinimumLength);

      MutableMultimap<Integer, Move> duplicates = moves.groupBy(move -> move.hashCode())
          .selectKeysMultiValues((key, values) -> ((RichIterable<Move>) values).size() >= 2);
      for (Integer key : duplicates.keySet()) {
        MutableCollection<Move> dups = duplicates.get(key);
        Move first = null;
        for (Move move : dups) {
          if (first == null) {
            first = move;
          } else {
            first.getPreds().addAll(move.getPreds());
            moves.removeIf(m -> m == move);
          }
        }
      }

      MutableCollection<Move> level = moves;
      while (!level.isEmpty()) {
        mergePredecessors(level);
        level = getCompleteLowerLevel(level);
      }
    }
  }

  public void mergePredecessors(MutableCollection<Move> curMoves) {
    MutableList<Move> preds = new FastList<>();
    for (Move test : curMoves) {
      test.getPreds().reject(m -> preds.anySatisfy(p -> p == m), preds);
    }
    MutableMultimap<Integer, Move> predGroups = preds.groupBy(pred -> pred.hashCode());

    for (Integer predHash : predGroups.keySet()) {
      MutableCollection<Move> dups = predGroups.get(predHash);
      Move first = null;
      for (Move dupPred : dups) {
        if (first == null) {
          first = dupPred;
        } else {
          final MutableCollection<Move> firstPredecessors = first.getPreds();
          dupPred.getPreds().reject(m -> firstPredecessors.anySatisfy(p -> p == m), firstPredecessors);
          final Move secondFirst = first;

          curMoves.forEach(m -> {
            final MutableCollection<Move> curPreds = m.getPreds();
            if (curPreds.removeIf(m1 -> m1 == dupPred)) {
              curPreds.with(secondFirst);
            }
          });
        }
      }
    }
  }

  public Paths filterPossibles(Predicate<Move> rule) {
    moves = moves.select(rule);
    return this;
  }

  public MutableCollection<Move> getEndMoves() {
    return new FastList<>(moves);
  }

  public boolean isEmpty() {
    return moves.isEmpty();
  }

  public int getMinLength() {
    return moves.isEmpty() ? 0 : moves.minBy(move -> move.getPathLen()).getPathLen();
  }

  public int getMinTotalLength() {
    return moves.isEmpty() ? Short.MAX_VALUE : moves.minBy(move -> move.getTotalLen()).getTotalLen();
  }

  public Paths getShortestTracks() {
    int minLength = getMinTotalLength();
    return Paths.onlyFiltered(this, moves.select(move -> move.getTotalLen() == minLength));
  }

  /**
   * Changes the internal structure of the path by cutting single connections between moves and their predecessors which
   * are not on the path to a later crash-move.
   */
  public void trimCrashPaths() {
    MutableSet<Move> crashSuccesors = Sets.mutable.empty();
    for (int i = getMinTotalLength(); i > 0; i--) {
      MutableList<Move> crashs = getCrashsOfRound(i);
      if (!crashs.isEmpty()) {
        crashSuccesors.addAll(crashs);
        for (Move crash : crashs) {
          MutableCollection<Move> crashPreds = crash.getPreds();
          if (crashPreds.anySatisfy(c -> c.equalsPos(crashSuccesors))) {
            crashPreds.removeIf(c -> !c.equalsPos(crashSuccesors));
          }
        }
      }
    }
  }

  public MutableList<Move> getCrashsOfRound(int round) {
    return getMovesOfRound(round + 1).flatCollect(m -> m.getCrashPredecessors());
  }

  public MutableList<Move> getMovesOfRound(int round) {
    if (roundMoves.containsKey(round))
      return roundMoves.get(round);

    MutableCollection<Move> beginingMoves = Sets.mutable.empty();
    MutableCollection<Move> curLevel = moves;
    while (!curLevel.isEmpty()) {
      beginingMoves.addAll(curLevel.select(move -> move.getTotalLen() == round - 1));
      MutableCollection<Move> previousLevel = curLevel.flatCollect(move -> move.getNonCrashPredecessors()).toSet();
      curLevel = previousLevel;
    }
    MutableList<Move> moveList = beginingMoves.toList();
    roundMoves.put(round, moveList);
    return moveList;
  }

  public MutableList<Move> getSuccessors(int level, Move predecessor) {
    if (predecessor == null)
      return new FastList<>(0);
    MutableList<Move> movesOfRound = getMovesOfRound(level);
    if (movesOfRound == null || movesOfRound.isEmpty())
      return new FastList<>(0);
    return movesOfRound.select(m -> m.getPreds() != null && m.getPreds().contains(predecessor));
  }

  public MutableCollection<Move> getPartialMoves() {
    MutableCollection<Move> partialMoves = Sets.mutable.empty();
    MutableCollection<Move> levelMoves = moves;
    while (!levelMoves.isEmpty()) {
      partialMoves.addAll(levelMoves);
      levelMoves = getCompleteLowerLevel(levelMoves);
    }
    return partialMoves;
  }

  public Map<Short, MutableCollection<Move>> getWidestPaths() {
    Map<Short, MutableCollection<Move>> map = new HashMap<>();
    for (Move move : getPartialMoves()) {
      MutableCollection<Move> depth = map.get(move.getTotalLen());
      if (depth == null) {
        depth = new FastList<>();
        depth.add(new Move(move.getX(), move.getY(), 0, 0));
        map.put(move.getTotalLen(), depth);
      } else if (depth.noneSatisfy(wide -> wide.equalsPos(move))) {
        depth.add(new Move(move.getX(), move.getY(), 0, 0));
      }
    }
    return map;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public boolean isInCurrentRound() {
    return getEndMoves().allSatisfy(move -> move.getPathLen() <= 1);
  }

  @Override
  public String toString() {
    return moves.toString();
  }

}
