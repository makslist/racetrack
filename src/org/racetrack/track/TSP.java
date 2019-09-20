package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.set.primitive.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.eclipse.collections.impl.multimap.set.*;
import org.eclipse.collections.impl.set.mutable.primitive.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public final class TSP {

  private static final int MAX_TOURS = 200;

  private static final Logger logger = Logger.getLogger(PathFinder.class.toString());

  private Game game;
  private GameRule rule;
  private SynchronizedPutUnifiedSetMultimap<MapTile, Edge> edges = new SynchronizedPutUnifiedSetMultimap<>();

  private boolean areCpsClustered;

  public TSP(Game game, GameRule rule) {
    this.game = game;
    this.rule = rule;
  }

  public MutableList<Tour> solve(MutableCollection<Move> possibles, MutableCollection<MapTile> missingCps) {
    if (missingCps.isEmpty())
      return Tour.SINGLE_FINISH_TOUR;

    areCpsClustered = game.getMap().areCpsClustered(missingCps);

    MutableCollection<Move> startMoves = possibles.select(rule.filterPossibles());
    CrashDetector crashDetector = new CrashDetector(rule, startMoves);

    Collection<RecursiveTask<MutableList<Tour>>> results = ForkJoinTask
        .invokeAll(missingCps.collect(cp -> new RecursiveTask<MutableList<Tour>>() {
          private static final long serialVersionUID = 1L;

          @Override
          protected MutableList<Tour> compute() {
            int dist = findRange(false, startMoves, cp, crashDetector);
            Tour tour = new Tour(MapTile.START, cp, dist);
            return getTours(tour, new FastList<>(missingCps).without(cp));
          }
        }));

    MutableList<Tour> tours = new FastList<>(results).flatCollect(t -> {
      try {
        return t.get();
      } catch (InterruptedException | ExecutionException e) {
      }
      return null;
    });
    tours.sortThis();

    if (!game.isFormula1()) { // findRange to Finish doesn't work before Xing finish line first
      int minDistToFinish = findRange(false, startMoves, MapTile.FINISH, crashDetector);
      if (minDistToFinish > 100
          && tours.anySatisfy(t -> t.getDistance() >= minDistToFinish * (areCpsClustered ? 0.8 : 0.9))) {
        tours = tours.reject(t -> t.getDistance() < minDistToFinish * (areCpsClustered ? 0.8 : 0.9));
      }
    }
    int minTourLength = tours.min().getDistance();
    if (rule.isMapCircuit()) {
      tours = tours.select(t -> t.getDistance() <= minTourLength * (areCpsClustered ? 1.15 : 1.25));
    } else {
      tours = tours.select(t -> t.getDistance() <= minTourLength * (areCpsClustered ? 1.1 : 1.30));
    }
    return tours.take(MAX_TOURS);
  }

  private MutableList<Tour> getTours(Tour partialTour, MutableCollection<MapTile> missingCps) {
    if (missingCps.isEmpty()) {
      Tour tour = partialTour.copy();
      tour.addEdge(getEdgeRange(tour.getEnd(), MapTile.FINISH));
      return Lists.mutable.with(tour);
    } else {
      Collection<RecursiveTask<MutableList<Tour>>> results = ForkJoinTask
          .invokeAll(missingCps.collect(cp -> new RecursiveTask<MutableList<Tour>>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected MutableList<Tour> compute() {
              Tour tour = partialTour.copy();
              tour.addEdge(getEdgeRange(tour.getEnd(), cp));
              return getTours(tour, new FastList<>(missingCps).without(cp));
            }
          }));
      return new FastList<>(results).flatCollect(t -> {
        try {
          return t.get();
        } catch (InterruptedException | ExecutionException e) {
        }
        return null;
      });
    }
  }

  private Edge getEdgeRange(MapTile fromCp, MapTile toCp) {
    Edge detect = edges.get(fromCp).detect(edge -> edge.connects(toCp));
    if (detect == null) {
      int dist = findRange(true, game.getMap().getTilesAsMoves(fromCp), toCp, null);
      detect = new Edge(fromCp, toCp, dist);
      edges.put(fromCp, detect);
      edges.put(toCp, detect);
    }
    return detect;
  }

  private int findRange(boolean isFromCp, Collection<Move> startMoves, MapTile toCp, CrashDetector cD) {
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
          MutableCollection<Move> nextMoves = rule.filterNextMvDist(move);
          if (!nextMoves.isEmpty()) {
            queue.addAll(nextMoves);
          } else if (game.isCrashAllowed() || (cD != null && cD.isCrashAhead(move))) {
            queue.addAll(move.getMovesAfterCrash(game.getZzz()));
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

}
