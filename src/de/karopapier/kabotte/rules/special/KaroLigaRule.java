package de.karopapier.kabotte.rules.special;

import de.karopapier.api.Game;
import de.karopapier.api.Move;
import de.karopapier.api.Player;
import de.karopapier.kabotte.rules.MapRule;

import java.util.Collection;
import java.util.regex.Pattern;

public class KaroLigaRule extends MapRule {

    public static final Pattern TAG = Pattern.compile(".*KaroLiga.*");

    public KaroLigaRule(Game game) {
        super(game);
    }

    public static boolean matches(String title) {
        return TAG.matcher(title).find();
    }

    @Override
    public Collection<Move> filteredPossibles(Player player) {
        //TODO comment game.getCurrentRound() != 1 ? path : path.addComment("Die Auswertung der KaroLiga findet man übrigens im Karo-Wiki: https://wiki.karopapier.de/KaroLiga");
        return super.filteredPossibles(player);
    }

    @Override
    public Collection<Move> filteredStartMoves(Collection<Move> possibles, Collection<Move> playerMoves, int round) {
        if (round == 1 && !game.isWithIq() && possibles.size() != playerMoves.size())
            return possibles.stream().filter(m -> !playerMoves.contains(m)).toList();
        else
            return playerMoves;
    }

}
