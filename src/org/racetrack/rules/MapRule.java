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

  protected Predicate<Move> mapRule = move -> (move.getTotalLen() == 0
      || (move.isMoving() && !isOffTrack(move.getX(), move.getY(), move.getXv(), move.getYv())));

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
    return isDrivenAcross(move.getX(), move.getY(), move.getXv(), move.getYv(), MapTile.FINISH.asList(), false);
  }

  protected boolean hasXdFinishline(LogMove lastMove) {
    Move move = lastMove;
    while (move != null && move != move.getPred()) {
      if (hasXdFinishline(move))
        return true;
      move = move.getPred();
    }
    return false;
  }

  /**
   * @param withCpRule
   *          The Karopapier website only recognized a checkpoint as passed when driven over as last MapTile by the
   *          vector. When checking for crossing e.g. grass this behavior is undesired and the cpRule should be disabled
   */
  private boolean isDrivenAcross(int x, int y, int xv, int yv, Collection<MapTile> tiles, boolean withCpRule) {
    if (yv == 0)
      return isHorizontalAcross(x, y, xv, tiles, withCpRule);
    else if (xv == 0)
      return isVerticalAcross(x, y, yv, tiles, withCpRule);
    else if (xv == yv || xv == -yv)
      return isDiagonalAcross(x, y, xv, yv, tiles, withCpRule);

    else if (Math.abs(xv) > Math.abs(yv))
      return isDominantHorizontalAcross(x, y, xv, yv, tiles, withCpRule);
    else if (Math.abs(xv) < Math.abs(yv))
      return isDominantVerticalAcross(x, y, xv, yv, tiles, withCpRule);

    return false;
  }

  private boolean isHorizontalAcross(int x, int y, int xv, Collection<MapTile> tiles, boolean cpMode) {
    for (int i = 0; i < Math.abs(xv); i++) {
      int x2 = x - (int) Math.signum(xv) * i;
      MapTile mapTile = map.getTileOf(x2, y);
      if (tiles.contains(mapTile))
        return true;
      else if (cpMode && mapTile.isCpOrFinish())
        return false;
    }
    return false;
  }

  private boolean isVerticalAcross(int x, int y, int yv, Collection<MapTile> tiles, boolean cpMode) {
    for (int i = 0; i < Math.abs(yv); i++) {
      int y2 = y - (int) Math.signum(yv) * i;
      MapTile mapTile = map.getTileOf(x, y2);
      if (tiles.contains(mapTile))
        return true;
      else if (cpMode && mapTile.isCpOrFinish())
        return false;
    }
    return false;
  }

  private boolean isDiagonalAcross(int x, int y, int xv, int yv, Collection<MapTile> tiles, boolean cpMode) {
    for (int i = 0; i < Math.abs(xv); i++) {
      int diagX = x - (int) Math.signum(xv) * i;
      int diagY = y - (int) Math.signum(yv) * i;
      MapTile mapTile = map.getTileOf(diagX, diagY);
      if (tiles.contains(mapTile))
        return true;
      else if (cpMode && mapTile.isCpOrFinish())
        return false;
    }
    return false;
  }

  private boolean isDominantHorizontalAcross(int x, int y, int xv, int yv, Collection<MapTile> tiles, boolean cpMode) {
    for (int i = 0; i < Math.abs(xv); i++) {
      double sectX = x - (Math.signum(xv) * (i + 0.5));
      double sectY = getIntersectionWithMapGrid(x, y, xv, yv, sectX);

      int xL = (int) sectX, xU = xL + 1;
      int yU = (int) Math.round(sectY), yL = yU;
      if ((sectY - (int) sectY) == 0.5) {
        if (Math.signum(xv) * Math.signum(yv) < 0) {
          yU--;
        } else {
          yL--;
        }
      }
      MapTile lowerTile = map.getTileOf(xL, yL);
      MapTile upperTile = map.getTileOf(xU, yU);

      if (Math.signum(xv) < 0) {
        if (tiles.contains(lowerTile))
          return true;
        else if (cpMode && lowerTile.isCpOrFinish())
          return false;
        if (tiles.contains(upperTile))
          return true;
        else if (cpMode && upperTile.isCpOrFinish())
          return false;
      } else {
        if (tiles.contains(upperTile))
          return true;
        else if (cpMode && upperTile.isCpOrFinish())
          return false;
        if (tiles.contains(lowerTile))
          return true;
        else if (cpMode && lowerTile.isCpOrFinish())
          return false;
      }
    }
    return false;
  }

  private boolean isDominantVerticalAcross(int x, int y, int xv, int yv, Collection<MapTile> tiles, boolean cpMode) {
    for (int i = 0; i < Math.abs(yv); i++) {
      double sectY = y - (Math.signum(yv) * (i + 0.5));
      double sectX = getIntersectionWithMapGrid(y, x, yv, xv, sectY);

      int yL = (int) sectY, yU = yL + 1;
      int xU = (int) Math.round(sectX), xL = xU;
      if ((sectX - (int) sectX) == 0.5) {
        if (Math.signum(xv) * Math.signum(yv) < 0) {
          xU--;
        } else {
          xL--;
        }
      }
      MapTile lowerTile = map.getTileOf(xL, yL);
      MapTile upperTile = map.getTileOf(xU, yU);

      if (Math.signum(yv) < 0) {
        if (tiles.contains(lowerTile))
          return true;
        else if (cpMode && lowerTile.isCpOrFinish())
          return false;
        if (tiles.contains(upperTile))
          return true;
        else if (cpMode && upperTile.isCpOrFinish())
          return false;
      } else {
        if (tiles.contains(upperTile))
          return true;
        else if (cpMode && upperTile.isCpOrFinish())
          return false;
        if (tiles.contains(lowerTile))
          return true;
        else if (cpMode && lowerTile.isCpOrFinish())
          return false;
      }
    }
    return false;
  }

  /**
   * Determines the vector equation (x y) - alpha * (xv yv) = (wx X)
   */
  private double getIntersectionWithMapGrid(int x, int y, int xv, int yv, double wx) {
    double alpha = (wx - x) / -xv;
    return y - (alpha * yv);
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
