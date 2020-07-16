package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.track.Strategy.*;
import org.racetrack.worker.*;

public class GTS implements Callable<GameAction> {

  private Game game;
  private MutableList<Pair<Player, Integer>> playerLength = new FastList<>(0);
  private int round;

  private MutableMap<Player, Paths> paths;
  private MutableList<Player> players;
  private ConcurrentMutableMap<Integer, Evaluation> evaluations = new ConcurrentHashMap<>();

  private Strategy strategy;
  private final double maxStatesPerRound = Settings.getInstance().gtsMaxStatesPerRound();
  private MutableMap<Integer, Long> statesInRound = Maps.mutable.empty();

  ForkJoinPool executor = new ForkJoinPool();

  public GTS(Game game, MutableMap<Player, Paths> paths, int round) {
    this.game = game;
    this.paths = paths;
    this.round = round;
  }

  @Override
  public GameAction call() {
    Player player = game.getNext();

    players = new FastList<>(paths.keySet());
    for (Player pl : players) {
      Paths path = paths.get(pl);
      int len = !path.isEmpty() ? path.getMinTotalLength() : Integer.MIN_VALUE;
      playerLength.add(new Pair<Player, Integer>(pl, len));
    }
    strategy = Strategy.get(player, playerLength);

    Paths path = paths.get(player);

    calcStatesInRound();
    ConsoleOutput.print(game.getId(), "Ratings:");
    MutableMap<Evaluation, Move> moveRatings = Maps.mutable.empty();
    final GameState startState = getStartState();
    for (Move move : startState.getUnblocked(path.getMovesOfRound(round))) {
      ForkJoinTask<Evaluation> task = null;
      if (startState.getNotMoved(players).size() == 1) {
        task = executor.submit(playNextRound(startState.add(player, move)));
      } else {
        task = executor.submit(() -> play(startState.add(player, move)));
      }
      Evaluation eval = task.fork().join();
      System.out.print(" " + move + " " + eval);
      moveRatings.put(eval, move);
    }
    System.out.println("");

    Comparator<Evaluation> maxSuccesorsFirst = (e1, e2) -> path.getSuccessors(round + 1, moveRatings.get(e2)).size()
        - path.getSuccessors(round + 1, moveRatings.get(e1)).size();
    MutableList<Evaluation> evals = new FastList<>(moveRatings.keySet()).sortThis(maxSuccesorsFirst);
    Move bestMove = evals.isEmpty() ? null : moveRatings.get(strategy.evaluate(player, evals));

    ConsoleOutput.println(game.getId(), "Result: " + bestMove + " with strategy : " + strategy);
    Paths playerPath = paths.get(player);
    return new GameAction(game, bestMove, playerPath.getMinLength() == 1, playerPath.getComment());
  }

  private Evaluation play(GameState state) {
    MutableList<Evaluation> evals = new FastList<>();
    int round = state.getRound();
    MutableList<Player> notMovedPlayers = state.getNotMoved(players);
    for (Player pl : notMovedPlayers) {
      Paths path = paths.get(pl);
      MutableList<Move> roundMmoves = path.getMovesOfRound(round);
      if (roundMmoves.isEmpty()) {
        // player has already finished the game or been blocked
        evals.add(play(state.add(pl)));
        continue;
      }

      MutableList<Move> unblockedMoves = state
          .getUnblocked(state.isStartState() ? roundMmoves : path.getSuccessors(round, state.getPrevMove(pl)));
      if (unblockedMoves.isEmpty()) {
        evals.add(strategy.block(pl,
            notMovedPlayers.size() == 1 ? playNextRound(state.add(pl)).fork().join() : play(state.add(pl))));
      } else if (notMovedPlayers.size() == 1) {
        MutableList<RecursiveTask<Evaluation>> tasks = unblockedMoves.collect(m -> playNextRound(state.add(pl, m)));
        tasks.forEach(t -> t.fork());
        evals.add(strategy.evaluate(pl, tasks.collect(t -> t.join())));
      } else {
        evals.add(strategy.evaluate(pl, unblockedMoves.collect(m -> play(state.add(pl, m)))));
      }
    }
    return strategy.merge(evals);
  }

