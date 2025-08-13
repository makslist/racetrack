package de.karopapier.kabotte.track;

import de.karopapier.api.Game;
import de.karopapier.api.KaroMap.Tile;
import de.karopapier.api.Move;
import de.karopapier.api.Rule;
import de.karopapier.kabotte.rules.KaroMapSetting;
import de.karopapier.kabotte.utils.SeqLock;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.impl.factory.primitive.ObjectIntMaps;

import java.io.Serial;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.stream.Stream;

public final class TSP {

    private final Game game;
    private final KaroMapSetting setting;
    private final Rule rule;
    private final ReadWriteLock edgeLengthLock = new SeqLock(false);
    private final MutableObjectIntMap<String> edgeLength = ObjectIntMaps.mutable.empty();
    private PathFinder scout;
    private Collection<Move> possibles;
    private Collection<Tile> missingCps;
    private TourStopover solvedTours;

    public TSP(Game game, Rule rule) {
        this.game = game;
        setting = KaroMapSetting.readSettings(game.map());
        this.rule = rule;
    }

    public TourStopover solve(Collection<Move> possibles, Collection<Tile> missingCps, PathFinder scout) {
        this.scout = scout;
        if (solvedTours != null && Objects.equals(this.possibles, possibles) && Objects.equals(this.missingCps, missingCps))
            return solvedTours;

        TourStopover start = TourStopover.start();
        if (missingCps.isEmpty()) {
            start.finish(1);
            return start;
        }
        if (missingCps.size() == 1) {
            start.addStop(missingCps.stream().findFirst().get(), 1).finish(1);
            return start;
        }

        this.possibles = possibles;
        this.missingCps = missingCps;

        List<Move> startMoves = possibles.stream().map(Move::copyAsStartover).toList();

        solvedTours = getStartTours(startMoves, missingCps);
        int minimumLength = solvedTours.minTourLength.get();
        if (setting.getTourLengthSafetyMargin() >= 0) {
            solvedTours.trim(minimumLength + setting.getTourLengthSafetyMargin());
        } else if (rule.isMapCircuit())
            solvedTours.trim(minimumLength + 3);
        else if (missingCps.stream().allMatch(game.map()::isClustered)) {
            solvedTours.trim(minimumLength + 3);
        } else if (solvedTours.size() > 10) {
            solvedTours.trim(minimumLength + 6);
        }

        return solvedTours;
    }

    private TourStopover getStartTours(List<Move> startMoves, Collection<Tile> missingCps) {
        TourStopover tours = TourStopover.start();
        ForkJoinPool executor = new ForkJoinPool();
        List<ForkJoinTask<TourStopover>> tasks = missingCps.stream()
                .map(cp -> executor.submit(getTours(tours.addStop(cp, scout.findEdgeLength(false, startMoves, cp)),
                        missingCps.stream().filter(c -> !c.equals(cp)).toList())))
                .toList();
        tasks.forEach(ForkJoinTask::join);
        executor.shutdown();
        return tours;
    }

    private ForkJoinTask<TourStopover> getTours(TourStopover stop, List<Tile> missingCps) {
        return new RecursiveTask<>() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected TourStopover compute() {
                Thread.currentThread().setName("TSP from " + stop.cp + " to " + missingCps);
                if (missingCps.isEmpty())
                    stop.finish(getCpDistance(stop, Tile.FINISH));
                else {
                    List<ForkJoinTask<TourStopover>> tasks = missingCps.stream()
                            .map(cp -> getTours(stop.addStop(cp, getCpDistance(stop, cp)),
                                    missingCps.stream().filter(c -> !c.equals(cp)).toList()).fork())
                            .toList();
                    tasks.forEach(ForkJoinTask::join);
                }
                return stop;
            }
        };
    }

    private int getCpDistance(TourStopover from, Tile toCp) {
        String key = from.cp.compareTo(toCp) < 0 ? from.cp.name() + "/" + toCp.name() : toCp.name() + "/" + from.cp.name();
        SeqLock.SeqReadLock lock = (SeqLock.SeqReadLock) edgeLengthLock.readLock();

        try {
            while (true) {
                long counter = lock.tryReadLock();
                int dist = edgeLength.getOrThrow(key);
                if (lock.retryReadLock(counter))
                    return dist;
            }
        } catch (IllegalStateException ise) {
            int dist = scout.findEdgeLength(true, game.map().getStartMoves(from.cp), toCp);
            edgeLengthLock.writeLock().lock();
            int oldDist = edgeLength.getIfAbsentPut(key, dist);
            if (oldDist != dist)
                edgeLength.put(key, Math.min(oldDist, dist));
            edgeLengthLock.writeLock().unlock();
            return dist;
        }
    }

    public static class TourStopover implements Comparable<TourStopover> {

        public final Tile cp;
        private final TourStopover prev;
        private final int totalLength;
        private final AtomicInteger minTourLength = new AtomicInteger(Integer.MAX_VALUE);
        private final AtomicBoolean visited = new AtomicBoolean();
        private final Collection<TourStopover> next = new ConcurrentLinkedQueue<>();

        private TourStopover(TourStopover prev, Tile cp, int length) {
            this.prev = prev;
            this.cp = cp;
            totalLength = prev != null ? prev.totalLength + length : length;
        }

        public static TourStopover FinishStop() {
            return new TourStopover(null, Tile.FINISH, 1);
        }

        private static TourStopover start() {
            return new TourStopover(null, Tile.START, 0);
        }

        public Stream<TourStopover> next() {
            return next.stream().sorted();
        }

        private synchronized TourStopover addStop(Tile cp, int length) {
            TourStopover nextStop = new TourStopover(this, cp, length);
            next.add(nextStop);
            return nextStop;
        }

        private void finish(int length) {
            TourStopover finish = addStop(Tile.FINISH, length);
            finish.minTourLength.set(finish.totalLength);
            finish.propagateMin(finish.totalLength);
        }

        private void propagateMin(int minLength) {
            if (prev != null && prev.minTourLength.get() > minLength)
                prev.propagateMin(prev.minTourLength.updateAndGet(v -> Integer.min(v, minLength)));
        }

        private void trim(int maxLength) {
            Iterator<TourStopover> it = next.iterator();
            while (it.hasNext()) {
                TourStopover stop = it.next();
                if (stop.minTourLength.get() > maxLength)
                    it.remove();
                else
                    stop.trim(maxLength);
            }
        }

        public int isUnvisited() {
            return prev != null ? prev.isUnvisited() : size();
        }

        public void markAsVisited() {
            visited.set(true);
        }

        public int size() {
            return visited.get() ? 0 : (next.isEmpty() ? 1 : next.stream().mapToInt(TourStopover::size).sum());
        }

        public boolean isStart() {
            return cp.isStart();
        }

        public boolean isFinish() {
            return cp.isFinish();
        }

        public List<String> print() {
            return isFinish() ? List.of(toString()) : next().map(TourStopover::print).flatMap(List::stream).toList();
        }

        @Override
        public String toString() {
            if (isFinish())
                return (minTourLength.get() != Integer.MAX_VALUE ? minTourLength.get() + " " : "") + (prev != null ? prev.toString() : "S") + "/F";
            else if (prev != null) {
                String prevCp = prev.toString();
                return (prevCp != null ? (prevCp + "/") : "") + cp.toString();
            } else
                return "S";
        }

        @Override
        public int compareTo(TourStopover o) {
            return minTourLength.get() - o.minTourLength.get();
        }
    }

}
