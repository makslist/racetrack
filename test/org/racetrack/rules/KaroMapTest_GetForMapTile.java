package org.racetrack.rules;

import static org.junit.Assert.*;

import org.junit.*;
import org.racetrack.karoapi.*;

public class KaroMapTest_GetForMapTile {

  KaroMap map;

  // OOX
  // XOO
  @Before
  public void initialzeMap() {
    map = new KaroMap(new String("OOX\n" + "XOO"));
  }

  @Test
  public void test() {
    assertEquals(map.getTileOf(0, 0), MapTile.ROAD);
  }

  @Test
  public void test2() {
    assertEquals(map.getTileOf(1, 0), MapTile.ROAD);
  }

  @Test
  public void test3() {
    assertEquals(map.getTileOf(2, 0), MapTile.GRASS);
  }

  @Test
  public void test4() {
    assertEquals(map.getTileOf(0, 1), MapTile.GRASS);
  }

  @Test
  public void test5() {
    assertEquals(map.getTileOf(1, 1), MapTile.ROAD);
  }

  @Test
  public void test6() {
    assertEquals(map.getTileOf(2, 1), MapTile.ROAD);
  }

}
