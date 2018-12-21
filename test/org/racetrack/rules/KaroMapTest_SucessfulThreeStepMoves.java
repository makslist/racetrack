package org.racetrack.rules;

import static org.junit.Assert.*;

import org.junit.*;
import org.racetrack.karoapi.*;

public class KaroMapTest_SucessfulThreeStepMoves {

  MapRule validator = null;

  // OOXX
  // OOXX
  // XXOO
  // XXOO
  @Before
  public void initialzeMap() {
    KaroMap map = new KaroMap(new String("OOXX\n" + "OOXX\n" + "XXOO\n" + "XXOO\n"));
    validator = new MapRule(map);
  }

  @Test
  public void whenRightDown() {
    Move move = new Move(3, 2, 3, 1);

    assertTrue(validator.isValid(move));
  }

  @Test
  public void whenDownRight() {
    Move move = new Move(2, 3, 1, 3);

    assertTrue(validator.isValid(move));
  }

  @Test
  public void whenLeftUp() {
    Move move = new Move(1, 0, -1, -3);

    assertFalse(!validator.isValid(move));
  }

  @Test
  public void whenUpLeft() {
    Move move = new Move(0, 1, -3, -1);

    assertFalse(!validator.isValid(move));
  }

}
