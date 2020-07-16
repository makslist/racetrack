package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.logging.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.primitive.*;
import org.eclipse.collections.api.set.primitive.*;
import org.eclipse.collections.impl.factory.primitive.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.eclipse.collections.impl.set.mutable.primitive.*;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public final class TSP {

  private static final Logger logger = Logger.getLogger(PathFinder.class.toString());

  private boolean withMultiCrash = Settings.getInstance().withMultiCrash();

  private Game game;
  private GameRule rule;

  private final ReadWriteLock edgeLengthLock = new SeqLock(false);
  private MutableObjectIntMap<String> edgeLength = ObjectIntMaps.mutable.empty();

  public TSP(Game game, GameRule rule) {
    this.game = game;
    this.rule = rule;
  }

  public TourStopover solve(MutableCollection<Move> possibles, MutableCollection<MapTile> missingCps) {
    TourStopover start = TourStopover.start();
    if (missingCps.isEmpty()) {
      start.addFinish(1);
      return start;
    }
    if (missingCps.size() == 1) {
      start.addStop(missingCps.getOnly(), 1).addFinish(1);
      return start;
    }

    MutableCollection<Move> startMoves = possibles.select(rule.filterPossibles());
    CrashDetector crashDetector = new CrashDetector(rule, startMoves);

    TourStopover tours = getStartTours(startMoves, missingCps.toSortedList(), crashDetector);

    int minimumLength = tours.minTourLength;
    if (game.getMap().getSetting().getTourLengthSafetyMargin() > 0) { // depends on predefined rules for a specific map
      tours.trimMax(minimumLength + game.getMap().getSetting().getTourLengthSafetyMargin());
    } else if (rule.isMapCircuit() || missingCps.size() <= 1 || game.getMap().areCpsClustered(missingCps)) {
      tours.trimMax(minimumLength + 4);
    } else {
      tours.trimMax(minimumLength + 4); // TODO
    }

    // tours.printHistogram();

    return tours;
  }

  private TourStopover getStartTours(MutableCollection<Move> startMoves, MutableCollection<MapTile> missingCps,
      CrashDetector crashDetector) {
    TourStopover tours = TourStopover.start();
    ForkJoinPool executor = new ForkJoinPool();
    MutableCollection<ForkJoinTask<TourStopover>> tasks = missingCps.collect(nextCp -> executor
        .submit(getTours(tours.addStop(nextCp, findEdgeLength(false, startMoves, nextCp, crashDetector)),
            missingCps.reject(c -> c.equals(nextCp)))));

    tasks.forEach(t -> tours.setMin(t.join()));
    executor.shutdownNow();
    return tours;
  }

  private RecursiveTask<TourStopover> getTours(TourStopover stop, MutableCollection<MapTile> missingCps) {
    return new RecursiveTask<TSP.TourStopover>() {
      private static final long serialVersionUID = 1L;

      @Override
      protected TourStopover compute() {
        Thread.currentThread().setName("TSP from " + stop.cp + " to " + missingCps);

        if (missingCps.isEmpty()) {
          stop.setMin(stop.addFinish(getCpDistance(stop.prev, stop, MapTile.FINISH)));
        } else {
          MutableCollection<RecursiveTask<TourStopover>> tasks = missingCps
              .collect(nextCp -> getTours(stop.addStop(nextCp, getCpDistance(stop.prev, stop, nextCp)),
                  missingCps.reject(c -> c.equals(nextCp))));
          tasks.forEach(t -> t.fork());
          tasks.forEach(t -> stop.setMin(t.join()));
        }
        return stop;
      }
    };
  }

  private int getCpDistance(TourStopover prev, TourStopover from, MapTile toCp) {
    String key = from.cp.compareTo(toCp) < 0 ? from.cp.name() + "/" + toCp.name() : toCp.name() + "/" + from.cp.name();
    SeqLock.SeqReadLock lock = (SeqLock.SeqReadLock) edgeLengthLock.readLock();

    try {
      while (true) {
        long counter = lock.tryReadLock();
        Integer dist = edgeLength.getOrThrow(key);
        if (lock.retryReadLock(counter)) {
          // triangle-inequality
          if (prev != null && !prev.isStart()) {
            int directLength = getCpDistance(null, prev, toCp);
            int startDist = getCpDistance(null, prev, from.cp);
            return startDist + dist < directLength ? directLength - startDist : dist;
          }
          return dist;
        }
      }
    } catch (IllegalStateException ise) {
      int dist = findEdgeLength(true, game.getMap().getTilesAsMoves(from.cp), toCp, null);
      edgeLengthLock.writeLock().lock();
      int oldDist = edgeLength.getIfAbsentPut(key, dist);
      if (oldDist != dist) {
        edgeLength.put(key, Math.min(oldDist, dist));
      }
      edgeLengthLock.writeLock().unlock();
      return dist;
    }
  }

  private int findEdgeLength(boolean isFromCp, Collection<Move> startMoves, MapTile toCp, CrashDetector cD) {
    MutableIntSet visitedMoves = new IntHashSet();
    Queue<Move> queue = new LinkedList<>(startMoves);
    while (!queue.isEmpty()) {
      Move move = queue.poll();
      if (visitedMoves.add(move.hashCode())) {
        if (toCp.isFinish() && rule.hasForbidXdFinishline(move)) {
          continue;
        } else if (isFromCp && toCp.isCp() && rule.hasXdFinishlineForDist(move)) {
          continue;
        } else if (rule.hasXdCp(move, toCp))
          return move.getPathLen();
        else {
          if (!queue.addAll(rule.filterNextMvDist(move))) {
            if (game.isCrashAllowed() || (cD != null && cD.isCrashAhead(move))) {
              queue.addAll(move.getMovesAfterCrash(game.getZzz(), withMultiCrash));
            }
          }
        }
      }
    }
    if (toCp.isFinish()) {
      logger.warning("No valid path found from " + startMoves + " to " + toCp + ". This map is recognized as "
          + (rule.isMapCircuit() ? "circuit" : "non-circuit")
          + ". Set this map to non/circuit manually if this error still happens.");
    } else {
      logger.warning("No path found from " + startMoves + " to " + toCp.toString());
    }
    return Integer.MAX_VALUE;
  }

  public static class TourStopover implements Comparable<TourStopover> {

    private static TourStopover start() {
      return new TourStopover(null, MapTile.START, 0);
    }

    private TourStopover prev;
    private MapTile cp;
    private int totalLength;
    private int minTourLength = Integer.MAX_VALUE;
    private MutableList<TourStopover> next = new FastList<TourStopover>();

    private TourStopover(TourStopover prev, MapTile cp, int length) {
      this.prev = prev;
      this.cp = cp;
      totalLength = prev != null ? prev.totalLength + length : length;
    }

    public MapTile getCp() {
      return cp;
    }

    public MutableList<TourStopover> getNext() {
      return next.sortThis();
    }

    private TourStopover addStop(MapTile cp, int length) {
      TourStopover nextStop = new TourStopover(this, cp, length);
      next.add(nextStop);
      return nextStop;
    }

    private TourStopover addFinish(int length) {
      TourStopover finish = addStop(MapTile.FINISH, length);
      finish.minTourLength = finish.totalLength;
      minTourLength = finish.minTourLength;
      return finish;
    }

    private void setMin(TourStopover stop) {
      if (stop.minTourLength < minTourLength) {
        minTourLength = stop.minTourLength;
      }
      next.sortThis();
    }

    private void trimMax(int maxLength) {
      Iterator<TourStopover> it = next.iterator();
      while (it.hasNext()) {
        TourStopover stop = it.next();
        if (stop.minTourLength > maxLength) {
          it.remove();
        } else {
          stop.trimMax(maxLength);
        }
      }
    }

    private int getStartLength() {
      return prev != null ? prev.getStartLength() : minTourLength;
    }

    public int size() {
      return next.isEmpty() ? 1 : (int) next.sumOfInt(x -> x.size());
    }

    public boolean isStart() {
      return cp.isStart();
    }

    public boolean isFinish() {
      return cp.isFinish();
    }

    public void printHistogram() {
      int[] hist = getHist();
      System.out.println("");
      StringBuilder cols = new StringBuilder("| ");
      StringBuilder count = new StringBuilder("| ");
      for (int i = 0; i < hist.length; i++) {
        if (hist[i] != 0) {
          cols.append(+i + " | ");
          count.append(hist[i] + " | ");
        }
      }
      System.out.println(cols);
      System.out.println(count);
    }

    private int[] getHist() {
      if (next.isEmpty())
        return new int[] { totalLength };

      int[] hist = new int[256];
      for (TourStopover stop : next) {
        int[] hist2 = stop.getHist();
        if (hist2.length == 1) {
          hist[hist2[0]] += 1;
        } else {
          for (int i = 0; i < hist2.length; i++) {
            hist[i] += hist2[i];
          }
        }
      }
      return hist;
    }

    public String getUnidealFactor() {
      return totalLength / (double) getStartLength() + " (" + (totalLength - getStartLength()) + ")";
    }

    @Override
    public String toString() {
      return (prev != null ? prev.toString() + "," : "") + cp + "/" + minTourLength;
    }

    @Override
    public int compareTo(TourStopover o) {
      return minTourLength - o.minTourLength;
    }

  }

}
