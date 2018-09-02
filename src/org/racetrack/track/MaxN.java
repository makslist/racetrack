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

public class MaxN implements Callable<GameAction> {

  private static final int SAMPLE_SIZE = 50;

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

  private class Constellation {

    private MutableList<PlayerMove> moves = new FastList<>();

    private Constellation() {
    }

    private Constellation(Player player, Move move) {
      moves.add(new PlayerMove(player, move));
    }

    private Constellation(MutableList<PlayerMove> moves) {
      this.moves = moves.clone();
    }

    private boolean isOccupied(Move move) {
      return moves.anySatisfy(pm -> pm.move.equalsPos(move));
    }

    private Constellation with(Player player, Move move) {
      moves.with(new PlayerMove(player, move));
      return this;
    }

    private Constellation without(Player player) {
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
    public Constellation clone() {
      return new Constellation(moves);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Constellation))
        return false;

      Constellation c = (Constellation) o;
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

  private Game game;
  private GameRule rule;
  private TSP tsp;
  private Player player;

  private MutableObjectIntMap<Player> players = ObjectIntMaps.mutable.empty();
  private MutableMap<Player, Paths> paths = Maps.mutable.empty();
  private IntObjectHashMap<MiniMax> maxes = new IntObjectHashMap<>();
  private Random random = new Random();

  private int maxThreads = Integer.max(Settings.getInstance().getInt(Property.maxParallelTourThreads), 1);
  private int minThreads = Integer.min(maxThreads, Runtime.getRuntime().availableProcessors());
  private ExecutorService threadPool = Executors.newWorkStealingPool(minThreads);
  private CompletionService<MiniMax> completeSrv = new ExecutorCompletionService<>(threadPool);

  private int gameLength = Integer.MIN_VALUE;
  private int maxDepth = Integer.MAX_VALUE;

  public MaxN(Game game, Player player) {
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
    try {
      for (Player pl : actualPlayers) {
        Paths path = futurePaths.get(pl).get();
        int len = !path.isEmpty() ? path.getMinTotalLength() : Integer.MIN_VALUE;
        paths.put(pl, path);
        if (len > gameLength) {
          gameLength = len;
        }
      }
    } catch (InterruptedException | ExecutionException e) {
    }
    threadPool.shutdown();

    MutableList<Move> playerMoves = paths.get(player).getMovesOfRound(round);
    if (playerMoves.size() == 1)
      return new GameAction(game, paths.get(player), playerMoves.getFirst());

    maxDepth = calcMaxNDepth(actualPlayers, 80000, gameLength);

    int playerpos = 0;
    for (Player pl : actualPlayers) {
      players.put(pl, playerpos++);
    }

    MutableList<Player> playersAlreadyMoved = actualPlayers.select(p -> p.getMove(round) != null);
    MutableList<Player> playersNotYetMoved = actualPlayers.reject(p -> p.getMove(round) != null);
    Constellation currentConstellation = new Constellation(
        playersAlreadyMoved.collect(p -> new PlayerMove(p, p.getLastmove())));
    Move bestMove = maxn(player, playersNotYetMoved, currentConstellation, round);

    System.out.println("Actual players: " + actualPlayers + "with unique nodes: " + maxes.size());
    // DEBUG System.out.println("Result: " + bestMove);

    return new GameAction(game, paths.get(player), bestMove);
  }

  public int calcMaxNDepth(MutableList<Player> actualPlayers, int maxNodes, int maxRound) {
    int nodes = 0;
    for (int i = game.getCurrentRound() - 1; i < maxRound; i++) {
      int curRound = 1;
      for (Player player : actualPlayers) {
        curRound *= paths.get(player).getMovesOfRound(i).size();
      }
      nodes += curRound * actualPlayers.size();
      if (nodes > maxNodes)
        return i - 1;
    }
    return maxRound;
  }

  private Move maxn(Player player, MutableList<Player> playersToMove, Constellation occupied, int round) {
    Paths path = paths.get(player);
    MutableMap<MiniMax, Move> moveRatings = Maps.mutable.empty();
    for (Move move : path.getMovesOfRound(round).reject(m -> occupied.isOccupied(m))) {
      MiniMax maxn = maxn(playersToMove.reject(p -> p.equals(player)), occupied.with(player, move), round, null);
      moveRatings.put(maxn, move);
      // DEBUG System.out.println("Move: " + move + " with rating: " + maxn);
      occupied.without(player);
    }
    MiniMax max = MiniMax.max(moveRatings.keySet(), player);
    return moveRatings.get(max);
  }

