package org.racetrack.track;

import org.racetrack.karoapi.*;

public class Edge {

  private MapTile cp1;
  private MapTile cp2;
  private int dist;

  public Edge(MapTile cp1, MapTile cp2) {
    this.cp1 = cp1;
    this.cp2 = cp2;
  }

  public Edge(MapTile cp1, MapTile cp2, int dist) {
    this.cp1 = cp1;
    this.cp2 = cp2;
    this.dist = dist;
  }

  public MapTile getCP1() {
    return cp1;
  }

  public MapTile getCP2() {
    return cp2;
  }

  public int getDist() {
    return dist;
  }

  public boolean connects(MapTile cp) {
    return cp1.equals(cp) || cp2.equals(cp);
  }

  public MapTile getStart() {
    return cp1 == MapTile.START || cp1 != MapTile.FINISH ? cp1 : cp2;
  }

  public MapTile getFinish() {
    return cp2 == MapTile.FINISH || cp2 != MapTile.START ? cp2 : cp1;
  }

  @Override
  public boolean equals(Object obj) {
    Edge edge = (Edge) obj;
    return (cp1.equals(edge.cp1) && cp2.equals(edge.cp2)) || (cp1.equals(edge.cp2) && cp2.equals(edge.cp1));
  }

  @Override
  public int hashCode() {
    return cp1.hashCode() ^ cp2.hashCode();
  }

  @Override
  public String toString() {
    return new StringBuffer().append(cp1).append("/").append(cp2).toString();
  }

}
