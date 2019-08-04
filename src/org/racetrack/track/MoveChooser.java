package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.*;
import org.eclipse.collections.impl.factory.*;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.rules.special.*;

public class MoveChooser implements Callable<GameAction> {

  protected static final Logger logger = Logger.getLogger(MoveChooser.class.toString());

  private Game game;
  private Player player;

  private MutableMap<Player, Paths> paths = Maps.mutable.empty();

  public MoveChooser(Game game, User user) {
    this.game = game;
    player = game.getPlayer(user);
  }

  @Override
  public GameAction call() {
    Thread.currentThread().setName("MoveChooserThread");
    game.update();
    if (!game.isNextPlayer(player))
      return GameAction.notNext(game);

    if (quitGame())
      return GameAction.quitGame(game);

    if (player.getPossibles().isEmpty()) {
      System.out.println(game.getId() + " Crash.");
      return new GameAction(game, null, null);
    }

    long duration = System.currentTimeMillis();
    int round = game.getCurrentRound();

    MutableList<Player> actualPlayers = game.isStarted() ? game.getNearestPlayers(player, 4, 4)
        : (game.getMap().isCpClustered(MapTile.START) && game.getActivePlayersCount() <= 5 ? game.getActivePlayers()
            : Lists.mutable.with(player));
    System.out.println(game.getId() + " " + game.getName() + " " + actualPlayers);

    GameRule rule = RuleFactory.getInstance(game);

    MutableMap<Player, Future<Paths>> futurePaths = Maps.mutable.empty();
    ExecutorService executorService = Executors.newFixedThreadPool(Settings.getInstance().getMaxParallelTourThreads());
    CompletionService<Paths> pathService = new ExecutorCompletionService<>(executorService);
    TSP tsp = new TSP(game, rule);
    for (Player pl : actualPlayers) {
      futurePaths.put(pl, pathService.submit(new PathFinder(game, pl, rule, tsp)));
    }

    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(Settings.getInstance().maxExecutionTimeMinutes(), TimeUnit.MINUTES)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
      return GameAction.skipGame(game);
    }
    for (Player pl : actualPlayers) {
      Future<Paths> future = futurePaths.get(pl);
      try {
        paths.put(pl, future.get());
      } catch (InterruptedException | ExecutionException e) {
        logger.warning(e.getMessage());
      }
    }

    // references not needed anymore
    rule = null;
    tsp = null;

    Paths playerPaths = paths.get(player);
    if (playerPaths.isEmpty())
      return GameAction.skipGame(game);
    MutableList<Move> playerMoves = playerPaths.getMovesOfRound(round);

    if (playerMoves.size() == 1) {
      System.out.println(game.getId() + " Result: " + playerMoves.getFirst() + " with only one move. Duration "
          + ((System.currentTimeMillis() - duration) / 1000) + "s");
      return new GameAction(game, playerMoves.getFirst(), playerPaths.getComment());
    }

    if (actualPlayers.size() == 1) {
      try {
        Move maxSucc = playerMoves.max((o1, o2) -> playerPaths.getSuccessors(round + 1, o1).size()
            - playerPaths.getSuccessors(round + 1, o2).size());
        System.out.println(game.getId() + " Result: " + maxSucc + " with only one player. Duration "
            + ((System.currentTimeMillis() - duration) / 1000) + "s");
        return new GameAction(game, maxSucc, playerPaths.getComment());
      } catch (NoSuchElementException nsee) {
        return GameAction.skipGame(game);
      }
    }

    GTS gts = new GTS(game, paths, round);
    GameAction action = gts.call();

    System.out.println(game.getId() + " " + ((System.currentTimeMillis() - duration) / 1000) + "s to calculate.");
    return action;
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

}
