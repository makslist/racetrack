package org.racetrack.track;

import java.util.*;

import org.eclipse.collections.api.*;
import org.eclipse.collections.api.block.predicate.*;
import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.primitive.*;
import org.eclipse.collections.api.multimap.*;
import org.eclipse.collections.api.set.*;
import org.eclipse.collections.api.set.primitive.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.factory.primitive.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.karoapi.*;

public class Paths {

  private static final int MAX_LEVEL_WEIGHT = 100;

  public static Paths getQuitPath(Game game) {
    Paths path = new Paths(game);
    path.quitGame = true;
    return path;
  }

  public static Paths getCopy(Paths path) {
    Paths copy = new Paths(path.game);
    copy.crashAhead = path.crashAhead;
    copy.quitGame = path.quitGame;
    copy.comment = path.comment;
    return copy;
  }

  public static Paths onlyFiltered(Paths path, MutableCollection<Move> filtered) {
    Paths copy = new Paths(path.game);
    copy.crashAhead = path.crashAhead;
    copy.quitGame = path.quitGame;
    copy.comment = path.comment;
    copy.moves = filtered;
    return copy;
  }

  private static MutableCollection<Move> getNonCrashPredecessors(MutableCollection<Move> moves) {
    return moves.flatCollect(move -> move.getNonCrashPredecessors()).toSet();
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

  public Comparator<Move> bestMoveFirst = (m1, m2) -> {
    int block = 20 * MAX_LEVEL_WEIGHT * (getOtherLenDiff(m2) - getOtherLenDiff(m1));
    int weight = (int) (Math.round(10 * getWeight(m2)) - Math.round(10 * getWeight(m1)));
    return block + weight;
  };

  private Game game;
  private MutableCollection<Move> moves = new FastList<>(0);
  private String comment = "";
  private boolean crashAhead = false;
  private boolean quitGame = false;

  private MutableIntDoubleMap weight = IntDoubleMaps.mutable.empty();
  private MutableIntIntMap followers = IntIntMaps.mutable.empty();
  private MutableIntIntMap mutualOverlap = IntIntMaps.mutable.empty();

  private MutableIntFloatMap friendsTotalLen = IntFloatMaps.mutable.empty();
  private MutableIntFloatMap opponentsTotalLen = IntFloatMaps.mutable.empty();

  private boolean weightsCalculated = false;

  public Paths(Game game) {
    this.game = game;
  }

  public Paths(Game game, boolean crashAhead) {
    this.game = game;
    this.crashAhead = crashAhead;
  }

  public Paths(Game game, MutableCollection<Move> moves) {
    this.game = game;
    this.moves = moves;
    if (moves.isEmpty()) {
      crashAhead = true;
    }
  }

  public Game getGame() {
    return game;
  }

  public int getGameId() {
    return game.getId();
  }

  public int getMapId() {
    return game.getMapId();
  }

  public void setComment(String comment) {
    this.comment = comment;
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

  public Move getBestMove() {
    calcMoveWeights();

    MutableCollection<Move> beginingMoves = getRelativeLevelMoves(1);
    return beginingMoves.toList().sortThis(bestMoveFirst).getFirst();
  }

  private void calcMoveWeights() {
    if (weightsCalculated)
      return;

    double weightPow = game.getMap().getSetting().getPathWeightPow();
    if (Double.isNaN(weightPow)) {
      weightPow = 1.2d;
    }
    int succCountMod = game.getMap().getSetting().getPathSuccCountMod();
    if (succCountMod == -1) {
      succCountMod = 2;
    }

    MutableCollection<Move> level = moves;
    while (!level.isEmpty()) {
      MutableCollection<Move> previousLevel = Sets.mutable.empty();

      // determine single move endpoints
      double maxAngle = 0;
      for (Move move : level) {
        mutualOverlap.addToValue(move.getPosHash(), 1);
        for (Move pred : move.getPreds()) {
          followers.addToValue(pred.hashCode(), 1);
          if (move.getAngle(pred) > maxAngle) {
            maxAngle = move.getAngle(pred);
          }
        }
      }

      for (Move move : level) {
        double overlap = 1 - ((double) (mutualOverlap.get(move.getPosHash()) - 1) / level.size());
        weight.updateValue(move.hashCode(), 1, x -> x * overlap);
      }

      double sumLevelWeight = 0d;
      for (Move move : level) {
        sumLevelWeight += weight.getIfAbsent(move.hashCode(), 1);
      }

      double normalizeFactor = MAX_LEVEL_WEIGHT / sumLevelWeight;
      for (Move move : level) {
        double succweight = weight.updateValue(move.hashCode(), 0, x -> x * normalizeFactor);
        for (Move pred : move.getPreds()) {
          weight.addToValue(pred.hashCode(), Math.pow(succweight, weightPow) + succCountMod);
          if (move.getPreds().size() > 1) {
            weight.addToValue(pred.hashCode(), maxAngle - move.getAngle(pred));
          }
        }
        previousLevel.addAll(move.getNonCrashPredecessors());
      }
      level = previousLevel;
    }
    weightsCalculated = true;
  }

  public void setFriendsTotalLen(Move move, float len) {
    friendsTotalLen.put(move.hashCode(), len);
  }

  public void setOpponentsTotalLen(Move move, float len) {
    opponentsTotalLen.put(move.hashCode(), len);
  }

  public void trimCrashPaths(int zzz) {
    if (zzz >= 1) {
      MutableCollection<Move> levelCrashs = new FastList<>(0);
      MutableCollection<Move> levelMoves = moves;

      while (!levelMoves.isEmpty()) {
        if (!levelCrashs.isEmpty()) {
          Map<Move, MutableSet<Move>> crashPreds = getCommonCrashPred(levelCrashs);
          MutableLongSet validPredecessors = LongSets.mutable.empty();
          for (Move pred : crashPreds.keySet()) {
            Deque<MutableCollection<Move>> crashsOnPath = new LinkedList<MutableCollection<Move>>();
            crashsOnPath.push(crashPreds.get(pred));
            pred.trimCrashPath(crashsOnPath, zzz, zzz, validPredecessors, false, Collections.emptyList());
          }
        }

        levelCrashs = levelMoves.flatCollect(move -> move.getCrashPredecessors()).toSet();
        levelMoves = getNonCrashPredecessors(levelMoves);
      }
    }
  }

  private Map<Move, MutableSet<Move>> getCommonCrashPred(Collection<Move> crashs) {
    Map<Move, MutableSet<Move>> crashPreds = Maps.mutable.empty();

    for (Move crash : crashs) {
      for (Move pred : crash.getPreds()) {
        if (crashPreds.containsKey(pred)) {
          crashPreds.get(pred).add(crash);
        } else {
          crashPreds.put(pred, Sets.mutable.of(crash));
        }
      }
    }
    return crashPreds;
  }

  public MutableList<Move> getLevelMoves(int level) {
    calcMoveWeights();

    MutableCollection<Move> beginingMoves = Sets.mutable.empty();
    MutableCollection<Move> curLevel = moves;
    while (!curLevel.isEmpty()) {
      beginingMoves.addAll(curLevel.select(move -> move.getTotalLen() == level));
      MutableCollection<Move> previousLevel = curLevel.flatCollect(move -> move.getNonCrashPredecessors()).toSet();
      curLevel = previousLevel;
    }
    return beginingMoves.toList();
  }

  public MutableList<Move> getRelativeLevelMoves(int level) {
    calcMoveWeights();

    MutableCollection<Move> beginingMoves = Sets.mutable.empty();
    MutableCollection<Move> curLevel = moves;
    while (!curLevel.isEmpty()) {
      beginingMoves.addAll(curLevel.select(move -> move.getPathLen() == level));
      MutableCollection<Move> previousLevel = curLevel.flatCollect(move -> move.getNonCrashPredecessors()).toSet();
      curLevel = previousLevel;
    }
    return beginingMoves.toList();
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

  public boolean isCrashAhead() {
    return crashAhead;
  }

  public boolean hasComment() {
    return !comment.isEmpty();
  }

  public String getComment() {
    return comment;
  }

  public boolean isQuitting() {
    return quitGame;
  }

  public boolean isInCurrentRound() {
    return getEndMoves().allSatisfy(move -> move.getPathLen() <= 1);
  }

  private int getOtherLenDiff(Move move) {
    return Math.round(opponentsTotalLen.getIfAbsent(move.hashCode(), 0))
        - Math.round(friendsTotalLen.getIfAbsent(move.hashCode(), 0));
  }

  public double getWeight(Move move) {
    return weight.getIfAbsent(move.hashCode(), 10000);
  }

  public int getFollowers(Move move) {
    return followers.get(move.hashCode());
  }

  @Override
  public String toString() {
    return moves.toString();
  }

}
