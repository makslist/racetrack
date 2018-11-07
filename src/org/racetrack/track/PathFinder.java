package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.eclipse.collections.api.block.function.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.primitive.*;
import org.eclipse.collections.impl.map.mutable.primitive.*;
import org.racetrack.collections.*;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public class PathFinder implements Callable<Paths> {

  protected static final Logger logger = Logger.getLogger(PathFinder.class.toString());

  protected static final int MAX_MOVE_TRESHOLD = 13;
  protected static final int MAX_MOVE_LIMIT = 256;

  protected Game game;
  protected Player player;

  protected GameRule rule;
  protected CrashDetector crashDetector;
  protected TSP tsp;

  protected int minPathLength = MAX_MOVE_LIMIT;

  public PathFinder(Game game, Player player) {
    this.game = game;
    this.player = player;
    rule = RuleFactory.getInstance(game);
    tsp = new TSP(game, rule);
  }

  public PathFinder(Game game, Player player, GameRule rule, TSP tsp) {
    this.game = game;
    this.player = game.getPlayer(player.getId());
    this.rule = rule;

    this.tsp = tsp;
  }

  @Override
  public Paths call() throws Exception {
    Paths possiblePaths = rule.filterPossibles(new Paths(player.getNextMoves()));
    if (possiblePaths.isEmpty())
      return new Paths();
    crashDetector = new CrashDetector(rule, possiblePaths.getEndMoves());

    if (rule.hasNotXdFinishlineOnF1Circuit(player.getMotion())) {
      possiblePaths = findPathToCp(possiblePaths, MapTile.FINISH, true);
    }

    MutableList<Tour> tours = game.withCps() ? tsp.solve(possiblePaths.getEndMoves(), player.getMissingCps())
        : Tour.SINGLE_FINISH_TOUR;
    if (game.getMap().getSetting().getMaxTours() > 0) {
      tours = tours.take(game.getMap().getSetting().getMaxTours());
    }
    Paths shortestPaths = getMinPathsForTours(tours, possiblePaths);

    if (shortestPaths.isEmpty())
      return possiblePaths;

    return shortestPaths;
  }

  protected Paths getMinPathsForTours(Collection<Tour> tours, Paths possibles) {
    Paths paths = new Paths();
    int maxThreads = Integer.max(Settings.getInstance().getInt(Property.maxParallelTourThreads), 1);
    int minThreads = Integer.min(maxThreads, Runtime.getRuntime().availableProcessors());
    ExecutorService threadPool = Executors.newWorkStealingPool(minThreads);
    CompletionService<Paths> service = new ExecutorCompletionService<>(threadPool);
    for (Tour tour : tours) {
      service.submit(travelTour(tour, possibles));
    }
    for (int i = 1; i <= tours.size(); i++) {
      try {
        Paths travel = service.take().get();
        int travelLength = travel.getMinTotalLength();
        if (travelLength <= minPathLength) {
          minPathLength = travelLength;
          paths.merge(travel);
        }
      } catch (InterruptedException | ExecutionException e) {
        logger.warning(e.getMessage());
      }
    }
    threadPool.shutdown();

    if (game.isCrashAllowed()) {
      logger.fine("Game " + game.getId() + ": Begining to trim the path.");
      paths.trimCrashPaths(game.getZzz());
    }

    return paths.getShortestTracks();
  }

  /**
   * Travels a tour consisting of a series of checkpoints one by one in the defined order.
   *
   * @return the paths with minimal length, traveling all checkpoint ending with finish
   */
  protected Callable<Paths> travelTour(Tour tour, Paths possibles) {
    return () -> {
      Paths intermedPaths = possibles;
      for (MapTile cp : tour.getSequence()) {
        if (!intermedPaths.isEmpty()) {
          intermedPaths = findPathToCp(intermedPaths, cp, false);
        }
      }
      return intermedPaths;
    };
  }

  protected Paths findPathToCp(Paths possibles, MapTile cp, boolean crossF1Finish) {
    MutableIntObjectMap<Move> visitedMoves = new IntObjectHashMap<>(2 << 16);
    Paths filtered = rule.filterPossibles(possibles);
    Queue<Move> queue = ShortBucketPriorityQueue.of(filtered.getEndMoves(),
        (Function<Move, Short>) move -> move.getTotalLen());
    boolean crossedCP = false;
    int minPathToCpLength = minPathLength; // initialize with global known minimum

    Paths shortestPaths = Paths.getCopy(filtered);
    while (!queue.isEmpty()) {
      Move move = queue.poll();
      int pathLength = move.getPathLen();
      if ((pathLength > minPathLength)
          || (pathLength > minPathToCpLength + (cp.isFinish() && !crossF1Finish ? 0 : MAX_MOVE_TRESHOLD)))
        return shortestPaths;

      Move visitedMove = visitedMoves.get(move.hashCode());
      if (visitedMove != null) {
        visitedMove.merge(move);

      } else {
        visitedMoves.put(move.hashCode(), move);// Remember each visited move

        if (rule.hasForbidXdFinishline(move)) {
          continue;
        } else if (rule.hasXdCp(move, cp)) {
          shortestPaths.add(move);
          if (!crossedCP) {
            crossedCP = true;
            minPathToCpLength = pathLength;
          }
        } else {
          Collection<Move> nextMoves = rule.filterNextMv(move);

          if (!nextMoves.isEmpty()) {
            queue.addAll(nextMoves);
          } else if ((queue.isEmpty() && minPathToCpLength >= minPathLength) || game.isCrashAllowed()
              || crashDetector.isCrashAhead(move)) {
            queue.addAll(move.getMovesAfterCrash(game.getZzz()));
          }
        }
      }
    }
    return shortestPaths;
  }

}
