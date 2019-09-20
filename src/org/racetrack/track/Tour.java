package org.racetrack.track;

import java.util.*;
import java.util.logging.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.karoapi.*;

public class Tour implements Comparable<Tour> {

  private static final Logger logger = Logger.getLogger(Tour.class.getName());

  public static final MutableList<Tour> SINGLE_FINISH_TOUR = Lists.fixedSize
      .with(new Tour(MapTile.START, MapTile.FINISH));

  private MapTile end;
  private MutableList<Edge> edges = new FastList<>(0);

  private Tour() {
  }

  public Tour(MapTile start, MapTile end) {
    this.end = end;
    edges.add(new Edge(start, end));
  }

  public Tour(MapTile start, MapTile end, int dist) {
    this.end = end;
    edges.add(new Edge(start, end, dist));
  }

  public Tour(Edge edge) {
    end = edge.getCP1() != MapTile.START ? edge.getCP1() : edge.getCP2();
    edges.add(edge);
  }

  public Tour copy() {
    Tour tour = new Tour();
    tour.end = end;
    tour.edges = new FastList<>(edges);
    return tour;
  }

  public void addEdge(Edge edge) {
    if (end == null) {
      edges.add(edge);
    } else if (end.equals(edge.getCP1())) {
      end = edge.getCP2();
      edges.add(edge);
    } else if (end.equals(edge.getCP2())) {
      end = edge.getCP1();
      edges.add(edge);
    } else {
      logger.severe("Error in tour " + edges + ": " + edge + " doesn't connect to tour end " + end);
    }
  }

  public Collection<MapTile> getSequence() {
    MapTile last = MapTile.START;
    Collection<MapTile> sequence = new FastList<>();
    for (Edge edge : edges) {
      last = edge.getCP1() != last ? edge.getCP1() : edge.getCP2();
      sequence.add(last);
    }
    return sequence;
  }

  public MapTile getEnd() {
    return end;
  }

  public int getDistance() {
    return (int) edges.sumOfInt(each -> each.getDist());
  }

  @Override
  public int compareTo(Tour tour) {
    return getDistance() - tour.getDistance();
  }

  @Override
  public String toString() {
    return getSequence() + ": " + getDistance();
  }

}
