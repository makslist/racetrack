package de.karopapier.kabotte.rules.special;

import de.karopapier.api.Game;
import de.karopapier.api.KaroMap.Tile;
import de.karopapier.api.Move;
import de.karopapier.api.Player;
import de.karopapier.kabotte.rules.MapRule;
import org.eclipse.collections.api.block.predicate.Predicate;

import java.util.Collection;
import java.util.regex.Pattern;

public class EdgeDirRule extends MapRule {

    public static final Pattern TAG = Pattern.compile(".*" + " Richtungswechsel nur am Rand " + ".*", Pattern.CASE_INSENSITIVE);

    private final Predicate<Move> gameRule = m -> m.isStarting()
            || m.getPreds().flatMap(map::getNeighboringTiles).allMatch(Tile.OFF_TRACK::contains)
            || m.getPreds().allMatch(p -> p.angle() == m.angle());

    public EdgeDirRule(Game game) {
        super(game);
    }

    public static boolean matches(String title) {
        return TAG.matcher(title).find();
    }

    public Collection<Move> filteredPossibles(Player player) {
        Collection<Move> possibles = super.filteredPossibles(player);
        return possibles.stream().anyMatch(gameRule) ? possibles.stream().filter(gameRule).toList() : possibles;
    }

    @Override
    public boolean isValid(int x, int y, int xv, int yv, int round, Collection<Move> predecessors) {
        return round == 0 || predecessors.stream().flatMap(map::getNeighboringTiles).allMatch(Tile.OFF_TRACK::contains)
                || predecessors.stream().allMatch(p -> Math.toDegrees(Math.atan2(p.yv, p.xv)) == Math.toDegrees(Math.atan2(yv, xv)));
    }

//    public boolean isValid(Move move) {
//        return super.isValid(move) && gameRule.accept(move);
//    }

//    @Override
//    public List<Move> filterNext(Move move) {
//        return move.next(this).stream().filter(mapRule.and(gameRule)).toList();
//    }
//
//    @Override
//    public Collection<Move> filterNextMvDist(Move move) {
//        return filterNext(move);
//    }

}
