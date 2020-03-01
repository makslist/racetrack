package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

import org.eclipse.collections.api.block.function.*;
import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.map.primitive.*;
import org.eclipse.collections.impl.map.mutable.primitive.*;
import org.racetrack.collections.*;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.track.TSP.*;
import org.racetrack.worker.*;

public class PathFinder implements Callable<Paths> {

  private static final int MAX_MOVE_THRESHOLD = 5;
  private static final int MAX_MOVE_LIMIT = 256;

  private static final Logger logger = Logger.getLogger(PathFinder.class.toString());

  private boolean withMultiCrash = Settings.getInstance().withMultiCrash();

  private Game game;
  private Player player;

  private GameRule rule;
  private CrashDetector crashDetector;
  private TSP tsp;

  private AtomicBoolean isCanceled;

  private AtomicInteger minPathLength = new AtomicInteger(MAX_MOVE_LIMIT);

  public PathFinder(Game game, Player player) {
    this.game = game;
    this.player = player;
    rule = RuleFactory.getInstance(game);
    tsp = new TSP(game, rule);
    isCanceled = new AtomicBoolean(false);
  }

  public PathFinder(Game game, Player player, GameRule rule, TSP tsp, AtomicBoolean isCanceled) {
    this.game = game;
    this.player = game.getPlayer(player.getId());
    this.rule = rule;
    this.tsp = tsp;
    this.isCanceled = isCanceled;
  }

  @Override
  public Paths call() {
    Thread.currentThread().setName("PathFinder");
    Paths possiblePaths = rule.filterPossibles(new Paths(player.getNextMoves()));
    if (possiblePaths.isEmpty())
      return Paths.empty();
    crashDetector = new CrashDetector(rule, possiblePaths.getEndMoves());

    if (rule.hasNotXdFinishlineOnF1Circuit(player.getMotion())) {
      possiblePaths = breadthFirstSearch(possiblePaths, MapTile.FINISH, false);
    }

    MutableCollection<MapTile> missingCps = player.getMissingCps();
    TourStopover tours = tsp.solve(possiblePaths.getEndMoves(), missingCps);
    ConsoleOutput.println(game.getId(), player.getName() + " travels " + tours.size() + " tour(s)."
        + (missingCps.isEmpty() ? "" : " Missing CPs: " + missingCps));

    return travelTours(possiblePaths, tours);
  }

  private Paths travelTours(Paths possibles, TourStopover stopOver) {
    if (isCanceled.get())
      return Paths.empty();

    if (possibles.getMinLength() >= minPathLength.get())
      return Paths.empty();

    if (stopOver.getCp().isFinish()) {
      Paths findPathToCp = breadthFirstSearch(possibles, stopOver.getCp(), true);
      int totalLength = findPathToCp.getMinTotalLength();
      // if (findPathToCp.getMinLength() != 0) {
      ConsoleOutput.println(game.getId(), stopOver + ":" + findPathToCp.getMinLength());
      // }
      minPathLength.updateAndGet(x -> totalLength < x ? totalLength : x);
      return findPathToCp;
    }

    final Paths toCp = stopOver.getCp().isCp() ? breadthFirstSearch(possibles, stopOver.getCp(), true) : possibles;
    Paths paths = Paths.empty();
    @SuppressWarnings("serial")
    Collection<RecursiveTask<Paths>> results = ForkJoinTask
        .invokeAll(stopOver.getNext().collect(nextStop -> new RecursiveTask<Paths>() {
          @Override
          protected Paths compute() {
            return travelTours(toCp, nextStop);
          }
        }));
    results.forEach(p -> {
      try {
        paths.merge(p.get());
      } catch (InterruptedException e) {
      } catch (ExecutionException e) {
        logger.severe(e.getMessage());
        e.printStackTrace();
      }
    });
    return paths;
  }

  protected Paths breadthFirstSearch(Paths starts, MapTile toCp, boolean crossedStartLine) {
    int overshot = getFindCpOvershot(toCp, crossedStartLine);
    MutableIntObjectMap<Move> visitedMoves = new IntObjectHashMap<>(2 << 18);
    Paths filtered = rule.filterPossibles(starts);
    Queue<Move> queue = ShortBucketPriorityQueue.of(filtered.getEndMoves(),
        (Function<Move, Short>) move -> (short) move.getTotalLen());
    boolean crossedCP = false;
    int minPathLengthToCp = minPathLength.get(); // initialize with global known minimum

    Paths shortestPaths = Paths.getCopy(filtered);
    while (!queue.isEmpty()) {
      Move move = queue.poll();
      if (move.getPathLen() > minPathLengthToCp + overshot || move.getPathLen() > minPathLength.get())
        return shortestPaths;

      Move visitedMove;
      if ((visitedMove = visitedMoves.get(move.hashCode())) != null) {
        visitedMove.merge(move);

      } else {
        visitedMoves.put(move.hashCode(), move);// Remember each visited move

        if (rule.hasForbidXdFinishline(move)) {
          continue;
        } else if (rule.hasXdCp(move, toCp) && shortestPaths.add(move) && (!crossedCP && (crossedCP ^= true))) {
          minPathLengthToCp = move.getPathLen();
        } else if (!queue.addAll(rule.filterNextMv(move))
            && ((queue.isEmpty() && minPathLengthToCp >= minPathLength.get()) || game.isCrashAllowed()
                || crashDetector.isCrashAhead(move))) {
          queue.addAll(move.getMovesAfterCrash(game.getZzz(), withMultiCrash));
        }
      }
    }
    return shortestPaths;
  }

  private int getFindCpOvershot(MapTile toCp, boolean crossedStartLine) {
    if (!(toCp.isFinish() && crossedStartLine)) {
      int overshot = MAX_MOVE_THRESHOLD;
      if (!game.getMap().isCpClustered(toCp)) {
        overshot += 10;
      } else {
        overshot += game.getMap().getSetting().getFindCpSafetyMargin();
      }
      return overshot;
    }
    return 0;
  }

}
