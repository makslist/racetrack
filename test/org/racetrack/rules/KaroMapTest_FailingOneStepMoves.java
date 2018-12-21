package org.racetrack.rules;

import static org.junit.Assert.*;

import org.junit.*;
import org.racetrack.karoapi.*;

public class KaroMapTest_FailingOneStepMoves {

  MapRule rule = null;

  @Before
  public void initialzeMap() {
    KaroMap map = new KaroMap(new String("OOO\n" + "OXO\n" + "OOO"));
    rule = new MapRule(map);
  }

  @Test
  public void whenHorizontalRight() {
    Move move = new Move(1, 1, 1, 0);

    assertFalse(rule.isValid(move));
  }

  @Test
  public void whenHorizontalLeft() {
    Move move = new Move(1, 1, -1, 0);

    assertFalse(rule.isValid(move));
  }

  @Test
  public void whenVerticalDown() {
    Move move = new Move(1, 1, 0, 1);

    assertFalse(rule.isValid(move));
  }

  @Test
  public void whenVerticalUp() {
    Move move = new Move(1, 1, 0, -1);

    assertFalse(rule.isValid(move));
  }

  @Test
  public void whenDiagonalDownRight() {
    Move move = new Move(1, 1, 1, 1);

    assertFalse(rule.isValid(move));
  }

  @Test
  public void whenDiagonalUpLeft() {
    Move move = new Move(1, 1, -1, -1);

    assertFalse(rule.isValid(move));
  }

  @Test
  public void whenDiagonalDownLeft() {
    Move move = new Move(1, 1, -1, 1);

    assertFalse(rule.isValid(move));
  }

  @Test
  public void whenDiagonalUpRight() {
    Move move = new Move(1, 1, 1, -1);

    assertFalse(rule.isValid(move));
  }

}
