package de.karopapier.kabotte.rules.special;

import de.karopapier.api.Game;
import de.karopapier.api.Move;
import de.karopapier.api.Player;
import de.karopapier.kabotte.rules.MapRule;
import org.eclipse.collections.api.block.predicate.Predicate;

import java.util.Collection;
import java.util.regex.Pattern;

public class DragRule extends MapRule { // Drück' aufs Gas aber nicht auf die Bremse
    // Drück nicht auf die Bremse DrinaB
    // Nicht auf die Bremse treten NaudBt

    private final Predicate<Move> gameRule = m -> m.isStarting() || m.getPreds().allMatch(p -> m.maxNorm() >= p.maxNorm());

    public DragRule(Game game) {
        super(game);
    }

    public static boolean matches(Game game) {
        return Pattern.compile(".*" + SPECIAL_MARKER + " DraG " + SPECIAL_MARKER + ".*").matcher(game.name()).find()
                && !game.isCrashAllowed();
    }

    @Override
    public Collection<Move> filteredPossibles(Player player) {
        Collection<Move> possibles = super.filteredPossibles(player);
        return possibles.stream().anyMatch(gameRule) ? possibles.stream().filter(gameRule).toList() : possibles;
    }

//    @Override
//    public Collection<Move> filterNext(Move move) { TODO
//        Collection<Move> onMap = move.next(this);//.filter(mapRule).toList();
//        return onMap.stream().anyMatch(gameRule) ? onMap.stream().filter(gameRule).toList() : onMap;
//    }

//    @Override
//    public Collection<Move> filterNextMvDist(Move move) {
//        return filterNext(move);
//    }

}
