package org.racetrack.rules;

import java.util.*;
import java.util.concurrent.locks.*;

import org.eclipse.collections.api.block.predicate.*;
import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.map.primitive.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.map.mutable.primitive.*;
import org.racetrack.karoapi.*;
import org.racetrack.track.*;

public class MapRule {

  private static final int MAX_DIST_FINISH_FOR_F1 = 6;

  private final ReadWriteLock offTrackLock = new SeqLock(false);
  private final ReadWriteLock onRoadLock = new SeqLock(false);
  private MutableIntBooleanMap offTrack = new IntBooleanHashMap(2 << 18);
  private MutableIntBooleanMap onRoad = new IntBooleanHashMap(2 << 18);

  protected KaroMap map;

  protected Predicate<Move> mapRule = move -> move.isMoving()
      && !isOffTrack(move.getX(), move.getY(), move.getXv(), move.getYv());

  private Boolean isMapCircuitCached;

  protected AngleRange angleRange;
  private int startFinishDist = 0;

  public MapRule(KaroMap map) {
    this.map = map;

    angleRange = getAngleForFinishVector();
  }

  private boolean isOffTrack(int x, int y, int xv, int yv) {
    int key = Move.getMoveHash(x, y, xv, yv);
    SeqLock.SeqReadLock lock = (SeqLock.SeqReadLock) offTrackLock.readLock();
    try {
      while (true) {
        long counter = lock.tryReadLock();
        boolean isOffTrack = offTrack.getOrThrow(key);
        if (lock.retryReadLock(counter))
          return isOffTrack;
      }
    } catch (IllegalStateException ise) {
      boolean isOffTrack = !map.contains(x, y) || isDrivenAcross(x, y, xv, yv, MapTile.OFF_TRACK, false);
      offTrackLock.writeLock().lock();
      offTrack.put(key, isOffTrack);
      offTrackLock.writeLock().unlock();
      return isOffTrack;
    }
  };

  private boolean isOnRoad(Move move) {
    int key = move.hashCode();
    SeqLock.SeqReadLock lock = (SeqLock.SeqReadLock) onRoadLock.readLock();
    try {
      while (true) {
        long counter = lock.tryReadLock();
        boolean isOnRoad = onRoad.getOrThrow(key);
        if (lock.retryReadLock(counter))
          return isOnRoad;
      }
    } catch (IllegalStateException ise) {
      boolean isOnRoad = !isDrivenAcross(move.getX(), move.getY(), move.getXv(), move.getYv(), MapTile.CP_AND_FINISH,
          false);
      onRoadLock.writeLock().lock();
      onRoad.put(key, isOnRoad);
      onRoadLock.writeLock().unlock();
      return isOnRoad;
    }
  }

  public Predicate<Move> filterMap() {
    return mapRule;
  }

  public MutableCollection<Move> filterNextMvDist(Move move) {
    return move.getNext().select(mapRule);
  };

  public MutableCollection<Move> filterNextMv(Move move) {
    return move.getNext().select(mapRule);
  };

  public boolean isValid(Move move) {
    return mapRule.accept(move);
  }

  public boolean hasXdCp(Move move, MapTile cp) {
    return !isOnRoad(move) && isDrivenAcross(move.getX(), move.getY(), move.getXv(), move.getYv(), cp.asList(), true);
  }

  public boolean hasXdCp(LogMove move, MapTile cp) {
    if (move == null)
      return false;
    if (!isOnRoad(move) && isDrivenAcross(move.getX(), move.getY(), move.getXv(), move.getYv(), cp.asList(), true))
      return true;
    return hasXdCp((LogMove) move.getPred(), cp);
  }

  public boolean hasXdCp(Paths paths, MapTile tile) {
    MutableCollection<Move> allTracks = paths.getEndMoves();
    while (!allTracks.isEmpty()) {
      if (allTracks.anySatisfy(move -> hasXdCp(move, tile)))
        return true;
      allTracks = allTracks.flatCollect(move -> move.getPreds()).toSet();
    }
    return false;
  }

