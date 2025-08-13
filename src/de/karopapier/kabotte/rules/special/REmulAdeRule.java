package de.karopapier.kabotte.rules.special;

import de.karopapier.api.Game;
import de.karopapier.api.Move;
import de.karopapier.api.Player;
import de.karopapier.kabotte.rules.MapRule;
import org.eclipse.collections.api.map.primitive.MutableLongBooleanMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongBooleanHashMap;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class REmulAdeRule extends MapRule {

    private final MutableLongBooleanMap reSafe = new LongBooleanHashMap(2 << 18);

    public REmulAdeRule(Game game) {
        super(game);
    }

    public static boolean matches(String title) {
        return Pattern.compile(".*" + SPECIAL_MARKER + " " + "Rundenerster wiederholt seinen Zug" + "|" + "REmulAde" + " " + SPECIAL_MARKER + ".*").matcher(title).find();
    }

    @Override
    public Collection<Move> filteredPossibles(Player player) {
        Collection<Move> possibles = super.filteredPossibles(player);

        if (game.getPosInCurrentRoundOfNextPlayer() <= Math.floorDiv(game.activePlayersCount(), 7) + 1) {
            if (possibles.stream().noneMatch(Move::isRepeat))
                return possibles;

            if (game.activePlayersCount() <= 3 && game.wasPlayerFirstInPrevRound())
                return possibles;//TODO comment.addComment("RE-Schutz: Wiederholung.");

            Player playerStillOnField = game.activePlayers()
                    .filter(p -> possibles.stream().anyMatch(((Predicate<Move>) Move::isRepeat).and(m -> m.isBlocked(p.motion())))).findFirst()
                    .orElse(null);
            if (playerStillOnField != null)
                return possibles;//TODO comment.addComment("RE-Schutz: Spieler " + playerStillOnField.name() + " steht noch auf dem Feld.");

            return possibles.stream().filter(Move::isRepeat).toList();
        }

        return possibles;
    }

//    @Override
//    public boolean isValid(int x, int y, int xv, int yv, int round, Collection<Move> predecessors) {
//        if (round == 0 || (xv == 0 && yv == 0))
//            return true;
//
//        int players = game.activePlayersCount();
//        if (onMap.stream().noneMatch(Move.IS_REPEAT))
//            return onMap;
//
//        double repeatProp = players <= 3 ? 1d / (3 * 2) : (players >= 7 ? 2d / 7 : 1d / players);
//        int depth = move.maxNorm() + (int) Math.ceil(move.maxNorm() * repeatProp) + 1;
//        return onMap.stream().filter(Move.IS_REPEAT.or(m -> isMoveSafe(m, depth))).toList();
//    }
//
//    @Override
//    public Collection<Move> filterNext(Move move) {
//        Collection<Move> onMap = move.nextMap(this);
//
//        if (move.isStarting() || move.isCrash())
//            return onMap;
//
//        int players = game.activePlayersCount();
//        if (onMap.stream().noneMatch(Move.IS_REPEAT))
//            return onMap;
//
//        double repeatProp = players <= 3 ? 1d / (3 * 2) : (players >= 7 ? 2d / 7 : 1d / players);
//        int depth = move.maxNorm() + (int) Math.ceil(move.maxNorm() * repeatProp) + 1;
//        return onMap.stream().filter(Move.IS_REPEAT.or(m -> isMoveSafe(m, depth))).toList();
//    }

    private boolean isMoveSafe(Move move, int d) {
        if (move.maxNorm() <= 1)
            return reSafe.getIfAbsentPut(move.hashCode(), !move.nextMap(this).isEmpty());
        else if (d <= 0)
            return move.maxNorm() <= 1;
        else if (reSafe.containsKey(move.hashCode()))
            return reSafe.get(move.hashCode());
        else {
            Collection<Move> next = move.nextMap(this);
            if (next.stream().anyMatch(Move::isRepeat))
                return reSafe.getIfAbsentPut(move.hashCode(),
                        next.stream().filter(Move::isRepeat).anyMatch(m -> isMoveSafe(m, d - 1)));
            else
                return reSafe.getIfAbsentPut(move.hashCode(), next.stream().anyMatch(m -> isMoveSafe(m, d - 1)));
        }
    }

    public Optional<Boolean> withdraw(Game game, Player player) {
        Optional<Boolean> withdraw = super.withdraw(player);
        return withdraw.isPresent() ? withdraw : (game.isWithIq() && REmulAdeRule.matches(game.name()) ? Optional.of(true) : Optional.empty());
    }

}
