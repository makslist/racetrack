package de.karopapier.kabotte.track;

import de.karopapier.api.Move;
import org.eclipse.collections.api.block.function.Function0;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Paths {

    private final MutableIntObjectMap<Collection<Move>> roundIdx = new IntObjectHashMap<>(63);
    private final Collection<Move> moves = new ConcurrentLinkedQueue<>();
    private String comment = "";

    private Paths() {
    }

    public Paths(Collection<Move> moves) {
        this.moves.addAll(moves);
    }

    public static Paths empty() {
        return new Paths();
    }

    /**
     * Returns the moves of the previous round
     *
     * @param moves the reference moves from a single level
     */
    public static Collection<Move> getPrevRound(Collection<Move> moves) {
        return moves.stream().filter(m -> !m.isCrash())
                .flatMap(m -> m.getPreds().flatMap(p -> p != null ? (p.isCrash() ? p.getPreds() : Stream.of(p)) : Stream.empty())).distinct().toList();
    }

    public Paths copy() {
        Paths path = new Paths(moves);
        path.comment = comment;
        return path;
    }

    public boolean add(Move move) {
        return moves.add(move);
    }

    public Paths clear() {
        moves.clear();// = new ArrayList<>();
        return this;
    }

    public void merge(Paths paths) {
        if (comment.isEmpty())
            comment = paths.comment;

        if (paths.isEmpty())
            return;

        if (!roundIdx.isEmpty())
            throw new RuntimeException("Modify after indexing!"); //TODO

        if (!isEmpty() & moves.addAll(paths.getEndMoves())) { // no short circuit eval
            int minLen = length();
            moves.removeIf(m -> m.round > minLen);

            List<List<Move>> dups = moves.stream().collect(Collectors.groupingBy(Move::hashKey)).values().stream().filter(d -> d.size() > 1).toList();
            for (List<Move> dup : dups) {
                dup.get(0).mergePreds(dup);
                dup.stream().skip(1).forEach(d -> moves.removeIf(m -> m == d));
            }

            Collection<Move> level = moves;
            while (!level.isEmpty())
                level = getPrevRound(zip(level));
        }
    }

    private Collection<Move> zip(Collection<Move> moves) { // TODO test for concurrent modification
        List<List<Move>> listdups = moves.stream().flatMap(Move::getPreds)
                .collect(Collectors.groupingBy(Move::hashKey)).values().stream().filter(d -> d.size() > 1).toList();
        for (List<Move> dup : listdups) {
            Move first = dup.get(0).mergePreds(dup);
            dup.stream().skip(1).forEach(p -> moves.forEach(m -> m.replacePred(p, first)));
        }
        return moves;
    }

    public Paths filter(Predicate<Move> rule) {
        moves.removeIf(m -> !rule.test(m));
        return this;
    }

    public Collection<Move> getEndMoves() {
        return moves;
    }

    public boolean isEmpty() {
        return moves.isEmpty();
    }

    public int length() {
        return moves.isEmpty() ? 0 : moves.stream().mapToInt(m -> m.round).min().orElse(0);
    }

    public Collection<Move> movesInRound(int round) {
        if (roundIdx.containsKey(round))
            return roundIdx.get(round);
        if (moves.isEmpty())
            return List.of();

        int currentRound = moves.stream().findFirst().get().round;//get(0).round + 1;
        Collection<Move> curLevel = moves;
        while (!curLevel.isEmpty()) {
            roundIdx.put(currentRound--, curLevel);
            curLevel = curLevel.stream().flatMap(Move::nonCrashPreds).distinct().toList();
        }

        return roundIdx.getIfAbsent(round, (Function0<List<Move>>) List::of);
    }

    public Stream<Move> movesIn(int round) {
        return movesInRound(round).stream();
    }

    public List<Move> getFollowUp(int level, Move predecessor) {
        if (predecessor == null)
            return Collections.emptyList();
        return movesIn(level).filter(m -> m.isPred(predecessor)).toList();
    }

    public String getComment() {
        return comment;
    }

    public Paths addComment(String comment) {
        this.comment = comment;
        return this;
    }

    @Override
    public String toString() {
        return moves.toString();
    }

}
