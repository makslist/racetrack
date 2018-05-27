package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;

import org.eclipse.collections.api.block.function.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.karoapi.*;

public class Tour implements Comparable<Tour> {

  public static final MutableList<Tour> SINGLE_FINISH_TOUR = Lists.fixedSize.with(new Tour(MapTile.FINISH));

  private MutableList<MapTile> cps = null;
  private Function<Edge, Short> startRuler = null;
  private Function<Edge, Short> edgeRuler = null;
  private short distance = -1;

  public Tour(Function<Edge, Short> startRuler, Function<Edge, Short> edgeRuler) {
    cps = Lists.mutable.empty();
    this.startRuler = startRuler;
    this.edgeRuler = edgeRuler;
  }

  public Tour(Tour tour, MapTile cp) {
    cps = new FastList<>(tour.cps).with(cp);
    startRuler = tour.startRuler;
    edgeRuler = tour.edgeRuler;
  }

  private Tour(MapTile cp) {
    cps = Lists.mutable.with(cp);
  }

  public MapTile getLast() {
    return cps.getLast();
  }

  public Collection<MapTile> getSequence() {
    return new FastList<>(cps).with(MapTile.FINISH);
  }

  public Callable<Short> evaluate() {
    return () -> {
      if (distance >= 0)
        return distance;

      distance = 0;
      MapTile prev = MapTile.START;
      for (MapTile cp : cps) {
        Edge edge = new Edge(prev, cp);
        if (prev == MapTile.START) {
          distance += startRuler.apply(edge);
        } else {
          distance += edgeRuler.apply(edge);
        }
        prev = cp;
      }
      distance += edgeRuler.apply(new Edge(prev, MapTile.FINISH));
      return distance;
    };
  }

  public int getDistance() {
    if (distance < 0) {
      try {
        evaluate().call();
      } catch (Exception e) {
      }
    }
    return distance;
  }

  @Override
  public int compareTo(Tour tour) {
    return distance - tour.distance;
  }

  @Override
  public String toString() {
    return cps.toString() + " with distance " + distance;
  }

}
