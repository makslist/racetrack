package de.karopapier.kabotte.track;

import de.karopapier.api.Game;
import de.karopapier.api.KaroMap.Tile;
import de.karopapier.api.Move;
import de.karopapier.api.Player;
import de.karopapier.api.Rule;
import de.karopapier.kabotte.config.Settings;
import de.karopapier.kabotte.rules.KaroMapSetting;
import de.karopapier.kabotte.rules.RuleFactory;
import de.karopapier.kabotte.track.TSP.TourStopover;
import de.karopapier.kabotte.utils.IntBucketPriorityQueue;
import de.karopapier.kabotte.worker.KaBotteConsole;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;

import java.io.Serial;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PathFinder implements Callable<Paths> {

    // TODO make private again, in settings
    public static final int CRASH_DETECTOR_DEPTH = 12;
    /**
     * It's crucial for further checkpoint search to allow slightly longer paths e.g. dead ends or hard turns right after
     * the check point.
     */
    private static final int MAX_MOVE_PADDING = 5;
    private static final int MAX_MOVE_LIMIT = 2 << 16;//4096; TODO

    private KaBotteConsole console;
    private final boolean withMultiCrash = Settings.getInstance().withMultiCrash();
    private final Game game;
    private final Player player;
    private final Rule rule;
    private final KaroMapSetting setting;
    private final TSP tsp;
    private final AtomicInteger minGlobalPathLength = new AtomicInteger(MAX_MOVE_LIMIT);
    // CrashDetector
    private final int cdCurrentRound;
    private boolean cdCrashAhead;

    //TODO add parameter multiCrash
    public PathFinder(Game game, Player player) {
        this.game = game;
        this.player = player;
        rule = RuleFactory.getInstance(game);
        tsp = new TSP(game, rule);
        setting = KaroMapSetting.readSettings(game.map());
        cdCurrentRound = game.currentRound();
    }

    public PathFinder(Game game, Player player, Rule rule, TSP tsp) {
        this.game = game;
        this.player = game.asPlayer(player.id());
        this.rule = rule;
        setting = KaroMapSetting.readSettings(game.map());
        this.tsp = tsp;
        cdCurrentRound = game.currentRound();
    }

    public void setConsole(KaBotteConsole console) {
        this.console = console;
    }

    public static void qualifying(Rule rule, Collection<Move> startMoves) {
        MutableLongSet visited = new LongHashSet(2 << 18);
        Queue<Move> queue = new LinkedList<>(startMoves);
        Rule.Flagman flagman = rule.flagman();
        try {
            int startFinishDist = 0;
            Move move;
            while ((move = queue.poll()) != null) {
                if (move.round > startFinishDist + 7) // TODO MAX_DIST_FINISH_FOR_F1
                    return;
                if (visited.add(Move.hashKey(move))) {
                    if (rule.hasCrossed(move.x, move.y, move.xv, move.yv, Tile.FINISH.asList(), false)) {
                        if (startFinishDist == 0)
                            startFinishDist = move.round;
                        flagman.add(move);
                    } else
                        queue.addAll(move.next(rule));
                }
            }
        } catch (OutOfMemoryError e) {
            System.err.println("OutOfMemoryError in PathFinder.qualifying: " + e.getMessage());
        }
    }

    @Override
    public Paths call() {
        Thread.currentThread().setName("PathFinder: " + game.id());
        ForkJoinPool executor = new ForkJoinPool();
        try {
            Paths possiblePaths = new Paths(rule.filteredPossibles(player));
            if (possiblePaths.isEmpty())
                return Paths.empty();

            cdCrashAhead = possiblePaths.getEndMoves().stream().noneMatch(m -> rule.areNextSafe(m, CRASH_DETECTOR_DEPTH));//TODO maxDepth in settings));

            if (rule.hasToCrossFinish(player.motion()))
                possiblePaths = breadthFirstSearch(possiblePaths, TourStopover.FinishStop(), false);

            Collection<Tile> missingCps = game.withCps() ? player.missingCps() : new ArrayList<>();
            TourStopover tours = tsp.solve(possiblePaths.getEndMoves(), missingCps, this);
            if (console != null && tours.isUnvisited() > 4)
                console.verbose(game.id(), player.name() + " travels " + tours.isUnvisited() + " tour(s)."
                        + (missingCps.isEmpty() ? "" : " Missing CPs: " + missingCps));

            Paths paths = executor.submit(travelTours(possiblePaths, tours)).get();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(600, TimeUnit.SECONDS)) // TODO setting
                    return paths;
            } catch (InterruptedException ignored) {
            }
            paths.movesInRound(-1);
            return paths;
        } catch (InterruptedException e) {
            if (console != null)
                console.warning(game.id(), "PathFinder interrupted.");
            executor.shutdownNow();
        } catch (ExecutionException e) {
            if (console != null)
                console.error(game.id(), "ExecutionException" + ": " + e.getMessage());
        } catch (Exception e) {
            if (console != null)
                console.error(game.id(), "Execption: " + e.getMessage());
            throw e;
        }
        return Paths.empty();
    }

    private RecursiveTask<Paths> travelTours(Paths possibles, TourStopover stopOver) {
        return new RecursiveTask<>() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected Paths compute() {
                try {
                    if (Thread.currentThread().isInterrupted())
                        return Paths.empty();

                    Thread.currentThread().setName("PathFinder to " + stopOver.cp);

                    Paths pathsToCp = stopOver.isStart() ? possibles : breadthFirstSearch(possibles.copy(), stopOver, true);
                    if (pathsToCp.isEmpty() || pathsToCp.length() > minGlobalPathLength.get())
                        return Paths.empty();

                    if (stopOver.isFinish()) {
                        minGlobalPathLength.updateAndGet(x -> Integer.min(x, pathsToCp.length()));
                        return pathsToCp;
                    } else {
                        List<ForkJoinTask<Paths>> tasks = stopOver.next().map(stop -> travelTours(pathsToCp, stop).fork())
                                .toList();
                        stopOver.markAsVisited();

                        Paths paths = Paths.empty();
                        tasks.forEach(t -> paths.merge(t.join()));
                        return paths;
                    }
                } catch (Exception e) {
                    if (console != null)
                        console.error(game.id(), "Exception imtravelTours: " + e.getMessage());
                    throw e;
                }
            }
        };
    }

    private Paths breadthFirstSearch(Paths starts, TourStopover toCp, boolean crossedStartLine) {
        int safetyPadding = crossedStartLine && toCp.isFinish() ? 0
                : MAX_MOVE_PADDING + game.map().clusterDist(toCp.cp) + setting.getFindCpOvershot();
        MutableLongObjectMap<Move> visited = new LongObjectHashMap<>(2 << 18);
        Queue<Move> queue = IntBucketPriorityQueue.of(starts.getEndMoves(), m -> m.round, 2 << 18);
        boolean crossedCp = false;
        int minPathLengthToCp = minGlobalPathLength.get(); // initialize with currently known global minimum

        Paths shortestPaths = starts.copy().clear();
        Move move;
        while ((move = queue.poll()) != null) {
            if (move.round > minPathLengthToCp + safetyPadding || move.round > minGlobalPathLength.get())
                return shortestPaths;

            long key = Move.hashKey(move);
            Move knownMove;
            if ((knownMove = visited.get(key)) != null)
                knownMove.merge(move);
            else if (visited.put(key, move) == null && rule.isFinishLineCrossedLegally(move)) {
                if (rule.hasCrossedCp(move, toCp.cp) && shortestPaths.add(move) && !crossedCp && (crossedCp = true)) // only true for one time because SCE
                    minPathLengthToCp = move.round;
                if (!queue.addAll(move.next(rule)) // no following moves means crash
                        && (game.isCrashAllowed() || isCrashUnavoidable(move)))
                    queue.addAll(move.nextAfterCrash(game.zzz(), withMultiCrash));
            }
        }
//        throw new RuntimeException("No Path from " + starts.getEndMoves() + " to " + toCp + " found. (Map is detected as " + (rule.isMapCircuit() ? "" : "not ") + "circuit"); //TODO PathNotFoundError
        return shortestPaths;
    }

    public int findEdgeLength(boolean isFromCp, List<Move> startMoves, Tile toCp) {
        LongHashSet visited = new LongHashSet();
        Queue<Move> queue = new LinkedList<>(startMoves);
        Move move;
        while ((move = queue.poll()) != null) {
            if (visited.add(Move.hashKey(move))
                    && rule.isFinishLineCrossedLegally(move) && !(isFromCp && toCp.isCp() && rule.hasCrossedFinish(move)))
                if (rule.hasCrossedCp(move, toCp))
                    return move.round;
                else if (!queue.addAll(move.nextMap(rule)) && (game.isCrashAllowed() || isCrashUnavoidable(move)))
                    queue.addAll(move.nextAfterCrash(game.zzz(), withMultiCrash));
        }
        if (toCp.isFinish()) {
            // TODO no path to finish found
            if (console != null)
                console.warning(game.id(), "No valid path found from " + startMoves + " to " + toCp + ". This map is recognized as "
                        + (rule.isMapCircuit() ? "circuit" : "non-circuit")
                        + ". Set this map to non/circuit manually.");
        } else
            // TODO no path between two cps found
            if (console != null)
                console.warning(game.id(), "No path found from " + startMoves + " to " + toCp);
        return Integer.MAX_VALUE;
    }

    public boolean isCrashUnavoidable(Move move) {
        return cdCrashAhead && move.round < cdCurrentRound + CRASH_DETECTOR_DEPTH && !move.hasCrashHappened(move.round - cdCurrentRound);
    }

}
