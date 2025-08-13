package de.karopapier.kabotte.rules.special;

import de.karopapier.api.Game;
import de.karopapier.api.Move;
import de.karopapier.api.Player;
import de.karopapier.kabotte.rules.MapRule;
import org.eclipse.collections.api.block.predicate.Predicate;

import java.util.Collection;
import java.util.regex.Pattern;

public class NumpadRule extends MapRule {

    private final Predicate<Move> gameRule = m -> m.isStarting()
            || m.getPreds().allMatch(p -> p.xv != m.xv && p.yv != m.yv);

    public NumpadRule(Game game) {
        super(game);
    }

    public static boolean matches(String title) {
        return Pattern.compile(".*" + SPECIAL_MARKER + " Ziffernblock " + SPECIAL_MARKER + ".*").matcher(title).find();
    }

    public Collection<Move> filteredPossibles(Player player) {
        Collection<Move> possibles = super.filteredPossibles(player);
        return possibles.stream().anyMatch(gameRule) ? possibles.stream().filter(gameRule).toList() : possibles;
    }

    @Override
    public boolean isValid(int x, int y, int xv, int yv, int round, Collection<Move> predecessors) {
        return round == 0 || predecessors.stream().allMatch(p -> p.xv != xv && p.yv != yv);
    }

//    @Override
//    public Collection<Move> filterNext(Move move) { //TODO wie soll ich das nur loesen: ich betrachte nur jeden einzelnen move
//        Collection<Move> onMap = move.next(this);//.filter(mapRule).toList();
//        return onMap.stream().anyMatch(gameRule) ? onMap.stream().filter(gameRule).toList() : onMap;
//    }

//    @Override
//    public boolean isValidMap(int x, int y, int xv, int yv, int round, Collection<Move> predecessors) {
//        return isValid(x, y, xv, yv, round, predecessors);
//    }

}
