package de.karopapier.kabotte.rules.special;

import de.karopapier.api.Game;
import de.karopapier.api.KaroMap.Tile;
import de.karopapier.api.Move;
import de.karopapier.api.Player;
import de.karopapier.kabotte.rules.MapRule;
import org.eclipse.collections.api.block.predicate.Predicate;

import java.util.Collection;
import java.util.regex.Pattern;

public class EdgeRule extends MapRule {

    private final Predicate<Move> gameRule = m -> m.isStarting()
            || m.getPreds().flatMap(map::getNeighboringTiles).anyMatch(Tile.OFF_TRACK::contains);

    public EdgeRule(Game game) {
        super(game);
    }

    public static boolean matches(String title) {
        return Pattern.compile(".*" + " Randrennen " + ".*").matcher(title).find();
    }

    public Collection<Move> filteredPossibles(Player player) {
        return super.filteredPossibles(player).stream().filter(gameRule).toList();
    }

    @Override
    public boolean isValid(int x, int y, int xv, int yv, int round, Collection<Move> predecessors) {
        return round == 0 || predecessors.stream().flatMap(map::getNeighboringTiles).anyMatch(Tile.OFF_TRACK::contains);
    }

//    @Override
//    public boolean isValidMap(int x, int y, int xv, int yv, int round, Collection<Move> predecessors) {
//        return isValid(x, y, xv, yv, round, predecessors);
//    }

}
