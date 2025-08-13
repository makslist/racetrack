package de.karopapier.kabotte.track;

import de.karopapier.api.Game;
import de.karopapier.api.Move;
import de.karopapier.api.Player;
import de.karopapier.kabotte.config.Settings;
import de.karopapier.kabotte.track.Strategy.Score;
import de.karopapier.kabotte.utils.SeqLock;
import de.karopapier.kabotte.worker.KaBotteConsole;

import java.io.Serial;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;

public class GTS implements Callable<List<GTS.WeightedMove>> {

    private KaBotteConsole console;

    private final Game game;
    private final int round;

    private final Map<Player, Paths> paths;
    private final Map<String, Score> transposition = new HashMap<>(2 << 18);
    private final int maxPlayerCount;
    private final int gtsTranspositionRoundLimit;
    private final int finishingRound;
    private final Map<Player, Integer> playerFinishRound = new LinkedHashMap<>();
    private final Player player;
    private final Collection<Player> players;
    private int maxTreeDepthLookAhead;
    private final Strategy strategy;
    private final Score maxDepthScore;

    private final ReadWriteLock scoreLock = new SeqLock(false);

    public GTS(Game game, Map<Player, Paths> paths, Settings setting) {
        this.game = game;
        this.paths = paths;
        this.round = game.currentRound();

        finishingRound = paths.values().stream().filter(p -> !p.isEmpty()).mapToInt(Paths::length).min().orElse(0);
        paths.forEach((pl, path) -> playerFinishRound.put(pl, path.length()));

        this.maxPlayerCount = setting.gtsMaxPlayerCount();
        this.gtsTranspositionRoundLimit = setting.gtsTranspositionRoundLimit();

        player = game.next();
        players = paths.keySet();
        strategy = Strategy.get(player, paths);
        maxDepthScore = strategy.maxDepth();
    }

    public void setConsole(KaBotteConsole console) {
        this.console = console;
    }

    @Override
    public List<WeightedMove> call() {

        List<Map.Entry<Player, Paths>> kickPlayer = paths.entrySet().stream().filter(e -> !e.getKey().equals(player)) //TODO kick farestPlayers
                .sorted((e1, e2) -> e1.getValue().movesInRound(round).size() - e2.getValue().movesInRound(round).size()).skip(maxPlayerCount).toList();
        kickPlayer.forEach(k -> paths.remove(k.getKey()));

        maxTreeDepthLookAhead = this.round;
        int maxRounds = paths.values().stream().filter(p -> !p.isEmpty()).mapToInt(Paths::length).max().orElse(0) + 1;
        for (int i = this.round; i <= maxRounds; ++i) {
            int finalI = i;
            long transpositions = players.size() * paths.values().stream().mapToLong((v) -> (long) v.movesInRound(finalI).size()).reduce(1L, (t, u) -> t * u);
            if (transpositions < gtsTranspositionRoundLimit && i < round + 16)
                maxTreeDepthLookAhead = i;
            else
                break;
        }

        if (console != null)
            console.info(game.id(), "Starting GTS with strategy " + strategy.toString() + ", tree depth lookahead " + maxTreeDepthLookAhead);

        List<WeightedMove> moveScores = playGameAs(player);
        moveScores.sort(strategy.maxScore(player, (p, m) -> paths.get(p).getFollowUp(round + 1, m).size()));
        return moveScores;
    }

    private List<WeightedMove> playGameAs(Player player) {
        final Collection<Move> movesInRound = paths.get(player).movesInRound(round);
        final Turn roundState = Turn.getStartState(round, playerFinishRound);

        boolean lastInRound = roundState.haveNotMovedInRound(players).size() == 1;

        List<WeightedMove> moveScores = new ArrayList<>();
        ForkJoinPool executor = new ForkJoinPool();
        try {
            roundState.unblocked(movesInRound).forEach(m -> {
                Turn turn = round == playerFinishRound.get(player) ? roundState.finish(round, player) : roundState.makeMove(round, player, m);
                moveScores.add(new WeightedMove(m,
                        (lastInRound ? executor.submit(nextRound(turn)) : executor.submit(() -> nextTurn(turn, false))).fork().join()));
            });
            executor.shutdown();
            if (!executor.awaitTermination(600, TimeUnit.SECONDS)) //TODO settings
                return moveScores;
        } catch (InterruptedException ignored) {
            if (console != null)
                console.warning(game.id(), "Timeout in playGameAs!");
        }
        return moveScores;
    }

    private Score nextTurn(Turn rnd, boolean nextRound) {
        List<Score> scores = new ArrayList<>();

        int round = rnd.round + (nextRound ? 1 : 0);
        Collection<Player> notMovedYet = nextRound ? rnd.haveMovedInRound(players) : rnd.haveNotMovedInRound(players);
        for (Player p : notMovedYet) {
            Collection<Move> roundMoves = paths.get(p).movesInRound(round);

            boolean lastInRound = notMovedYet.size() == 1;
            if (round == finishingRound && round == playerFinishRound.get(p)) { // player has already finished the game one round before
                scores.add(lastInRound ? nextRound(rnd.finish(round, p)).fork().join() : nextTurn(rnd.finish(round, p), false));
            } else if (round > finishingRound && round == playerFinishRound.get(p)) {
                scores.add(lastInRound ? nextRound(rnd.lost(round, p)).fork().join() : nextTurn(rnd.lost(round, p), false));
            } else {
                Collection<Move> unblocked = rnd.unblocked(nextRound || !rnd.isStart() ? rnd.followUp(p, roundMoves) : roundMoves);
                if (unblocked.isEmpty()) {// blocked player, no forking needed if last in round
                    scores.add(strategy.block(p, lastInRound ? nextRound(rnd.block(round, p)).fork().join() : nextTurn(rnd.block(round, p), false)));
                } else if (lastInRound) { // fork execution after last player makes his move
                    scores.add(strategy.eval(p, unblocked.stream().map(m -> nextRound(rnd.makeMove(round, p, m)).fork().join()).toList()));
                } else {
                    scores.add(strategy.eval(p,
                            unblocked.stream().map(m -> nextTurn(rnd.makeMove(round, p, m), false)).filter(Objects::nonNull).toList()));
                }
            }
        }
        return strategy.merge(scores);
    }

    private RecursiveTask<Score> nextRound(Turn rnd) {
        return new RecursiveTask<>() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected Score compute() {
                String key = rnd.lookupKey();

                SeqLock.SeqReadLock lock = (SeqLock.SeqReadLock) scoreLock.readLock();
                while (true) {
                    long counter = lock.tryReadLock();
                    Score score = transposition.get(key);
                    if (score == null) {
                        if (rnd.round > maxTreeDepthLookAhead || Thread.currentThread().isInterrupted()) {
                            score = maxDepthScore;
                        } else if (rnd.isFinished() || rnd.activePlayers() == 1)
                            score = strategy.gameEnd(rnd);
                        else
                            score = nextTurn(rnd, true);
                        scoreLock.writeLock().lock();
                        Score test = transposition.put(key, score);
                        scoreLock.writeLock().unlock();
                        return score;
                    }
                    if (lock.retryReadLock(counter)) {
                        return score;
                    }
                }
            }
        };
    }

    public record WeightedMove(Move move, Score score) {
        public String toString() {
            return move.toString() + " " + score;
        }
    }

    public Map<String, Score> transposition() {
        return transposition;
    }

    public String getStrat() {
        return strategy.toString();
    }

}