  protected boolean hasXdFinishline(Move move) {
    if (move == null)
      return false;
    return isDrivenAcross(move.getX(), move.getY(), move.getXv(), move.getYv(), MapTile.FINISH.asList(), false);
  }

  protected boolean hasXdFinishline(LogMove lastMove) {
    Move move = lastMove;
    while (move != null) {
      if (hasXdFinishline(move))
        return true;
      move = move.getPred();
    }
    return false;
  }

  /**
   * Bresenham's line algorithm with modification to check all crossed fields. See Karopapier sourcecode at GitHub.
   *
   * @param withCpRule
   *          The Karopapier website only recognized a checkpoint as passed when driven over as last MapTile by the
   *          vector. When checking for crossing e.g. grass this behavior is undesired and the cpRule should be disabled
   */
  private boolean isDrivenAcross(int x, int y, int xv, int yv, Collection<MapTile> tiles, boolean withCpRule) {
    int x0 = x - xv;
    int y0 = y - yv;

    int incx = (int) Math.signum(x - x0);
    int incy = (int) Math.signum(y - y0);

    int dx = Math.abs(xv);
    int dy = Math.abs(yv);

    int pdx, pdy, qdx, qdy, ddx, ddy;
    int el, es;
    if (dx > dy) { // x is fast direction
      pdx = incx;
      pdy = 0;
      qdx = 0;
      qdy = incy;
      ddx = incx;
      ddy = incy;
      es = dy;
      el = dx;
    } else { // y is fast direction
      pdx = 0;
      pdy = incy;
      qdx = incx;
      qdy = 0;
      ddx = incx;
      ddy = incy;
      es = dx;
      el = dy;
    }

    float err = (el - es) / 2f;
    /*
     * the signum of err indicates on which side of the vector the center of the last considered box is located. if
     * there is a deviation in the * "slower" direction, err is positive, and we take a step in the "faster"
     * direction.if there is a deviation in the "fast" direction, err is negative, and we take a step in the "slower"
     * direction.
     */
    while (true) {
      MapTile mapTile = map.getTileOf(x0, y0);
      if (tiles.contains(mapTile))
        return true;
      else if (withCpRule && mapTile.isCpOrFinish())
        return false;

      if (x0 == x && y0 == y)
        return false;

      if (err < 0) { // move in slow direction
        err += el;
        x0 += qdx;
        y0 += qdy;
      } else if (err > 0) { // move in fast direction
        err -= es;
        x0 += pdx;
        y0 += pdy;
      } else { // diagonal
        err += el;
        err -= es;
        x0 += ddx;
        y0 += ddy;
      }
    }
  }

  protected AngleRange getAngleForFinishVector() {
    AngleRange angleRange = null;
    Set<Move> visitedMoves = Sets.mutable.empty();
    Queue<Move> moveQueue = new LinkedList<>(map.getTilesAsMoves(MapTile.START));

    while (!moveQueue.isEmpty()) {
      Move move = moveQueue.poll();
      if (move.getTotalLen() > startFinishDist + MAX_DIST_FINISH_FOR_F1)
        return angleRange;

      if (!visitedMoves.contains(move)) {
        visitedMoves.add(move);
        if (hasXdFinishline(move)) {
          if (angleRange == null) {
            angleRange = new AngleRange(move);
            startFinishDist = move.getPathLen();
          }
          angleRange.add(move);
        } else {
          moveQueue.addAll(move.getNext().select(mapRule));
        }
      }
    }
    return angleRange;
  }

  public boolean isMapCircuit() {
    if (isMapCircuitCached != null)
      return isMapCircuitCached;

    if (map.isSettingSet() && map.isCircuit() != null) {
      isMapCircuitCached = map.isCircuit();
    } else if (map.isCpClustered(MapTile.FINISH)) {
      if (angleRange != null && angleRange.isRangeSmallerThanPi() && startFinishDist > 0
          && startFinishDist <= MAX_DIST_FINISH_FOR_F1) {
        isMapCircuitCached = Boolean.TRUE;
      } else {
        isMapCircuitCached = Boolean.FALSE;
      }
    } else {
      isMapCircuitCached = Boolean.FALSE;
    }
    return isMapCircuitCached;
  }

}
