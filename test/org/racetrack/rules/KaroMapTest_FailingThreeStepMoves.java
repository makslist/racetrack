package org.racetrack.rules;

import static org.junit.Assert.*;

import org.junit.*;
import org.racetrack.karoapi.*;

public class KaroMapTest_FailingThreeStepMoves {

  MapRule rule = null;

  // OOXX
  // OOXX
  // XXOO
  // XXOO
  @Before
  public void initialzeMap() {
    KaroMap map = new KaroMap(new String("OOXX\n" + "OOXX\n" + "XXOO\n" + "XXOO\n"));
    rule = new MapRule(map);
  }

  @Test
  public void whenDownRight() {
    Move move = new Move(1, 3, 1, 3);

    assertFalse(rule.isValid(move));
  }

  @Test
  public void whenDownRight2() {
    Move move = new Move(2, 3, 2, 3);

    assertFalse(rule.isValid(move));
  }

  @Test
  public void whenDownRight3() {
    Move move = new Move(3, 3, 2, 3);

    assertFalse(rule.isValid(move));
  }

  @Test
  public void whenDownRight4() {
    Move move = new Move(3, 3, 3, 2);

    assertFalse(rule.isValid(move));
  }

}
