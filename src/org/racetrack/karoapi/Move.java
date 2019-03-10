package org.racetrack.karoapi;

import java.util.*;

import org.eclipse.collections.api.block.predicate.*;
import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.set.primitive.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.json.*;

public class Move {

  protected static final String X = "x";
  protected static final String Y = "y";
  protected static final String XV = "xv";
  protected static final String YV = "yv";
  protected static final String CRASH = "crash";

  public static Predicate<Move> equalsPosition(MutableCollection<Move> moves) {
    return move -> moves.anySatisfy(other -> other.equalsPos(move));
  }

  public static int getMoveHash(int x, int y, int xv, int yv) {
    return (yv & 63) << 26 | (xv & 63) << 20 | (y & 1023) << 10 | (x & 1023);
  }

  public static MutableList<Move> getPossibleMoves(JSONArray json, Move previous) {
    MutableList<Move> moves = new FastList<>();
    json.forEach(jsonObj -> moves.add(new LogMove((JSONObject) jsonObj, previous)));
    moves.forEach(move -> move.pathLen = 1);
    return moves;
  }

  public static Move crash(int x, int y, Move predecessor) {
    Move move = new Move(x, y, 0, 0);
    move.crash = true;
    move.setPredecessor(predecessor);
    return move;
  }

  protected short x;
  protected short y;
  protected short xv;
  protected short yv;
  protected boolean crash;
  protected MutableList<Move> preds = new FastList<>(0);
  protected short totalLen = 0;
  protected short pathLen = 0;

  public Move(int x, int y, int xv, int yv) {
    this.x = (short) x;
    this.y = (short) y;
    this.xv = (short) xv;
    this.yv = (short) yv;
  }

  protected Move(int x, int y, int xv, int yv, Move predecessor) {
    this(x, y, xv, yv);

    setPredecessor(predecessor);
  }

  /**
   * Copy constructor
   */
  protected Move(Move move) {
    x = move.x;
    y = move.y;
    xv = move.xv;
    yv = move.yv;
    totalLen = move.totalLen;
    pathLen = move.pathLen;
    if (move.preds != null) {
      preds.addAll(move.preds);
    }
  }

  protected void setPredecessor(Move predecessor) {
    if (predecessor != null && !equals(predecessor)) {
      preds.add(predecessor);
      totalLen = predecessor.totalLen;
      pathLen = predecessor.pathLen;
      if (!isCrash()) {
        totalLen++;
        pathLen++;
      }
    }
  }

  public short getX() {
    return x;
  }

  public short getY() {
    return y;
  }

  public short getXv() {
    return xv;
  }

  public short getYv() {
    return yv;
  }

  /**
   * Returns the total path length since the start
   */
  public short getTotalLen() {
    return totalLen;
  }

  /**
   * Returns the pathlength for this turn
   */
  public short getPathLen() {
    return pathLen;
  }

  /**
   * Returns the predecessors
   */
  public MutableCollection<Move> getPreds() {
    return preds;
  }

  public Move getPred() {
    return preds.isEmpty() ? null : preds.getFirst();
  }

  public MutableCollection<Move> getNonCrashPredecessors() {
    if (totalLen <= 0)
      return Sets.mutable.empty();

    MutableCollection<Move> moves = new FastList<>();
    for (Move predecessor : preds) {
      if (predecessor.isCrash()) {
        moves.addAll(predecessor.getNonCrashPredecessors());
      } else {
        moves.add(predecessor);
      }
    }
    return moves;
  }

  public Collection<Move> getCrashPredecessors() {
    return preds.select(move -> move.isCrash());
  }

  public void merge(Move other) {
    if (this != other && equals(other) && totalLen == other.totalLen) {
      for (Move otherPred : other.preds) {
        if (!preds.contains(otherPred)) {
          preds.add(otherPred);
        }
      }
    }
  }

  public boolean isCrash() {
    return crash;
  }

  public boolean isMoving() {
    return totalLen == 0 || xv != 0 || yv != 0;
  }

  public MutableList<Move> getNext() {
    MutableList<Move> next = new FastList<>(9);
    for (int i = -1; i <= 1; i++) {
      for (int j = -1; j <= 1; j++) {
        if (xv + i != 0 || yv + j != 0) {
          next.add(new Move(x + xv + i, y + yv + j, xv + i, yv + j, this));
        }
      }
    }
    return next;
  }

  public int getDist(Move move) {
    return Integer.max(Math.abs(x - move.x), Math.abs(y - move.y));
  }

  public double getSpeed() {
    return Math.sqrt((xv * xv) + (yv * yv));
  }

  public int getTaxiSpeed() {
    return Integer.max(Math.abs(xv), Math.abs(yv));
  }

  /**
   * Returns the angle of the move as an integer between -180 and 180
   */
  public double getAngle() {
    double atan2 = Math.atan2(yv, xv);
    return Math.toDegrees(atan2);
  }

  public double getAngle(Move move) {
    double skalar = xv * move.xv + yv * move.yv;
    double lengths = Math.abs(Math.hypot(xv, yv)) * Math.abs(Math.hypot(move.xv, move.yv));
    double acos = Math.acos(skalar / lengths);
    return Double.isNaN(acos) ? 0 : Math.abs(Math.toDegrees(acos));
  }

