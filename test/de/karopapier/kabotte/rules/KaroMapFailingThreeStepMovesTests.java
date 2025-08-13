package de.karopapier.kabotte.rules;

import de.karopapier.api.KaroMap;
import de.karopapier.api.Move;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class KaroMapFailingThreeStepMovesTests {

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
