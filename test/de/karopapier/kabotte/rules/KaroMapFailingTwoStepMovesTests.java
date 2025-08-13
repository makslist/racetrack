package de.karopapier.kabotte.rules;

import de.karopapier.api.KaroMap;
import de.karopapier.api.Move;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class KaroMapFailingTwoStepMovesTests {

    MapRule rule = null;

    @Before
    public void initializeMap() {
        KaroMap map = new KaroMap("""
                OOO
                OXO
                OOX""");
        rule = new MapRule(map);
    }

    @Test
    public void whenHorizontalRight() {
        Move move = new Move(2, 2, 2, 0);

        assertFalse(rule.isValid(move));
    }

    @Test
    public void whenVerticalDown() {
        Move move = new Move(2, 2, 0, 2);

        assertFalse(rule.isValid(move));
    }

    @Test
    public void whenDiagonalRight() {
        Move move = new Move(2, 1, 2, 1);

        assertFalse(rule.isValid(move));
    }

    @Test
    public void whenDiagonalDown() {
        Move move = new Move(1, 2, 1, 2);

        assertFalse(rule.isValid(move));
    }

}