  /**
   * Only works, when only one predecessor exists (special function for RE races)
   */
  public boolean isRepeat() {
    Move pred = getPred();
    return pred != null && xv == pred.xv && yv == pred.yv;
  }

  public MutableCollection<Move> getMovesAfterCrash(int zzz) {
    if (isCrash())
      return new FastList<Move>(0);
    return getZzzPredecessors(zzz, zzz).collect(move -> Move.crash(move.x, move.y, this));
  }

  protected MutableCollection<Move> getZzzPredecessors(final int zzz, final int depth) {
    MutableCollection<Move> result = Sets.mutable.empty();

    Collection<Move> moves = Collections.singleton(this);
    int aussetzen = depth;

    while (aussetzen >= 0) {
      MutableCollection<Move> nextLevel = Sets.mutable.empty();
      for (Move move : moves) {
        if (move.isCrash() && move != this && aussetzen + zzz < 3 * zzz) {
          result.addAll(move.getZzzPredecessors(zzz, aussetzen + zzz));
        } else if (aussetzen == 0) {
          result.add(move);
        } else {
          // inner move or start move
          nextLevel.addAll(move.preds.isEmpty() ? Collections.singleton(move) : move.preds);
        }
      }
      moves = nextLevel;
      aussetzen--;
    }

    return result;
  }

  /**
   * Changes the internal structure of the path by cutting single connections between moves and their predecessors which
   * are not on the path to a later crash-move.
   */
  public boolean trimCrashPath(Deque<MutableCollection<Move>> crashsOnPath, int zzz, int depth,
      MutableLongSet knownCrashPath, boolean searchMode, List<Move> parallelMoves) {
    if (depth == 0) {
      if (!crashsOnPath.isEmpty()) {
        for (Move crash : crashsOnPath.pop()) {
          if (equalsPos(crash) && crashsOnPath.isEmpty())
            return true;
        }
      }
      return false;
    } else if (equalsPos(crashsOnPath.peek())) {
      crashsOnPath.pop();
    }

    boolean isOnCrashPath = false;
    Iterator<Move> it = preds.iterator();
    while (it.hasNext()) {
      Move predecessor = it.next();
      int aussetzen = depth - 1;
      Deque<MutableCollection<Move>> seenCrashs = new LinkedList<MutableCollection<Move>>(crashsOnPath);
      if (isCrash()) {
        seenCrashs.push(ListAdapter.adapt(parallelMoves).select(move -> move.isCrash()).distinct());
        aussetzen += zzz;
      }
      long key = ((long) predecessor.hashCode()) << 32 | (hashCode() & 0xFFFFFFFFL);
      if (!searchMode && preds.size() == 1) {
        predecessor.trimCrashPath(seenCrashs, zzz, aussetzen, knownCrashPath, false, preds);
      } else if (searchMode && crashsOnPath.size() == 1 && knownCrashPath.contains(key)) {
        isOnCrashPath = true;
      } else if (predecessor.trimCrashPath(seenCrashs, zzz, aussetzen, knownCrashPath, true, preds)) {
        if (crashsOnPath.size() == 1) {
          knownCrashPath.add(key);
        }
        isOnCrashPath = true;
        if (searchMode)
          return true;
        else {
          predecessor.trimCrashPath(seenCrashs, zzz, aussetzen, knownCrashPath, false, preds);
        }
      } else if (!searchMode && preds.size() > 1) {
        it.remove();
      }
    }

    return isOnCrashPath;
  }

  @Override
  public int hashCode() {
    return getMoveHash(x, y, xv, yv);
  }

  @Override
  public boolean equals(Object obj) {
    Move other = (Move) obj;
    return x == other.x && y == other.y && xv == other.xv && yv == other.yv;
  }

  public int getPosHash() {
    return (totalLen & 63) << 20 | (y & 1023) << 10 | (x & 1023);
  }

  public boolean equalsPos(Move other) {
    return other != null && x == other.x && y == other.y;
  }

  public boolean isNearPos(Move move, int dist) {
    return getDist(move) <= dist;
  }

  public boolean isNearPos(MutableCollection<Move> moves, int dist) {
    for (Move other : moves)
      if (getDist(other) < dist)
        return true;
    return false;
  }

  public int getMinDist(MutableCollection<Move> moves) {
    int minDist = Integer.MAX_VALUE;
    for (Move other : moves) {
      int dist = getDist(other);
      if (dist < minDist) {
        minDist = dist;
      }
    }
    return minDist;
  }

  public boolean equalsStart(Move other) {
    return other != null && (x - xv) == (other.x - other.xv) && (y - yv) == (other.y - other.yv);
  }

  public boolean equalsPos(MutableCollection<Move> moves) {
    return moves != null ? moves.anySatisfy(move -> move.equalsPos(this)) : false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(x).append("/").append(y);
    if (xv != 0 || yv != 0) {
      sb.append("/").append(xv).append("/").append(yv);
    }
    return sb.toString();
  }

}
