package de.karopapier.kabotte.track;

import de.karopapier.api.Move;
import de.karopapier.api.Player;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Turn implements Comparable<Turn> {

    private static final NumberFormat ROUND_FORMAT = new DecimalFormat("000");

    public static Turn getStartState(int round, Map<Player, Integer> playerFinishRound) {
        Turn current = new Turn(null, round, new Player(), null, Outcome.NEUTRAL);
        int minRound = playerFinishRound.values().stream().min(Comparator.comparingInt(i -> i)).orElse(0);
        for (Player p : playerFinishRound.keySet())
            if (p.hasMovedIn(round))
                current = minRound == playerFinishRound.get(p) ? current.finish(round, p) : current.makeMove(round, p, p.motion());
        return current;
    }

    public final Turn prev;
    public final int round;
    public final Player player;
    public final Move move;
    public final Outcome outcome;

    private Turn(Turn prev, int round, Player player, Move move, Outcome outcome) {
        this.prev = prev;
        this.round = round;
        this.player = player;
        this.move = move;
        this.outcome = outcome;
    }

    public Turn makeMove(int round, Player player, Move move) {
        return new Turn(this, round, player, move, Outcome.NEUTRAL);
    }

    /**
     * When finishing, the move ist not saved, because a finished player doesn't block the position on map.
     */
    public Turn finish(int round, Player player) {
        return new Turn(this, round, player, null, Outcome.WIN);
    }

    public Turn lost(int round, Player player) {
        return new Turn(this, round, player, null, Outcome.LOSS);
    }

    /**
     * When blocked, could no move according to scouted route. In context of gts it's unknown where the
     * actual move is placed.
     */
    public Turn block(int round, Player player) {
        return new Turn(this, round, player, null, Outcome.BLOCKED);
    }

    public boolean isStart() {
        return prevRound().findAny().isEmpty();
    }

    private Optional<Turn> moved(Player p) {
        return round().filter(t -> t.player.equals(p)).findAny();
    }

    public Stream<Turn> round() {
        return Stream.concat(Stream.of(this), prev != null && prev.round == round ? prev.round() : Stream.empty());
    }

    public Stream<Turn> prevRound() {
        return prev == null ? Stream.empty() : (prev.round != round ? prev.round() : prev.prevRound()); //TODO really?
    }

    public Optional<Move> prevMove(Player p) {
        return prevRound().filter(t -> p.equals(t.player) && t.move != null).map(t -> t.move).findAny();
    }

    private Stream<Turn> progression() {
        return Stream.concat(Stream.of(this), prev != null ? prev.progression() : Stream.empty());
    }

    public boolean isBlocked(Player p) {
        return progression().anyMatch(t -> t.player == p && (t.outcome == Outcome.BLOCKED || t.outcome == Outcome.LOSS));
    }

    public boolean isFinished() {
        return prevRound().anyMatch(t -> t.outcome == Outcome.WIN);
    }

    public long activePlayers() {
        return round().filter(t -> t.outcome != Outcome.BLOCKED).count();
    }

    public Collection<Player> haveMovedInRound(Collection<Player> players) {
        List<Turn> currentRound = round().toList();
        return players.stream().filter(p -> currentRound.stream().anyMatch(t -> t.player.id() == p.id() && t.outcome == Outcome.NEUTRAL)).toList();
    }

    public Collection<Player> haveNotMovedInRound(Collection<Player> players) {
        List<Turn> prevRound = prevRound().toList();
        List<Turn> currentRound = round().toList();
        return players.stream().filter(p -> (prevRound.isEmpty() || prevRound.stream().anyMatch(t -> t.player.id() == p.id() && t.outcome == Outcome.NEUTRAL))
                && currentRound.stream().noneMatch(t -> t.player.id() == p.id())).toList();
    }

    public Collection<Move> unblocked(Collection<Move> moves) {
        Collection<Move> occupied = round().filter(t -> t.move != null).map(t -> t.move).toList();
        return occupied.isEmpty() ? moves : moves.stream().filter(m -> m.isUnblocked(occupied)).toList();
    }

    public Collection<Move> followUp(Player p, Collection<Move> moves) {
        Optional<Turn> turnthisRound = moved(p);
        Move prev = turnthisRound.isPresent() ? turnthisRound.get().move : (prevMove(p).orElse(null));
        return moves.stream().filter(m -> m.isPred(prev)).toList();
    }

    public String lookupKey() {
        return ROUND_FORMAT.format(round) + ": " + round().sorted().map(Turn::toString).collect(Collectors.joining(","));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Turn t && player.equals(t.player) && move != null && move.equals(t.move);
    }

    @Override
    public String toString() {
        return "Dummy".equals(player.name()) ? "" : player + " " + (move != null ? move.toString() : outcome);
    }

    @Override
    public int compareTo(Turn o) {
        return player.id() - o.player.id();
    }

    public enum Outcome {
        WIN, BLOCKED, LOSS, NEUTRAL
    }

}
