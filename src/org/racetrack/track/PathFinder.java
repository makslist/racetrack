package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

import org.eclipse.collections.api.block.function.*;
import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.primitive.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.eclipse.collections.impl.map.mutable.primitive.*;
import org.racetrack.collections.*;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.track.TSP.*;
import org.racetrack.worker.*;

public class PathFinder implements Callable<Paths> {

  protected static final Logger logger = Logger.getLogger(PathFinder.class.toString());

  private static final int MAX_MOVE_THRESHOLD = 5;
  private static final int MAX_MOVE_LIMIT = 256;

  private static boolean printStatus = true;

  public static void supressOutput() {
    printStatus = false;
  }

  private boolean withMultiCrash = Settings.getInstance().withMultiCrash();

  private Game game;
  private Player player;

  private GameRule rule;
  private CrashDetector crashDetector;
  private TSP tsp;

  private AtomicInteger minPathLength = new AtomicInteger(MAX_MOVE_LIMIT);

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
  public Paths call() {
    Thread.currentThread().setName("PathFinder");
    Paths possiblePaths = rule.filterPossibles(new Paths(player.getNextMoves()));
    if (possiblePaths.isEmpty())
      return Paths.empty();
    crashDetector = new CrashDetector(rule, possiblePaths.getEndMoves());

    if (rule.hasNotXdFinishlineOnF1Circuit(player.getMotion())) {
      possiblePaths = breadthFirstSearch(possiblePaths, MapTile.FINISH, false);
    }

    MutableCollection<MapTile> missingCps = game.withCps() ? player.getMissingCps() : new FastList<>();
    TourStopover tours = tsp.solve(possiblePaths.getEndMoves(), missingCps);
    if (printStatus) {
      ConsoleOutput.println(game.getId(), player.getName() + " travels " + tours.size() + " tour(s)."
          + (missingCps.isEmpty() ? "" : " Missing CPs: " + missingCps));
    }

    if (Thread.currentThread().isInterrupted())
      return Paths.empty();

    ForkJoinPool executor = new ForkJoinPool();
    try {
      return executor.submit(travelTours(possiblePaths, tours)).get();
    } catch (InterruptedException e) {
    } catch (ExecutionException e) {
      logger.severe(e.getMessage());
    } finally {
      executor.shutdownNow();
    }
    return Paths.empty();
  }

  private RecursiveTask<Paths> travelTours(Paths possibles, TourStopover stopOver) {
    return new RecursiveTask<Paths>() {
      private static final long serialVersionUID = 1L;

      @Override
      protected Paths compute() {
        Thread.currentThread().setName("PathFinder to " + stopOver.getCp());

        Paths pathsToCp = stopOver.isStart() ? possibles : breadthFirstSearch(possibles, stopOver.getCp(), true);
        if (pathsToCp.isEmpty() || pathsToCp.getMinLength() > minPathLength.get())
          return Paths.empty();

        if (stopOver.isFinish()) {
          if (printStatus) {
            ConsoleOutput.println(game.getId(),
                stopOver + ": " + pathsToCp.getMinLength() + " " + pathsToCp.getMovesOfRound(game.getCurrentRound())
                    .sortThis((o1, o2) -> o1.getX() - o2.getX() != 0 ? o1.getX() - o2.getX() : o1.getY() - o2.getY()));
          }
          minPathLength.updateAndGet(x -> Math.min(x, pathsToCp.getMinLength()));
          return pathsToCp;
        } else {
          MutableList<ForkJoinTask<Paths>> tasks = stopOver.getNext()
              .collect(nextStop -> travelTours(pathsToCp, nextStop));
          tasks.forEach(t -> t.fork());

          Paths paths = Paths.empty();
          tasks.forEach(t -> paths.merge(t.join()));
          return paths;
        }
      }
    };
  }

  private Paths breadthFirstSearch(Paths starts, MapTile toCp, boolean crossedStartLine) {
    int overshot = getCpFoundOvershot(toCp, crossedStartLine);
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

      Move knownMove;
      if ((knownMove = visitedMoves.get(move.hashCode())) != null) {
        knownMove.merge(move);

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

  private int getCpFoundOvershot(MapTile toCp, boolean crossedStartLine) {
    if (toCp.isFinish() && crossedStartLine)
      return 0;

    int overshot = MAX_MOVE_THRESHOLD;
    if (!game.getMap().isCpClustered(toCp)) {
      overshot += 10;
    } else {
      overshot += game.getMap().getSetting().getFindCpSafetyMargin();
    }
    return overshot;
  }

}
