package de.karopapier.kabotte.rules;

import de.karopapier.api.KaroMap;
import de.karopapier.api.KaroMap.Tile;
import de.karopapier.api.Move;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KaroMapSuccessfulCrossingCpTests {

    MapRule rule = null;

    @Before
    public void initializeMap() {
        KaroMap map = new KaroMap("O1F");
        rule = new MapRule(map);
    }

    @Test
    public void whenFromCpToCp() {
        Move move = new Move(1, 0, 1, 0);

        assertTrue(rule.hasCrossedCp(move, Tile.ONE));
    }

    @Test
    public void singleCpCrossing() {
        Move move = new Move(2, 0, 2, 0);

        assertFalse(rule.hasCrossedCp(move, Tile.ONE));
    }

}
