package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.*;
import org.eclipse.collections.api.map.primitive.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.factory.primitive.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.eclipse.collections.impl.map.mutable.primitive.*;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public class GTS implements Callable<GameAction> {

  private static final int SAMPLE_SIZE = 100;

  private class PlayerMove {

    private Player pl;
    private Move move;

    public PlayerMove(Player player, Move move) {
      pl = player;
      this.move = move;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof PlayerMove))
        return false;

      PlayerMove pm = (PlayerMove) o;
      if (!pl.equals(pm.pl))
        return false;

      return move.equals(pm.move);
    }

    @Override
    public String toString() {
      return pl + "(" + move + ")";
    }

  }

  private class GameState {

    private MutableList<PlayerMove> moves = new FastList<>();

    private GameState() {
    }

    private GameState(Player player, Move move) {
      moves.add(new PlayerMove(player, move));
    }

    private GameState(MutableList<PlayerMove> moves) {
      this.moves = moves.clone();
    }

    private boolean isOccupied(Move move) {
      return moves.anySatisfy(pm -> pm.move.equalsPos(move));
    }

    private GameState with(Player player, Move move) {
      moves.with(new PlayerMove(player, move));
      return this;
    }

    private GameState without(Player player) {
      moves.removeIf(m -> m.pl.equals(player));
      return this;
    }

    private Move getMove(Player player) {
      MutableList<PlayerMove> movesOfPlayer = moves.select(pm -> pm.pl.equals(player));
      return movesOfPlayer.isEmpty() ? null : movesOfPlayer.getFirst().move;
    }

    private MutableList<Player> getPlayers() {
      return moves.collect(pl -> pl.pl);
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
      for (PlayerMove move : moves.sortThis((p1, p2) -> p1.pl.getName().compareTo(p2.pl.getName()))) {
        if (sb.length() != 0) {
          sb.append(" ");
        }
        sb.append(move);
      }
      return sb.toString();
    }

  }

  private class LeadingEdge implements Comparable<LeadingEdge> {

    private Player player;
    private int length;

    private LeadingEdge(Player player, int length) {
      this.player = player;
      this.length = length;
    }

    @Override
    public int compareTo(LeadingEdge o) {
      return length - o.length;
    }

    @Override
    public String toString() {
      return player + "/" + length;
    }

  }

  private enum DecisionStrategy {

    MaxN, Paranoid, Offensive;

    public static DecisionStrategy getStrategy(Player player, Player leader, int leadingEdge) {
      if (leader == player) {
        if (leadingEdge >= 1)
          return Paranoid;
        else
          return MaxN;
      } else {
        if (leadingEdge >= 2)
          return Offensive;
        else
          return MaxN;
      }
    }

  }

  public static void main(String[] args) {
    GTS max = new GTS(Game.get(108126), null);
    max.call();
  }

  private Game game;
  private GameRule rule;
  private TSP tsp;
  private Player player;

  private MutableObjectIntMap<Player> players = ObjectIntMaps.mutable.empty();
  private MutableMap<Player, Paths> paths = Maps.mutable.empty();
  private IntObjectHashMap<Evaluation> maxes = new IntObjectHashMap<>();
  private Random random = new Random();

  private int maxThreads = Integer.max(Settings.getInstance().getInt(Property.maxParallelTourThreads), 1);
  private int minThreads = Integer.min(maxThreads, Runtime.getRuntime().availableProcessors());
  private ExecutorService threadPool = Executors.newWorkStealingPool(minThreads);
  private CompletionService<Evaluation> completeSrv = new ExecutorCompletionService<>(threadPool);

  private Player leader;
  private DecisionStrategy strategy;
  private int gameLength = Integer.MIN_VALUE;
  private int maxDepth = Integer.MAX_VALUE;

  private int sampleSize;

  public GTS(Game game, Player player) {
    this.game = game;
    this.player = player != null ? player : this.game.getDranPlayer();

    rule = new GameRule(this.game);
    tsp = new TSP(this.game);
  }

  @Override
  public GameAction call() {
    if (game.getMap().getSetting().isQuit())
      return GameAction.quitGame(game);

    ExecutorService threadPool = Executors.newFixedThreadPool(4);
    CompletionService<Paths> service = new ExecutorCompletionService<>(threadPool);

    int round = game.getCurrentRound();

    MutableList<Player> actualPlayers = game.isStarted() ? game.getNearbyPlayers(player, 3)
        : (game.getMap().isCpClustered(MapTile.START) && game.getActivePlayersCount() <= 5 ? game.getActivePlayers()
            : Lists.mutable.with(player));

    MutableMap<Player, Future<Paths>> futurePaths = Maps.mutable.empty();
    for (Player pl : actualPlayers) {
      futurePaths.put(pl, service.submit(new PathFinder(game, pl, rule, tsp)));
    }
    MutableList<LeadingEdge> playerLength = Lists.mutable.empty();
    try {
      for (Player pl : actualPlayers) {
        Paths path = futurePaths.get(pl).get();
        int len = !path.isEmpty() ? path.getMinTotalLength() : Integer.MIN_VALUE;
        paths.put(pl, path);
        playerLength.add(new LeadingEdge(pl, len));
        if (len > gameLength) {
          gameLength = len;
        }
      }
    } catch (InterruptedException | ExecutionException e) {
    }

    threadPool.shutdown();
    rule = null;

    System.out.println("Game: " + game.getId() + " " + game.getName());
    System.out.println("Players: " + actualPlayers);
    Paths playerPaths = paths.get(player);
    MutableList<Move> playerMoves = playerPaths.getMovesOfRound(round);
    if (playerMoves.size() == 1) {
      System.out.println("Result: " + playerMoves.getFirst());
      return new GameAction(game, playerMoves.getFirst(), playerPaths.getComment(), playerPaths.isCrashAhead());
    }

    if (actualPlayers.size() == 1) {
      System.out.println("Only " + player.getName() + " is playing.");
    }

    strategy = getStrategy(playerLength);
    System.out.println("Selected strategy: " + strategy.name());

    maxDepth = calcMaxNDepth(actualPlayers, 200000, gameLength);
    sampleSize = Math.max(SAMPLE_SIZE / Math.max(gameLength - maxDepth, 1), 2);
    System.out.println("Gamelength: " + gameLength + " / MaxDepth: " + maxDepth + " / Samplesize: " + sampleSize);

    int playerpos = 0;
    for (Player pl : actualPlayers) {
      players.put(pl, playerpos++);
    }

    MutableList<Player> playersAlreadyMoved = actualPlayers.select(p -> p.getMove(round) != null);
    MutableList<Player> playersNotYetMoved = actualPlayers.reject(p -> p.getMove(round) != null);
    GameState currentState = new GameState(playersAlreadyMoved.collect(p -> new PlayerMove(p, p.getLastmove())));
    Move bestMove = play(player, playersNotYetMoved, currentState, round);

    System.out.println("Actual players: " + actualPlayers + " with unique nodes: " + maxes.size());
    System.out.println("Result: " + bestMove);

    return new GameAction(game, bestMove, playerPaths.getComment(), playerPaths.isCrashAhead());
  }

  public DecisionStrategy getStrategy(MutableList<LeadingEdge> playerLength) {
    if (playerLength.size() >= 2) {
      playerLength.sortThis();
      leader = playerLength.get(0).player;
      int leadingEdge = Math.abs(playerLength.get(0).length - playerLength.get(1).length);
      return DecisionStrategy.getStrategy(player, leader, leadingEdge);
    }
    return DecisionStrategy.MaxN;
  }

  public int calcMaxNDepth(MutableList<Player> actualPlayers, int maxNodes, int maxRound) {
    for (int i = game.getCurrentRound() - 1; i < maxRound; i++) {
      long nodeCount = 1;
      for (Player player : actualPlayers) {
        nodeCount *= Math.max(paths.get(player).getMovesOfRound(i).size(), 1);
        if (nodeCount > maxNodes) {
          System.out.println("NodeCount: " + nodeCount);
          return i - 1;
        }
      }
    }
    return maxRound;
  }

  private Move play(Player player, MutableList<Player> playersToMove, GameState state, int round) {
    Paths path = paths.get(player);
    MutableMap<Evaluation, Move> moveRatings = Maps.mutable.empty();
    for (Move move : path.getMovesOfRound(round).reject(m -> state.isOccupied(m))) {
      Evaluation maxn = play(playersToMove.reject(p -> p.equals(player)), state.with(player, move), round, null);
      moveRatings.put(maxn, move);
      System.out.println("Move: " + move + " with rating: " + maxn);
      state.without(player);
    }
    Evaluation max = getEvaluation(player, moveRatings.keySet());
    return moveRatings.get(max);
  }

  private Evaluation play(MutableList<Player> playersToMove, GameState state, int round, GameState lastRound) {
    if (playersToMove.isEmpty())
      return playNextRound(state, round);

    MutableList<Evaluation> innerRoundValues = Lists.mutable.empty();
    for (Player pl : playersToMove) {
      Paths path = paths.get(pl);
      MutableList<Move> moves = lastRound != null ? path.getSucessors(round, lastRound.getMove(pl))
          : path.getMovesOfRound(round);

      MutableList<Evaluation> moveMaxValues = Lists.mutable.empty();
      if (moves.isEmpty()) { // FINISHED
        Evaluation value = play(playersToMove.reject(p -> p.equals(pl)), state, round, lastRound);
        value.setValue(pl, gameLength - (round - 1));
        moveMaxValues.add(value);
      } else {
        MutableList<Move> movesNonBlocked = moves.reject(m -> state.isOccupied(m));
        if (movesNonBlocked.isEmpty()) { // BLOCK
          Evaluation value = play(playersToMove.reject(p -> p.equals(pl)), state, round, lastRound);
          value.setValue(pl, round - gameLength);
          moveMaxValues.add(value);
        } else {
          for (Move move : movesNonBlocked) {
            moveMaxValues.add(play(playersToMove.reject(p -> p.equals(pl)), state.with(pl, move), round, lastRound));
            state.without(pl);
          }
        }
      }
      innerRoundValues.add(getEvaluation(pl, moveMaxValues));
    }
    return Evaluation.avg(innerRoundValues, players);
  }

  private Evaluation getEvaluation(Player pl, Collection<Evaluation> evals) {
    if (pl == player)
      return strategy == DecisionStrategy.Offensive ? Evaluation.min(evals, leader) : Evaluation.max(evals, pl);
    else
      return strategy == DecisionStrategy.Paranoid ? Evaluation.min(evals, player) : Evaluation.max(evals, pl);
  }

  public Evaluation playNextRound(GameState state, int round) {
    if (maxes.containsKey(state.hashCode()))
      return maxes.get(state.hashCode());

    if (round < maxDepth) {
      Evaluation maxnValues = play(state.getPlayers(), new GameState(), round + 1, state);
      maxes.put(state.hashCode(), maxnValues);
      return maxnValues;
    } else {
      List<Future<Evaluation>> futures = Lists.mutable.empty();
      for (int i = 0; i < sampleSize; i++) {
        futures.add(completeSrv.submit(() -> playOut(state.getPlayers(), new GameState(), round + 1, state)));
      }
      MutableList<Evaluation> simValues = Lists.mutable.empty();
      for (Future<Evaluation> future : futures) {
        try {
          simValues.add(future.get(5, TimeUnit.MINUTES));
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
        }
      }
      Evaluation eval = Evaluation.avg(simValues, players);
      maxes.put(state.hashCode(), eval);
      return eval;
    }
  }

  private Evaluation playOut(MutableList<Player> playersToMove, GameState occupied, int round, GameState lastRound) {
    if (playersToMove.isEmpty())
      return lastRound.isEmpty() ? new Evaluation(players)
          : playOut(occupied.getPlayers(), new GameState(), round + 1, occupied);

    Player player = playersToMove.get(random.nextInt(playersToMove.size()));
    Paths path = paths.get(player);
    MutableList<Move> levelMoves = lastRound != null ? path.getSucessors(round, lastRound.getMove(player))
        : path.getMovesOfRound(round);

    if (levelMoves.isEmpty()) { // GAME END
      Evaluation value = playOut(playersToMove.reject(p -> p.equals(player)), occupied, round, lastRound);
      value.setValue(player, 2 * (gameLength - (round - 1)));
      return value;
    } else {
      MutableList<Move> movesNonBlocked = levelMoves.reject(m -> occupied.isOccupied(m));
      if (movesNonBlocked.isEmpty()) { // BLOCK
        Evaluation value = playOut(playersToMove.reject(p -> p.equals(player)), occupied, round, lastRound);
        value.setValue(player, 2 * ((round - 1) - gameLength));
        return value;
      } else {
        Move move = movesNonBlocked.get(random.nextInt(movesNonBlocked.size()));
        return playOut(playersToMove.reject(p -> p.equals(player)), occupied.with(player, move), round, lastRound);
      }
    }
  }

}
