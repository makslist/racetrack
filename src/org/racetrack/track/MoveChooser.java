package org.racetrack.track;

import java.util.concurrent.*;
import java.util.logging.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.*;
import org.eclipse.collections.impl.factory.*;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public class MoveChooser implements Callable<GameAction> {

  protected static final Logger logger = Logger.getLogger(MoveChooser.class.toString());

  private Game game;
  private Player player;

  private MutableMap<Player, Paths> paths = Maps.mutable.empty();

  private int minThreads = Integer.min(Settings.getInstance().getInt(Property.maxParallelTourThreads),
      Runtime.getRuntime().availableProcessors() - 1);
  private int maxThreads = Integer.max(minThreads, 1);

  public MoveChooser(Game game) {
    this.game = game;
    player = game.getNext();
  }

  @Override
  public GameAction call() {
    if (quitGame())
      return GameAction.quitGame(game);

    if (player.getPossibles().isEmpty()) {
      System.out.println(game.getId() + " Crash.");
      return new GameAction(game, null, null);
    }

    int round = game.getCurrentRound();

    MutableList<Player> actualPlayers = game.isStarted() ? game.getNearestPlayers(player, 4, 4)
        : (game.getMap().isCpClustered(MapTile.START) && game.getActivePlayersCount() <= 5 ? game.getActivePlayers()
            : Lists.mutable.with(player));
    System.out.println(game.getId() + " " + game.getName() + " " + actualPlayers);

    GameRule rule = RuleFactory.getInstance(game);

    MutableMap<Player, Future<Paths>> futurePaths = Maps.mutable.empty();
    ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);
    CompletionService<Paths> pathService = new ExecutorCompletionService<>(threadPool);
    TSP tsp = new TSP(game, rule);
    for (Player pl : actualPlayers) {
      futurePaths.put(pl, pathService.submit(new PathFinder(game, pl, rule, tsp)));
    }
    try {
      for (Player pl : actualPlayers) {
        Paths path = futurePaths.get(pl).get();
        paths.put(pl, path);
      }
    } catch (InterruptedException | ExecutionException e) {
      logger.warning(e.getMessage());
    }
    threadPool.shutdownNow();

    // remove reference to rule class to enabled freeing memory
    rule = null;

    Paths playerPaths = paths.get(player);
    if (playerPaths.isEmpty()) {
      MutableList<Move> nexts = player.getPossibles();
      return new GameAction(game, nexts.get(0), null);
    }
    MutableList<Move> playerMoves = playerPaths.getMovesOfRound(round);

    if (playerMoves.size() == 1) {
      System.out.println(game.getId() + " Result: " + playerMoves.getFirst() + " with only one move.");
      return new GameAction(game, playerMoves.getFirst(), playerPaths.getComment());
    }

    if (actualPlayers.size() == 1) {
      Move maxSucc = playerPaths.getMovesOfRound(round).max((o1, o2) -> playerPaths.getSuccessors(round + 1, o1).size()
          - playerPaths.getSuccessors(round + 1, o2).size());
      System.out.println(game.getId() + " Result: " + maxSucc + " with only one player.");
      return new GameAction(game, maxSucc, playerPaths.getComment());
    }

    GTS gts = new GTS(game, paths, round);
    return gts.call();
  }

  private boolean quitGame() {
    if (game.getMap().getSetting().isQuit())
      return true;
    if (game.isCrashAllowed() && game.getZzz() >= 6)
      return true;
    if (game.isCrashAllowed() && game.isWithIq())
      return true;

    return false;
  }

}