  public RecursiveTask<Evaluation> playNextRound(GameState state) {
    return new RecursiveTask<Strategy.Evaluation>() {
      private static final long serialVersionUID = 1L;

      @Override
      protected Evaluation compute() {
        if (state.isGameFinished())
          return strategy.gameEnd();
        else if (statesInRound.get(state.getRound()) > maxStatesPerRound)
          return strategy.maxDepth();
        else
          return evaluations.getIfAbsentPut(state.hashCode(), () -> play(state.nextRound()).normalize());
      }
    };
  }

  private GameState getStartState() {
    GameState state = new GameState(round);
    for (Player pl : players.select(p -> p.hasMovedInRound(round))) {
      state = state.add(pl, pl.getMotion());
    }
    return state;
  }

  private void calcStatesInRound() {
    for (int i = game.getCurrentRound() - 1; i < 300; i++) {
      long nodeCount = 1;
      final int round = i;
      for (Integer moveCount : players.collect(p -> paths.get(p).getMovesOfRound(round).size()).select(c -> c > 0)) {
        nodeCount *= moveCount;
      }
      statesInRound.put(i, nodeCount);
    }
  }

  class Pair<K, V> {

    K key;
    V value;

    public Pair(K key, V value) {
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

    private int round;
    private Player player;
    private Move move;
    private GameState prev;
    private GameState lastRound;

    private GameState(int round) {
      this.round = round;
    }

    private GameState(Player player, Move move, int round, GameState lastRound) {
      this.player = player;
      this.move = move;
      this.round = round;
      this.lastRound = lastRound;
    }

    private GameState add(Player player, Move move) {
      GameState newState = new GameState(player, move, round, lastRound);
      newState.prev = this;
      return newState;
    }

    private GameState add(Player player) {
      return add(player, null);
    }

    private GameState nextRound() {
      GameState gameState = new GameState(round + 1);
      gameState.lastRound = this;
      return gameState;
    }

    private int getRound() {
      return round;
    }

    private boolean isStartState() {
      return lastRound == null;
    }

    private boolean hasMoved(Player player) {
      return player.equals(this.player) || (prev != null && prev.hasMoved(player));
    }

    private boolean hasMoved() {
      return player != null && move != null || (prev != null && prev.hasMoved());
    }

    private boolean isTaken(Move move) {
      return move.equalsPos(this.move) || (prev != null && prev.isTaken(move));
    }

    private Move getMove(Player player) {
      return player.equals(this.player) ? move : (prev != null ? prev.getMove(player) : null);
    }

    private Move getPrevMove(Player player) {
      return lastRound != null ? lastRound.getMove(player) : null;
    }

    private boolean isGameFinished() {
      return !hasMoved();
    }

    private MutableList<Player> getNotMoved(MutableList<Player> players) {
      return players.select(p -> (lastRound == null || lastRound.hasMoved(p)) && !hasMoved(p));
    }

    private MutableList<Move> getUnblocked(MutableList<Move> moves) {
      return moves.reject(m -> isTaken(m));
    }

    private MutableList<GameState> aslist() {
      MutableList<GameState> prevs = prev != null ? prev.aslist() : new FastList<>();
      return player != null ? prevs.with(this) : prevs;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null)
        return false;

      GameState c = (GameState) o;
      return (player.equals(c.player) && move.equals(c.move)) && (prev != null && prev.equals(c.prev));
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (GameState state : aslist().sortThis((s1, s2) -> (s1.player != null ? s1.player.getName() : "null")
          .compareTo(s2.player != null ? s2.player.getName() : "null"))) {
        if (sb.length() != 0) {
          sb.append(" ");
        }
        sb.append(state.player + ":" + state.move);
      }
      return sb.toString();
    }

  }

}