  private MiniMax maxn(MutableList<Player> playersToMove, Constellation occupied, int round, Constellation lastRound) {
    if (playersToMove.isEmpty())
      return maxnNextRound(occupied, round);

    MutableList<MiniMax> innerRoundValues = Lists.mutable.empty();
    for (Player player : playersToMove) {
      Paths path = paths.get(player);
      MutableList<Move> moves = lastRound != null ? path.getSucessors(round, lastRound.getMove(player))
          : path.getMovesOfRound(round);

      MutableList<MiniMax> moveMaxValues = Lists.mutable.empty();
      if (moves.isEmpty()) { // FINISHED
        MiniMax value = maxn(playersToMove.reject(p -> p.equals(player)), occupied, round, lastRound);
        value.setValue(player, gameLength - (round - 1));
        moveMaxValues.add(value);
      } else {
        MutableList<Move> movesNonBlocked = moves.reject(m -> occupied.isOccupied(m));
        if (movesNonBlocked.isEmpty()) { // BLOCK
          MiniMax value = maxn(playersToMove.reject(p -> p.equals(player)), occupied, round, lastRound);
          value.setValue(player, round - gameLength);
          moveMaxValues.add(value);
        } else {
          for (Move move : movesNonBlocked) {
            moveMaxValues
                .add(maxn(playersToMove.reject(p -> p.equals(player)), occupied.with(player, move), round, lastRound));
            occupied.without(player);
          }
        }
      }
      innerRoundValues.add(MiniMax.max(moveMaxValues, player));
    }
    return MiniMax.avg(innerRoundValues, players);
  }

  public MiniMax maxnNextRound(Constellation occupied, int round) {
    if (maxes.containsKey(occupied.hashCode()))
      return maxes.get(occupied.hashCode());

    if (round < maxDepth) {
      MiniMax maxnValues = maxn(occupied.getPlayers(), new Constellation(), round + 1, occupied);
      maxes.put(occupied.hashCode(), maxnValues);
      return maxnValues;
    } else {
      List<Future<MiniMax>> futures = Lists.mutable.empty();
      for (int i = 0; i < SAMPLE_SIZE; i++) {
        futures.add(completeSrv.submit(() -> playOut(occupied.getPlayers(), new Constellation(), round + 1, occupied)));
      }
      MutableList<MiniMax> simValues = Lists.mutable.empty();
      for (Future<MiniMax> future : futures) {
        try {
          simValues.add(future.get(5, TimeUnit.MINUTES));
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
        }
      }
      MiniMax maxNValue = MiniMax.avg(simValues, players);
      maxes.put(occupied.hashCode(), maxNValue);
      return maxNValue;
    }
  }

  private MiniMax playOut(MutableList<Player> playersToMove, Constellation occupied, int round,
      Constellation lastRound) {
    if (playersToMove.isEmpty())
      return lastRound.isEmpty() ? new MiniMax(players)
          : playOut(occupied.getPlayers(), new Constellation(), round + 1, occupied);

    Player player = playersToMove.get(random.nextInt(playersToMove.size()));
    Paths path = paths.get(player);
    MutableList<Move> levelMoves = lastRound != null ? path.getSucessors(round, lastRound.getMove(player))
        : path.getMovesOfRound(round);

    if (levelMoves.isEmpty()) { // GAME END
      MiniMax value = playOut(playersToMove.reject(p -> p.equals(player)), occupied, round, lastRound);
      value.setValue(player, 2 * (gameLength - (round - 1)));
      return value;
    } else {
      MutableList<Move> movesNonBlocked = levelMoves.reject(m -> occupied.isOccupied(m));
      if (movesNonBlocked.isEmpty()) { // BLOCK
        MiniMax value = playOut(playersToMove.reject(p -> p.equals(player)), occupied, round, lastRound);
        value.setValue(player, 2 * ((round - 1) - gameLength));
        return value;
      } else {
        Move move = movesNonBlocked.get(random.nextInt(movesNonBlocked.size()));
        return playOut(playersToMove.reject(p -> p.equals(player)), occupied.with(player, move), round, lastRound);
      }
    }
  }

}
