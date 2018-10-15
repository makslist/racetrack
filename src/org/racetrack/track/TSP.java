package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.eclipse.collections.api.*;
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

  private static final int DIFF_DISTANCE = 7;
  private static final int MAX_MOVE = 7000;
  private static final int MAX_TOURS = 200;

  private static final Logger logger = Logger.getLogger(PathFinder.class.toString());

  private Game game;
  private GameRule rule;
  private SynchronizedPutUnifiedSetMultimap<MapTile, Edge> edges = new SynchronizedPutUnifiedSetMultimap<>();

  public TSP(Game game, GameRule rule) {
    this.game = game;
    this.rule = rule;
  }

  public MutableList<Tour> solve(MutableCollection<Move> possibles, MutableCollection<MapTile> missingCps) {
    if (missingCps.isEmpty())
      return Tour.SINGLE_FINISH_TOUR;

    ExecutorService executor = Executors.newCachedThreadPool();

    MutableList<MapTile> fromCps = new FastList<>(missingCps).with(MapTile.FINISH);
    MutableList<MapTile> toCps = new FastList<>(fromCps);
    for (MapTile fromCp : fromCps) {
      for (MapTile toCp : toCps.without(fromCp)) {
        executor.submit(() -> edgeRange(fromCp, toCp));
      }
    }

    executor.shutdown();
    try {
      executor.awaitTermination(20, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      logger.warning(e.getMessage());
    }

    MutableList<Tour> tours = new FastList<>();
    MutableCollection<Move> startMoves = possibles.select(rule.filterPossibles());
    for (MapTile toCp : missingCps) {
      short dist = findRange(false, startMoves, toCp);
      tours.addAll(buildTours(new Tour(MapTile.START, toCp, dist), new FastList<>(missingCps).without(toCp)));
    }

    int minimum = tours.min().getDistance();
    return tours.select(t -> t.getDistance() <= minimum + DIFF_DISTANCE).sortThis().take(MAX_MOVE / minimum)
        .take(MAX_TOURS);
  }

  private void edgeRange(MapTile fromCp, MapTile toCp) {
    RichIterable<Edge> edgeList = edges.get(fromCp);
    Edge detect = edgeList.detect(edge -> edge.connects(toCp));
    if (detect == null) {
      short dist = findRange(true, game.getMap().getTilesAsMoves(fromCp), toCp);
      detect = new Edge(fromCp, toCp, dist);
      edges.put(fromCp, detect);
      edges.put(toCp, detect);
    }
  }

  private short findRange(boolean isFromCp, Collection<Move> startMoves, MapTile toCp) {
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
          return move.getTotalLen();
        else {
          queue.addAll(rule.filterNextMvDist(move));
        }
      }
    }
    logger.warning("No valid path found from " + startMoves + " to " + toCp);
    return Short.MAX_VALUE;
  };

  private MutableList<Tour> buildTours(Tour partialTour, MutableCollection<MapTile> missingCps) {
    if (missingCps.isEmpty()) {
      Tour tour = partialTour.copy();
      Edge edge = edges.get(tour.getEnd()).detect(e -> e.connects(MapTile.FINISH));
      tour.addEdge(edge);
      return Lists.mutable.with(tour);
    } else {
      MutableList<Tour> tours = new FastList<>();
      for (MapTile cp : missingCps) {
        Tour tour = partialTour.copy();
        Edge edge = edges.get(tour.getEnd()).detect(e -> e.connects(cp));
        tour.addEdge(edge);
        MutableList<MapTile> restCps = new FastList<>(missingCps).without(cp);
        tours.addAll(buildTours(tour, restCps));
      }
      return tours;
    }
  }

}
