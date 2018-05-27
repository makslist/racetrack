package org.racetrack.track;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import org.eclipse.collections.api.block.function.*;
import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.primitive.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.eclipse.collections.impl.map.mutable.*;
import org.eclipse.collections.impl.map.mutable.primitive.*;
import org.racetrack.karoapi.*;
import org.racetrack.worker.*;

public class TSP {

  private static final int DIFF_DISTANCE = 7;
  private static final int MAX_MOVE = 7000;
  private static final int MAX_TOURS = 200;

  private Game game;
  private Function<Edge, Short> edgeRuler;

  private SynchronizedMutableMap<Edge, ReadWriteLock> edgeLocks = new SynchronizedMutableMap<>(
      new UnifiedMap<Edge, ReadWriteLock>());
  private MutableObjectShortMap<Edge> distances = new ObjectShortHashMap<Edge>();

  private Function<Edge, Short> distanceRuler = edge -> {
    ReadWriteLock rwLock = edgeLocks.getIfAbsentPut(edge, new SeqLock(false));
    SeqLock.SeqReadLock lock = (SeqLock.SeqReadLock) rwLock.readLock();
    try {
      while (true) {
        long lockCounter = lock.tryReadLock();
        short dist = distances.getOrThrow(edge);
        if (lock.retryReadLock(lockCounter))
          return dist;
      }
    } catch (IllegalStateException ise) {
      rwLock.writeLock().lock();
      short dist = edgeRuler.apply(edge);
      distances.put(edge, dist);
      rwLock.writeLock().unlock();
      return dist;
    }
  };

  public TSP(Game game) {
    this.game = game;
  }

  public MutableList<Tour> solve(MutableCollection<MapTile> missingCps, Function<Edge, Short> edgeRuler,
      boolean printProgress) {
    this.edgeRuler = edgeRuler;
    if (missingCps.isEmpty())
      return Tour.SINGLE_FINISH_TOUR;

    ExecutorService executor = Executors.newWorkStealingPool();

    for (MapTile cp : missingCps) {
      for (MapTile other : missingCps) {
        if (cp != other) {
          executor.submit(() -> distanceRuler.apply(new Edge(cp, other)));
        }
      }
      executor.submit(() -> distanceRuler.apply(new Edge(cp, MapTile.FINISH)));
    }

    MutableObjectShortMap<Edge> startDistances = new ObjectShortHashMap<Edge>();
    CliProgressBar progress = CliProgressBar.getTourBar(game, missingCps.size());
    for (MapTile cp : missingCps) {
      Edge start = new Edge(MapTile.START, cp);
      try {
        startDistances.put(start, executor.submit(() -> edgeRuler.apply(start)).get());
        if (printProgress) {
          progress.incProgress();
        }
      } catch (InterruptedException | ExecutionException e) {
      }
    }

    MutableList<Tour> tours = new FastList<>();
    buildTours(new Tour(edge -> startDistances.get(edge), distanceRuler), missingCps, executor, tours);

    try {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    int minimum = tours.min((t1, t2) -> t1.getDistance() - t2.getDistance()).getDistance();
    return tours.select(tour -> tour.getDistance() <= minimum + DIFF_DISTANCE).sortThis().take(MAX_MOVE / minimum)
        .take(MAX_TOURS);
  }

  private void buildTours(Tour tour, MutableCollection<MapTile> missingCps, ExecutorService executor,
      MutableList<Tour> tours) {
    for (MapTile cp : missingCps) {
      Tour tourSection = new Tour(tour, cp);
      MutableList<MapTile> restCps = new FastList<>(missingCps).without(cp);
      if (restCps.isEmpty()) {
        tours.add(tourSection);
        executor.submit(tourSection.evaluate());
      } else {
        buildTours(tourSection, restCps, executor, tours);
      }
    }
  }

}
