package org.racetrack.track;

import java.time.*;
import java.time.temporal.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.*;
import org.eclipse.collections.impl.factory.*;
import org.racetrack.concurrent.*;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.rules.special.*;
import org.racetrack.worker.*;

public class MoveChooser extends ComparableTask<GameAction> {

  protected static final Logger logger = Logger.getLogger(MoveChooser.class.toString());

  private Game game;
  private Player player;
  private LocalDateTime executionEnd;
  private int maxExecutionTimeMinutes;

  private MutableMap<Player, Paths> paths = Maps.mutable.empty();

  public MoveChooser(Game game, User user, int maxExecutionTimeMinutes) {
    this.game = game;
    player = game.getPlayer(user);
    this.maxExecutionTimeMinutes = maxExecutionTimeMinutes;
  }

  @Override
  public GameAction call() {
    Thread.currentThread().setName("MoveChooser");
    game.update();
    if (!game.isNextPlayer(player))
      return GameAction.notNext(game);

    if (quitGame())
      return GameAction.quitGame(game);

    ConsoleOutput.println(game.getId(), ("Map " + game.getMap().getId() + " - "
        + (game.getName().length() > 80 ? game.getName().substring(0, 80) : game.getName())));

    executionEnd = LocalDateTime.now().plusMinutes(maxExecutionTimeMinutes);

    GameRule rule = RuleFactory.getInstance(game);
    MutableList<Move> possibles = rule.filterMoves(player.getPossibles());
    if (player.getNextMoves().isEmpty() || possibles.isEmpty())
      return GameAction.crash(game);

    if (possibles.size() == 1) {
      ConsoleOutput.println(game.getId(), "Result: " + possibles.getFirst() + " with only one possible.");
      return new GameAction(game, possibles.getFirst(), false, "");
    }

    long duration = System.currentTimeMillis();
    int round = game.getCurrentRound();

    ExecutorService executor = Executors.newFixedThreadPool(Settings.getInstance().maxParallelTourThreads());
    TSP tsp = new TSP(game, rule);
    Future<Paths> playerPathsFuture = executor.submit(new PathFinder(game, player, rule, tsp));
    try {
      paths.put(player, playerPathsFuture.get(timeLeftForExecutionSeconds(), TimeUnit.SECONDS));
    } catch (ExecutionException e) {
      logger.warning(e.getMessage());
      return GameAction.skipGame(game, "Exception when executing path finder. " + e.getMessage());
    } catch (TimeoutException | InterruptedException e) {
      playerPathsFuture.cancel(true);
      return GameAction.skipGame(game, "Timeout or interrupt when getting path finder results.");
    } finally {
      executor.shutdownNow();
    }
    Paths playerPaths = paths.get(player);
    if (playerPaths.isEmpty())
      return GameAction.skipGame(game, "Path for player " + player.getName() + " is empty");

    MutableList<Move> playerMoves = playerPaths.getMovesOfRound(round);
    if (playerMoves.isEmpty())
      return GameAction.skipGame(game, "No element found when getting max of playermoves");

    if (playerMoves.size() == 1) {
      ConsoleOutput.println(game.getId(), "Result: " + playerMoves.getFirst() + " with only one move. Duration "
          + ((System.currentTimeMillis() - duration) / 1000) + "s");
      return new GameAction(game, playerMoves.getFirst(), playerPaths.getMinLength() == 1, playerPaths.getComment());
    }

    MutableList<Move> filteredStartMoves = rule.filterStartMoves(possibles, playerMoves, round);
    if (!filteredStartMoves.equals(playerMoves))
      return new GameAction(game, filteredStartMoves.getFirst(), playerPaths.getMinLength() == 1,
          playerPaths.getComment());

    MutableList<Player> opponents = game.isStarted() ? game.getNearestPlayers(player, 3, 4)
        : (game.getMap().isCpClustered(MapTile.START) && game.getActivePlayersCount() <= 5
            ? game.getActivePlayers().reject(p -> player.equals(p))
            : Lists.mutable.empty());

    if (opponents.isEmpty()) {
      Move maxSucc = playerMoves.max((o1, o2) -> playerPaths.getSuccessors(round + 1, o1).size()
          - playerPaths.getSuccessors(round + 1, o2).size());
      ConsoleOutput.println(game.getId(), "Result: " + maxSucc + " with only one player. Duration "
          + ((System.currentTimeMillis() - duration) / 1000) + "s");
      return new GameAction(game, maxSucc, playerPaths.getMinLength() == 1, playerPaths.getComment());
    } else {
      executor = Executors.newFixedThreadPool(Settings.getInstance().maxParallelTourThreads());
      CompletionService<Paths> completionService = new ExecutorCompletionService<>(executor);
      MutableMap<Player, Future<Paths>> futurePaths = Maps.mutable.empty();
      for (Player pl : opponents) {
        futurePaths.put(pl, completionService.submit(new PathFinder(game, pl, rule, tsp)));
      }

      executor.shutdown();
      try {
        if (!executor.awaitTermination(timeLeftForExecutionSeconds(), TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException ie) {
        executor.shutdownNow();
        return GameAction.skipGame(game, "MoveChooser interrupted");
      }
      for (Player pl : futurePaths.keySet()) {
        Future<Paths> future = futurePaths.get(pl);
        try {
          paths.put(pl, future.get());
        } catch (ExecutionException e) {
          logger.warning(e.getMessage());
          return GameAction.skipGame(game, "Exception when executing path finder");
        } catch (InterruptedException e) {
          return GameAction.skipGame(game, "Timeout when getting path finder results");
        }
      }
    }

    executor = Executors.newFixedThreadPool(1);
    Future<GameAction> action = executor.submit(new GTS(game, paths, round));
    try {
      return action.get(timeLeftForExecutionSeconds(), TimeUnit.SECONDS);
    } catch (ExecutionException ee) {
      return GameAction.skipGame(game, "Exception when getting game tree search result: " + ee.getMessage());
    } catch (InterruptedException | TimeoutException e) {
      return GameAction.skipGame(game, "Timeout during game tree search.");
    } finally {
      action.cancel(true);
      executor.shutdownNow();
      ConsoleOutput.println(game.getId(), ((System.currentTimeMillis() - duration) / 1000) + "s to calculate.");
    }
  }

  private long timeLeftForExecutionSeconds() {
    long timeLeft = ChronoUnit.SECONDS.between(LocalDateTime.now(), executionEnd);
    return timeLeft > 0 ? timeLeft : 0l;
  }

  private boolean quitGame() {
    if (game.isStartedBy(player))
      return false;
    if (game.getCurrentRound() > 2)
      return false;
    if (game.getMap().getSetting().isQuit())
      return true;
    if (game.getName().contains(CraZZZyRule.TITLE))
      return false;
    if (game.isCrashAllowed() && game.getZzz() >= 6)
      return true;
    if (game.isCrashAllowed() && game.isWithIq())
      return true;

    return false;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof MoveChooser && game.equals(((MoveChooser) obj).game);
  }

  @Override
  public int compareTo(ComparableTask<GameAction> o) {
    if (!(o instanceof MoveChooser))
      return 1;

    MoveChooser mc2 = (MoveChooser) o;
    int crash = (game.isCrashAllowed() ? 1 : 0) - (mc2.game.isCrashAllowed() ? 1 : 0);
    if (crash != 0)
      return crash;

    int zzz = game.getZzz() - mc2.game.getZzz();
    if (zzz != 0)
      return zzz;

    int missingCps = player.getMissingCps().size() - mc2.player.getMissingCps().size();
    if (missingCps != 0)
      return missingCps;

    return game.getMap().getCountDrivableTiles() - mc2.game.getMap().getCountDrivableTiles();
  }

}
