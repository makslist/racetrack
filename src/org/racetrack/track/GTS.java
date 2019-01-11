package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.track.Strategy.*;

public class GTS implements Callable<GameAction> {

  class Pair<K, V> {

    K key;
    V value;

    private Pair(K key, V value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o instanceof Pair<?, ?>)
        return this.key.equals(((Pair<?, ?>) o).key) && this.value.equals(((Pair<?, ?>) o).value);
      return false;
    }

    @Override
    public String toString() {
      return key + "(" + value + ")";
    }

  }

  private class GameState {

    private MutableList<Pair<Player, Move>> moves = new FastList<>();

    private GameState() {
    }

    private GameState(MutableList<Pair<Player, Move>> moves) {
      this.moves = moves.clone();
    }

    private boolean isTaken(Move move) {
      return moves.anySatisfy(m -> m.value.equalsPos(move));
    }

    private GameState with(Player player, Move move) {
      moves.with(new Pair<Player, Move>(player, move));
      return this;
    }

    private GameState without(Player player) {
      moves.removeIf(m -> m.key.equals(player));
      return this;
    }

    private Move getMove(Player player) {
      MutableList<Pair<Player, Move>> movesOfPlayer = moves.select(pm -> pm.key.equals(player));
      return movesOfPlayer.isEmpty() ? null : movesOfPlayer.getFirst().value;
    }

    private MutableList<Player> getPlayers() {
      return moves.collect(pl -> pl.key);
    }

    @Override
    public GameState clone() {
      return new GameState(moves);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof GameState))
        return false;

      GameState c = (GameState) o;
      return c.moves.equals(moves);
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
    }

    public boolean isEmpty() {
      return moves.isEmpty();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (Pair<Player, Move> move : moves.sortThis((p1, p2) -> p1.key.getName().compareTo(p2.key.getName()))) {
        if (sb.length() != 0) {
          sb.append(" ");
        }
        sb.append(move);
      }
      return sb.toString();
    }

  }

  protected static final Logger logger = Logger.getLogger(GTS.class.toString());

  private Game game;
  private Player player;

  private MutableMap<Player, Paths> paths = Maps.mutable.empty();
  private ConcurrentMutableMap<Integer, Evaluation> evaluations = new ConcurrentHashMap<>();
  private AtomicInteger playoutCount = new AtomicInteger();
  private Random random = new Random();

  private int minThreads = Integer.min(Settings.getInstance().getInt(Property.maxParallelTourThreads),
      Runtime.getRuntime().availableProcessors() - 1);
  private int maxThreads = Integer.max(minThreads, 1);

  private Strategy strategy;
  private int gameLength = Integer.MIN_VALUE;
  private int maxDepth = Integer.MAX_VALUE;

  public GTS(Game game) {
    this.game = game;
    player = game.getNext();
  }

  @Override
  public GameAction call() {
    if (game.getMap().getSetting().isQuit())
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
    ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);
    CompletionService<Paths> pathService = new ExecutorCompletionService<>(threadPool);
    TSP tsp = new TSP(game, rule);
    for (Player pl : actualPlayers) {
      futurePaths.put(pl, pathService.submit(new PathFinder(game, pl, rule, tsp)));
    }
    MutableList<Pair<Player, Integer>> playerLength = Lists.mutable.empty();
    try {
      for (Player pl : actualPlayers) {
        Paths path = futurePaths.get(pl).get();
        int len = !path.isEmpty() ? path.getMinTotalLength() : Integer.MIN_VALUE;
        paths.put(pl, path);
        playerLength.add(new Pair<Player, Integer>(pl, len));
        if (len > gameLength) {
          gameLength = len;
        }
      }
    } catch (InterruptedException | ExecutionException e) {
      logger.warning(e.getMessage());
    }
    threadPool.shutdownNow();

    // remove reference to rule class to enabled freeing memory
    rule = null;

    Paths playerPaths = paths.get(player);
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

    strategy = Strategy.get(player, playerLength);

    maxDepth = calcMaxNDepth(actualPlayers, 2000000, gameLength);

    MutableList<Player> playersAlreadyMoved = actualPlayers.select(p -> p.hasMovedInRound(round));
    MutableList<Player> playersNotYetMoved = actualPlayers.reject(p -> p.hasMovedInRound(round));
    GameState currentState = new GameState(playersAlreadyMoved.collect(p -> new Pair<Player, Move>(p, p.getMotion())));

    Move bestMove = play(player, playersNotYetMoved, currentState, round);
    threadPool.shutdownNow();

    System.out.println(game.getId() + " Result: " + bestMove + " with strategy : " + strategy);
    duration = (System.currentTimeMillis() - duration) / 1000;
    System.out.println(game.getId() + " " + duration + "s to calculate with " + evaluations.size() + " nodes and "
        + playoutCount.get() + " playout calls.");

    return new GameAction(game, bestMove, playerPaths.getComment());
  }

  private int calcMaxNDepth(MutableList<Player> actualPlayers, int maxNodes, int maxRound) {
    for (int i = game.getCurrentRound() - 1; i < maxRound; i++) {
      long nodeCount = 1;
      for (Player player : actualPlayers) {
        nodeCount *= Math.max(paths.get(player).getMovesOfRound(i).size(), 1);
      }
      nodeCount = (long) (nodeCount * Math.pow(actualPlayers.size(), 2));
      if (nodeCount > maxNodes)
        return i - 1;
    }
    return maxRound + 1;
  }

  private Move play(Player player, MutableList<Player> playersToMove, GameState state, int round) {
    Paths path = paths.get(player);
    MutableMap<Evaluation, Move> moveRatings = Maps.mutable.empty();

    System.out.print(game.getId() + " Ratings:");
    for (Move move : path.getMovesOfRound(round).reject(m -> state.isTaken(m))) {
      Evaluation eval = play(playersToMove.reject(p -> p.equals(player)), state.with(player, move), round, null);
      System.out.print(" " + move + " " + eval);
      moveRatings.put(eval, move);
      state.without(player);
    }
    System.out.println("");

    FastList<Evaluation> evals = new FastList<>(moveRatings.keySet());
    evals.sortThis((e1, e2) -> path.getSuccessors(round + 1, moveRatings.get(e2)).size()
        - path.getSuccessors(round + 1, moveRatings.get(e1)).size());
    return moveRatings.get(strategy.evaluate(player, evals));
  }

  private Evaluation play(MutableList<Player> playersToMove, GameState state, int round, GameState lastRound) {
    if (playersToMove.isEmpty())
      return playNextRound(state, round);

    MutableList<Evaluation> evals = Lists.mutable.empty();
    for (Player pl : playersToMove) {
      Paths path = paths.get(pl);
      MutableList<Move> moves = lastRound != null ? path.getSuccessors(round, lastRound.getMove(pl))
          : path.getMovesOfRound(round);

      MutableList<Evaluation> playerEvals = Lists.mutable.empty();
      if (moves.isEmpty()) { // GAME END
        Evaluation eval = play(playersToMove.reject(p -> p.equals(pl)), state, round, lastRound);
        playerEvals.add(strategy.finish(eval, pl, round));
      } else {
        MutableList<Move> movesNonBlocked = moves.reject(m -> state.isTaken(m));
        if (movesNonBlocked.isEmpty()) { // BLOCK
          Evaluation eval = play(playersToMove.reject(p -> p.equals(pl)), state, round, lastRound);
          playerEvals.add(strategy.block(eval, pl, round));
        } else {

          if (playersToMove.size() == 1) {
            @SuppressWarnings("serial")
            Collection<RecursiveTask<Evaluation>> results = ForkJoinTask
                .invokeAll(movesNonBlocked.collect(move -> new RecursiveTask<Evaluation>() {
                  @Override
                  protected Evaluation compute() {
                    return playNextRound(state.clone().with(pl, move), round);
                  }
                }));
            playerEvals.add(strategy.evaluate(pl, new FastList<>(results).collect(t -> {
              try {
                return t.get();
              } catch (InterruptedException | ExecutionException e) {
                logger.warning(e.getMessage());
              }
              return null;
            })));
          } else {
            for (Move move : movesNonBlocked) {
              playerEvals.add(play(playersToMove.reject(p -> p.equals(pl)), state.with(pl, move), round, lastRound));
              state.without(pl);
            }
          }

        }
      }
      evals.add(strategy.evaluate(pl, playerEvals));
    }
    return strategy.merge(evals);
  }

  public Evaluation playNextRound(GameState state, int round) {
    if (evaluations.containsKey(state.hashCode()))
      return evaluations.get(state.hashCode());

    if (state.getPlayers().isEmpty())
      return strategy.initEval();

    Evaluation eval;
    if (round <= maxDepth) {
      eval = play(state.getPlayers(), new GameState(), round + 1, state);
    } else {
      playoutCount.incrementAndGet();
      eval = playOut(state.getPlayers(), new GameState(), round + 1, state);
    }
    evaluations.put(state.hashCode(), eval);
    return eval;
  }

  private Evaluation playOut(MutableList<Player> playersToMove, GameState state, int round, GameState lastRound) {
    if (playersToMove.isEmpty())
      return lastRound.isEmpty() ? strategy.initEval() : playOut(state.getPlayers(), new GameState(), round + 1, state);

    Player pl = playersToMove.get(random.nextInt(playersToMove.size()));
    Paths path = paths.get(pl);
    MutableList<Move> moves = lastRound != null ? path.getSuccessors(round, lastRound.getMove(pl))
        : path.getMovesOfRound(round);

    if (moves.isEmpty()) { // GAME END
      Evaluation eval = playOut(playersToMove.reject(p -> p.equals(pl)), state, round, lastRound);
      return strategy.finish(eval, pl, round);
    } else {
      MutableList<Move> movesNonBlocked = moves.reject(m -> state.isTaken(m));
      if (movesNonBlocked.isEmpty()) { // BLOCK
        Evaluation eval = playOut(playersToMove.reject(p -> p.equals(pl)), state, round, lastRound);
        return strategy.block(eval, pl, round);
      } else {
        Move move = movesNonBlocked.get(random.nextInt(movesNonBlocked.size()));
        return playOut(playersToMove.reject(p -> p.equals(pl)), state.with(pl, move), round, lastRound);
      }
    }
  }

}
