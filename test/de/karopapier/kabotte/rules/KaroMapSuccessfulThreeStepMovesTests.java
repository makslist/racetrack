package de.karopapier.kabotte.rules;

import de.karopapier.api.KaroMap;
import de.karopapier.api.Move;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class KaroMapSuccessfulThreeStepMovesTests {

    MapRule rule = null;

    @Before
    public void initializeMap() {
        KaroMap map = new KaroMap("""
                OOXX
                OOXX
                XXOO
                XXOO
                """);
        rule = new MapRule(map);
    }

    @Test
    public void whenRightDown() {
        Move move = new Move(3, 2, 3, 1);

        assertTrue(rule.isValid(move));
    }

    @Test
    public void whenDownRight() {
        Move move = new Move(2, 3, 1, 3);

        assertTrue(rule.isValid(move));
    }

    @Test
    public void whenLeftUp() {
        Move move = new Move(1, 0, -1, -3);

        assertTrue(rule.isValid(move));
    }

    @Test
    public void whenUpLeft() {
        Move move = new Move(0, 1, -3, -1);

        assertTrue(rule.isValid(move));
    }

}
