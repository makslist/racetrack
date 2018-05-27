package org.racetrack.rules;

import static org.junit.Assert.*;

import org.junit.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.MapRule;

public class KaroMapTest_FailingTwoStepMoves {

  MapRule rule = null;

  // OOO
  // OXO
  // OOX
  @Before
  public void initialzeMap() {
    KaroMap map = new KaroMap(new String("OOO\n" + "OXO\n" + "OOX"));
    rule = new MapRule(map);
  }

  @Test
  public void whenHorizontalRight() {
    Move move = new Move(2, 2, 2, 0);

    assertTrue(rule.isValid(move));
  }

  @Test
  public void whenVerticalDown() {
    Move move = new Move(2, 2, 0, 2);

    assertTrue(rule.isValid(move));
  }

  @Test
  public void whenDiagonalRight() {
    Move move = new Move(2, 1, 2, 1);

    assertTrue(rule.isValid(move));
  }

  @Test
  public void whenDiagonalDown() {
    Move move = new Move(1, 2, 1, 2);

    assertTrue(rule.isValid(move));
  }

}
