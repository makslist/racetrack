package de.karopapier.kabotte.rules.special;

import de.karopapier.api.Game;
import de.karopapier.api.Move;
import de.karopapier.api.Player;
import de.karopapier.kabotte.rules.MapRule;
import org.eclipse.collections.api.block.predicate.Predicate;

import java.util.Collection;
import java.util.regex.Pattern;

public class BrakeClampRule extends MapRule {

    public static final Pattern TAG = Pattern.compile(".*Bremspedal klemmt.*");
    private final Predicate<Move> gameRule = m -> m.isStarting() || m.getPreds().allMatch(p -> m.speed() >= p.speed());

    public BrakeClampRule(Game game) {
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
        double speed = Math.sqrt((xv * xv) + (yv * yv));
        return round == 0 || predecessors.stream().allMatch(p -> speed >= p.speed());
    }

//    @Override
//    public Collection<Move> filterNext(Move move) {
//        Collection<Move> onMap = move.next(this);//.filter(mapRule).toList();
//        return onMap.stream().anyMatch(gameRule) ? onMap.stream().filter(gameRule).toList() : onMap;
//    }

//    @Override
//    public Collection<Move> filterNextMvDist(Move move) {
//        return filterNext(move);
//    }

}
