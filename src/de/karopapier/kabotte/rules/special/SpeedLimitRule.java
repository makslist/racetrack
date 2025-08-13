package de.karopapier.kabotte.rules.special;

import de.karopapier.api.Game;
import de.karopapier.api.Move;
import de.karopapier.api.Player;
import de.karopapier.kabotte.rules.MapRule;
import org.eclipse.collections.api.block.predicate.Predicate;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpeedLimitRule extends MapRule {

    public static final Pattern TAG = Pattern.compile(".*" + SPECIAL_MARKER + " Geschwindigkeitsbegrenzung " + SPECIAL_MARKER + ".*");
    public static final Pattern SPEEDLIMIT_PATTERN = Pattern.compile("-?\\d+");
    private int speedLimit = 10; // default
    private final Predicate<Move> gameRule = m -> m.speed() <= speedLimit;
    public SpeedLimitRule(Game game) {
        super(game);

        Matcher matcher = SPEEDLIMIT_PATTERN.matcher(game.name());
        speedLimit = matcher.find() ? Integer.parseInt(matcher.group()) : 10;
    }

    public static boolean matches(String title) {
        return TAG.matcher(title).find();
    }

    public Collection<Move> filteredPossibles(Player player) {
        return super.filteredPossibles(player).stream().filter(gameRule).toList();
    }

    @Override
    public boolean isValid(int x, int y, int xv, int yv, int round, Collection<Move> predecessors) {
        return Math.sqrt(xv * xv + yv * yv) <= speedLimit;
    }

//    @Override
//    public boolean isValidMap(int x, int y, int xv, int yv, int round, Collection<Move> predecessors) {
//        return isValid(x, y, xv, yv, round, predecessors);
//    }

}
