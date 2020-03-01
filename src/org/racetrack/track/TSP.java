package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.set.primitive.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.eclipse.collections.impl.multimap.set.*;
import org.eclipse.collections.impl.set.mutable.primitive.*;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public final class TSP {

  private static final Logger logger = Logger.getLogger(PathFinder.class.toString());

  private boolean withMultiCrash = Settings.getInstance().withMultiCrash();

  private Game game;
  private GameRule rule;
  private SynchronizedPutUnifiedSetMultimap<MapTile, Edge> edges = new SynchronizedPutUnifiedSetMultimap<>();

  private int maxExecutionTimeMinutes = Settings.getInstance().maxExecutionTimeMinutes();

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
    } else {
      if (rule.isMapCircuit()) {
        tours.trimMax(minimumLength + 4);
      } else {
        if (missingCps.size() <= 1 || game.getMap().areCpsClustered(missingCps)) {
          tours.trimMax(minimumLength + 4);
        } else {
          tours.trimMax(minimumLength + 8);
          int minDistToFinish = findEdgeLength(false, startMoves, MapTile.FINISH, crashDetector);
          if (minDistToFinish > minimumLength) {
            System.out.println("\nminDistFinish: " + minDistToFinish + " ; minimumLength: " + minimumLength);
            if (minDistToFinish - minimumLength > 20) {
              tours.trimMin(minimumLength + 10);
            }
          }
        }
      }
    }

    return tours;
  }

  private TourStopover getStartTours(MutableCollection<Move> startMoves, MutableCollection<MapTile> missingCps,
      CrashDetector crashDetector) {
    TourStopover tours = TourStopover.start();
    @SuppressWarnings("serial")
    Collection<RecursiveTask<TourStopover>> nextStopTasks = ForkJoinTask
        .invokeAll(missingCps.collect(nextCp -> new RecursiveTask<TourStopover>() {
          @Override
          protected TourStopover compute() {
            return getTours(tours.addStop(nextCp, findEdgeLength(false, startMoves, nextCp, crashDetector)),
                missingCps.reject(c -> c.equals(nextCp)));
          }
        }));
    for (RecursiveTask<TourStopover> task : nextStopTasks) {
      try {
        TourStopover nextStop = task.get(maxExecutionTimeMinutes, TimeUnit.MINUTES);
        if (nextStop.minTourLength < tours.minTourLength) {
          tours.minTourLength = nextStop.minTourLength;
        }
      } catch (InterruptedException e) {
      } catch (ExecutionException e) {
        logger.severe(e.getMessage());
      } catch (TimeoutException e) {
        nextStopTasks.forEach(t -> t.cancel(true));
        e.printStackTrace();
      }
    }
    tours.next.sortThis((x1, x2) -> x1.totalLength - x2.totalLength);
    return tours;
  }

  private TourStopover getTours(TourStopover stop, MutableCollection<MapTile> missingCps) {
    if (missingCps.isEmpty()) {
      TourStopover finish = stop.addFinish(getCpDistance(stop.cp, MapTile.FINISH));
      if (finish.minTourLength < stop.minTourLength) {
        stop.minTourLength = finish.minTourLength;
      }
    } else {

      @SuppressWarnings("serial")
      Collection<RecursiveTask<TourStopover>> nextStopTasks = ForkJoinTask
          .invokeAll(missingCps.collect(nextCp -> new RecursiveTask<TourStopover>() {
            @Override
            protected TourStopover compute() {
              return getTours(stop.addStop(nextCp, getCpDistance(stop.cp, nextCp)),
                  missingCps.reject(c -> c.equals(nextCp)));
            }
          }));
      for (RecursiveTask<TourStopover> task : nextStopTasks) {
        try {
          TourStopover nextStop = task.get(maxExecutionTimeMinutes, TimeUnit.MINUTES);
          if (nextStop.minTourLength < stop.minTourLength) {
            stop.minTourLength = nextStop.minTourLength;
          }
        } catch (InterruptedException e) {
        } catch (ExecutionException e) {
          logger.severe(e.getMessage());
        } catch (TimeoutException e) {
          nextStopTasks.forEach(t -> t.cancel(true));
          e.printStackTrace();
        }
      }
    }
    stop.next.sortThis((x1, x2) -> x1.minTourLength - x2.minTourLength);
    return stop;
  }

  private int getCpDistance(MapTile fromCp, MapTile toCp) {
    return edges.get(fromCp).detectIfNone(e -> e.connects(toCp), () -> {
      int dist = findEdgeLength(true, game.getMap().getTilesAsMoves(fromCp), toCp, null);
      Edge edge = new Edge(fromCp, toCp, dist);
      edges.put(fromCp, edge);
      edges.put(toCp, edge);
      return edge;
    }).dist;
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

  public static class TourStopover {

    private static TourStopover start() {
      return new TourStopover(null, MapTile.START, 0);
    }

    private TourStopover prev;
    private MapTile cp;
    private int totalLength;
    private int minTourLength = Integer.MAX_VALUE;
    private MutableList<TourStopover> next = new FastList<>();

    private TourStopover(TourStopover prev, MapTile cp, int length) {
      this.prev = prev;
      this.cp = cp;
      totalLength = prev != null ? prev.totalLength + length : length;
    }

    public MapTile getCp() {
      return cp;
    }

    public MutableList<TourStopover> getNext() {
      return next;
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

    private boolean trimMin(int minLength) {
      Iterator<TourStopover> it = next.iterator();
      while (it.hasNext()) {
        TourStopover stop = it.next();
        if (stop.cp.isFinish())
          return stop.minTourLength < minLength;
        else if (stop.trimMin(minLength)) {
          it.remove();
        }
      }
      Optional<TourStopover> minOptional = next.minOptional((t1, t2) -> t1.minTourLength - t2.minTourLength);
      if (minOptional.isPresent()) {
        minTourLength = minOptional.get().minTourLength;
      }
      return next.isEmpty();
    }

    private int getStartLength() {
      return prev != null ? prev.getStartLength() : minTourLength;
    }

    public int size() {
      return next.isEmpty() ? 1 : (int) next.sumOfInt(x -> x.size());
    }

    public String getUnidealFactor() {
      return totalLength / (double) getStartLength() + " (" + (totalLength - getStartLength()) + ")";
    }

    @Override
    public String toString() {
      return (prev != null ? prev.toString() + "," : "") + cp + "/" + minTourLength;
    }

  }

  private class Edge {
    private MapTile cp1;
    private MapTile cp2;
    private int dist;

    public Edge(MapTile cp1, MapTile cp2, int dist) {
      this.cp1 = cp1;
      this.cp2 = cp2;
      this.dist = dist;
    }

    public boolean connects(MapTile cp) {
      return cp1.equals(cp) || cp2.equals(cp);
    }

    @Override
    public boolean equals(Object obj) {
      Edge edge = (Edge) obj;
      return (cp1.equals(edge.cp1) && cp2.equals(edge.cp2)) || (cp1.equals(edge.cp2) && cp2.equals(edge.cp1));
    }

    @Override
    public int hashCode() {
      return cp1.hashCode() ^ cp2.hashCode();
    }

    @Override
    public String toString() {
      return new StringBuffer().append(cp1).append("/").append(cp2).toString();
    }

  }

}
