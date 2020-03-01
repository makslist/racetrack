package org.racetrack.rules;

import static org.junit.Assert.*;

import org.junit.*;
import org.racetrack.karoapi.*;

public class KaroMapTest_SucessfullCrossingCp {

  MapRule rule = null;

  @Before
  public void initialzeMap() {
    KaroMap map = new KaroMap(new String("O1F"));
    rule = new MapRule(map);
  }

  @Test
  public void whenfromCpToCp() {
    Move move = new Move(1, 0, 1, 0);

    assertTrue(rule.hasXdCp(move, MapTile.ONE));
  }

  @Test
  public void singleCpCrossing() {
    Move move = new Move(2, 0, 2, 0);

    assertFalse(rule.hasXdCp(move, MapTile.ONE));
  }

}
