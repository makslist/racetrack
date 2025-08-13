package de.karopapier.kabotte.track;

import de.karopapier.api.*;
import de.karopapier.api.KaroMap.Tile;
import de.karopapier.kabotte.config.Settings;
import de.karopapier.kabotte.rules.RuleFactory;
import de.karopapier.kabotte.worker.KaBotteConsole;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MoveChooser implements Callable<Action> {

    private static final int MAX_EXECUTION_TIME_DEFAULT_MINUTES = 10;

    private KaBotteConsole console;
    private final int round;
    private final Player player;
    private final Map<Player, Paths> paths = new HashMap<>();
    private final Settings settings;
    private final int maxExecutionTimeMinutes;
    private Game game;

    public MoveChooser(Game game, User user, Settings settings) {
        this.game = game;
        round = game.currentRound();
        player = game.asPlayer(user);
        this.settings = settings;
        maxExecutionTimeMinutes = Integer.min(settings.maxExecutionTimeMinutes(), MAX_EXECUTION_TIME_DEFAULT_MINUTES);
    }

    public void setConsole(KaBotteConsole console) {
        this.console = console;
    }

    @Override
    public Action call() {
        Thread.currentThread().setName("MoveChooser: " + game.id());
        game = game.reload();
        if (!game.isYourTurn(player))
            return Action.notMyTurn(game);

        if (console != null)
            console.info(game.id(), "-- " + "https://www.karopapier.de/spiele/" + game.id() + " -- " +
                    ((game.name().length() > 80 ? game.name().substring(0, 80) : game.name())
                            + " - " + "Map " + game.map().id()));

        Rule rule = RuleFactory.getInstance(game);
        Optional<Boolean> withdraw = rule.withdraw(player);
        if (withdraw.isPresent() && withdraw.get())
            return Action.withdraw(game);

        Collection<Move> possibles = rule.filteredPossibles(player);
        if (possibles.isEmpty() || player.nextMoves(game, rule).isEmpty())
            return Action.crashed(game);

        if (possibles.size() == 1) {
            Move possible = possibles.stream().findFirst().get();
            if (console != null)
                console.info(game.id(), "Result: " + possible + " with only one possible.");
            return new Action(game, List.of(), possible, false, "");
        }

        ExecutorService executor = Executors.newFixedThreadPool(settings.maxParallelTourThreads());
        TSP tsp = new TSP(game, rule);
        PathFinder scout = new PathFinder(game, player, rule, tsp);
        scout.setConsole(console);
        Future<Paths> playerPathsFuture = executor.submit(scout);
        try {
            int min = Integer.min(maxExecutionTimeMinutes, MAX_EXECUTION_TIME_DEFAULT_MINUTES); //TODO
            Paths value = playerPathsFuture.get(min, TimeUnit.MINUTES);
            paths.put(player, value);
        } catch (ExecutionException e) {
            if (console != null)
                console.error(game.id(), "ExecutionException: " + e.getMessage());
            return Action.skipTurn(game, "Exception when executing path finder. " + e.getMessage());
        } catch (TimeoutException | InterruptedException e) {
            return Action.skipTurn(game, "Timeout or interrupt when getting path finder results.");
        } finally {
            executor.shutdownNow();
        }
        Paths playerPath = paths.get(player);
        if (playerPath.isEmpty())
            return Action.skipTurn(game, "Path for player " + player.name() + " is empty. Map was classified as " + (rule.isMapCircuit() ? "circuit" : "non circuit") + ". Potential misclassification.");

        Collection<Move> playerMoves = playerPath.movesInRound(round);
        if (playerMoves.isEmpty())
            return Action.skipTurn(game, "No element found when getting max of player-moves");
        Collection<Move> filteredStartMoves = rule.filteredStartMoves(possibles, playerMoves, round);

        final boolean isFinishingMove = game.currentRound() == playerPath.length() + 1;
        // special rules: starting place
        if (!filteredStartMoves.equals(playerMoves))
            return new Action(game, List.of(), filteredStartMoves.stream().skip(new Random().nextInt(filteredStartMoves.size())).findFirst().orElse(null), isFinishingMove, playerPath.getComment());

        if (playerMoves.size() == 1) {
            Move only = playerMoves.stream().findFirst().get();
            if (console != null)
                console.info(game.id(), "Result: " + only + " with only one move.");
            return new Action(game, List.of(), only, isFinishingMove, playerPath.getComment());
        }

        if (console != null)
            console.info(game.id(), "Player " + player.name() + " pathlength " + (playerPath.length() + 1) + " with moves: " + playerMoves);

        Collection<Player> opponents = game.isStarted() ? game.nearestPlayers(player, 1, 4, rule)
                : (game.map().isClustered(Tile.START) && game.activePlayersCount() <= 5 ? game.opponents(player).toList() : Collections.emptyList());
        if (opponents.isEmpty()) {
            Move maxFollowUps = playerMoves.stream()
                    .max((o1, o2) -> playerPath.getFollowUp(round + 1, o1).size() - playerPath.getFollowUp(round + 1, o2).size())
                    .orElse(null);
            if (console != null)
                console.info(game.id(), "Result: " + maxFollowUps + " with only one player.");
            return new Action(game, List.of(), maxFollowUps, isFinishingMove, playerPath.getComment());
        } else
            try {
                executor = Executors.newFixedThreadPool(settings.maxParallelTourThreads());
                CompletionService<Paths> completionService = new ExecutorCompletionService<>(executor);
                Map<Player, Future<Paths>> futurePaths = new HashMap<>();
                for (Player op : opponents) {
                    PathFinder opScout = new PathFinder(game, op, rule, tsp);
                    opScout.setConsole(console);
                    futurePaths.put(op, completionService.submit(opScout));
                }

                executor.shutdown();
                try {
                    if (!executor.awaitTermination(maxExecutionTimeMinutes, TimeUnit.MINUTES))
                        executor.shutdownNow();
                } catch (InterruptedException ie) {
                    return Action.skipTurn(game, "MoveChooser interrupted");
                }
                for (Player pl : futurePaths.keySet()) {
                    Future<Paths> future = futurePaths.get(pl);
                    try {
                        paths.put(pl, future.get());
                    } catch (ExecutionException e) {
                        if (console != null)
                            console.error(game.id(), "ExecutionException: " + e.getMessage());
                        return Action.skipTurn(game, "Exception when executing path finder: " + e.getMessage());
                    } catch (CancellationException | InterruptedException e) {
                        return Action.skipTurn(game, "Path finder canceled or interrupted.");
                    }
                }
            } finally {
                executor.shutdownNow();
            }

        executor = Executors.newSingleThreadExecutor();
        GTS gts = new GTS(game, paths, settings);
        gts.setConsole(console);
        Future<List<GTS.WeightedMove>> futureAction = executor.submit(gts);
        try {
            List<GTS.WeightedMove> moves = futureAction.get(maxExecutionTimeMinutes, TimeUnit.MINUTES);
            if (console != null)
                console.info(game.id(), moves.toString());
            return new Action(game, moves, moves.get(0).move(), isFinishingMove, playerPath.getComment());
        } catch (ExecutionException e) {
            return Action.skipTurn(game,
                    "Exception when getting game tree search result: " + e.getMessage() + "\n"
                            + Stream.of(e.getStackTrace())
                            .map(s -> s.getClassName() + " " + s.getMethodName() + " " + s.getLineNumber())
                            .collect(Collectors.joining("\n")));
        } catch (InterruptedException | TimeoutException e) {
            return Action.skipTurn(game, "Timeout during game tree search.");
        } finally {
            executor.shutdownNow();
        }
    }

}
