package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.racetrack.karoapi.*;
import org.racetrack.track.Strategy.*;

public class GTS implements Callable<GameAction> {

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
  private MutableList<Pair<Player, Integer>> playerLength = new FastList<>(0);
  private int round;

  private MutableMap<Player, Paths> paths;
  private ConcurrentMutableMap<Integer, Evaluation> evaluations = new ConcurrentHashMap<>();

  private Strategy strategy;
  private int maxGameLength = Integer.MIN_VALUE;
  private int maxDepth = Integer.MAX_VALUE;

  public GTS(Game game, MutableMap<Player, Paths> paths, int round) {
    this.game = game;
    this.paths = paths;
    this.round = round;
  }

  @Override
  public GameAction call() {
    Player player = game.getNext();

    FastList<Player> players = new FastList<>(paths.keySet());
    for (Player pl : players) {
      Paths path = paths.get(pl);
      int len = !path.isEmpty() ? path.getMinTotalLength() : Integer.MIN_VALUE;
      maxGameLength = Math.max(maxGameLength, len);
      playerLength.add(new Pair<Player, Integer>(pl, len));
    }
    maxDepth = calcMaxNDepth(players, maxGameLength);
    strategy = Strategy.get(player, playerLength);

    MutableList<Player> playersAlreadyMoved = players.select(p -> p.hasMovedInRound(round));
    MutableList<Player> playersNotYetMoved = players.reject(p -> p.hasMovedInRound(round));
    GameState currentState = new GameState(playersAlreadyMoved.collect(p -> new Pair<Player, Move>(p, p.getMotion())));

    Move bestMove = play(player, playersNotYetMoved, currentState, round);

    System.out.println(game.getId() + " Result: " + bestMove + " with strategy : " + strategy);
    return new GameAction(game, bestMove, paths.get(player).getComment());
  }

  private int calcMaxNDepth(MutableList<Player> actualPlayers, int maxRound) {
    for (int i = game.getCurrentRound() - 1; i < maxRound; i++) {
      long nodeCount = 1;
      for (int j = 0; j < actualPlayers.size(); j++) {
        nodeCount *= (j + 1) * Math.max(paths.get(actualPlayers.get(j)).getMovesOfRound(i).size(), 1);
      }
      if (nodeCount > Math.pow(2, 20)) // max nodes in round
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
    if (evals.isEmpty())
      return null;
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
      return strategy.gameEnd();

    Evaluation eval = round > maxDepth ? strategy.maxDepth()
        : play(state.getPlayers(), new GameState(), round + 1, state);
    evaluations.put(state.hashCode(), eval);
    return eval;
  }

}
