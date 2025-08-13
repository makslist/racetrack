package de.karopapier.kabotte.rules.special;

import de.karopapier.api.Game;
import de.karopapier.api.Move;
import de.karopapier.api.Player;
import de.karopapier.kabotte.rules.MapRule;
import org.eclipse.collections.api.block.predicate.Predicate;

import java.util.Collection;
import java.util.regex.Pattern;

public class X0orY0Rule extends MapRule {

    private final Predicate<Move> gameRule = m -> m.isStarting() || m.xv == 0 || m.yv == 0;

    public X0orY0Rule(Game game) {
        super(game);
    }

    public static boolean matches(String title) {
        return Pattern.compile(".*" + SPECIAL_MARKER + " Nur x=0 oder y=0 " + SPECIAL_MARKER + ".*").matcher(title).find();
    }

    public Collection<Move> filteredPossibles(Player player) {
        return super.filteredPossibles(player).stream().filter(gameRule).toList();
    }

//    @Override
//    public List<Move> filterNext(Move move) {
//        return move.next(this).stream().filter(gameRule).toList();
//    }
//
//    @Override
//    public Collection<Move> filterNextMvDist(Move move) {
//        return filterNext(move);
//    }

    @Override
    public boolean isValid(int x, int y, int xv, int yv, int round, Collection<Move> predecessors) {
        return round == 0 || xv == 0 || yv == 0;
    }

}
