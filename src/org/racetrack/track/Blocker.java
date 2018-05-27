package org.racetrack.track;

import java.util.*;
import java.util.concurrent.*;

import org.eclipse.collections.api.block.predicate.*;
import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.analyzer.*;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.worker.*;

public class Blocker {

  private Game game;
  private TSP tsp;
  private GameRule rule;

  private MutableCollection<Move> playerMoves;
  private MutableCollection<Move> occupiedFields = new FastList<>(0);

  private Predicate<Move> notOccupied = move -> occupiedFields.noneSatisfy(occupied -> occupied.equalsPos(move));
  private Predicate<Move> intersectWithPlayer = move -> playerMoves.anySatisfy(imp -> imp.equalsPos(move));

  public Blocker(Game game, TSP tsp, GameRule rule) {
    this.game = game;
    this.tsp = tsp;
    this.rule = rule;
  }

  public void calcMoves(Paths path) {
    playerMoves = path.getRelativeLevelMoves(1);

    if (playerMoves.isEmpty() || playerMoves.size() == 1)
      return;

    int round = game.getCurrentRound();
    occupiedFields = game.getActivePlayers().collectIf(player -> player.getMove(round) != null,
        player -> player.getMove(round));

    int maxThreads = Integer.max(Settings.getInstance().getInt(Property.maxParallelGameThreads), 1);
    int minThreads = Integer.min(maxThreads, Runtime.getRuntime().availableProcessors());
    ExecutorService threadPool = Executors.newWorkStealingPool(minThreads);

    Map<Move, MutableCollection<Integer>> oppPathsIntLen = Maps.mutable.empty();
    Map<Move, MutableCollection<Integer>> friendPathsIntLen = Maps.mutable.empty();

    MutableCollection<Player> notMovedYet = game.getActivePlayers().select(
        oth -> !game.getDranPlayer().equals(oth) && oth.getMove(round) == null && oth.getMove(round - 1) != null);

    CliProgressBar progress = CliProgressBar.getBlockerBar(path.getGame(), notMovedYet.size());
    for (Player oth : notMovedYet) {
      Move othLastMv = oth.getMove(round - 1);
      MutableCollection<Move> othMoves = othLastMv.getNext().select(rule.filterMap()).select(notOccupied);
      if (othMoves.anySatisfy(intersectWithPlayer)) {
        for (Move playerMv : playerMoves) {
          AnalyzeGame analyze = AnalyzeGame.getBlockPlayerGame(game, oth, othMoves, FastList.newListWith(playerMv),
              rule);
          Future<Paths> futurePath = threadPool.submit(new PathFinder(analyze, oth, rule, tsp));
          try {
            int minLength = futurePath.get().getMinTotalLength();
            if (rule.isOpponent(oth)) {
              oppPathsIntLen.computeIfAbsent(playerMv, k -> new FastList<>()).add(minLength);
            } else {
              friendPathsIntLen.computeIfAbsent(playerMv, k -> new FastList<>()).add(minLength);
            }
          } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
          }
        }
      }
      progress.incProgress();
    }

    if (oppPathsIntLen.isEmpty() && friendPathsIntLen.isEmpty())
      return;

    for (Move playerMv : playerMoves) {
      if (oppPathsIntLen.containsKey(playerMv)) {
        MutableCollection<Integer> oppLen = oppPathsIntLen.get(playerMv);
        path.setOpponentsTotalLen(playerMv, oppLen.sumOfInt(x -> x) / (float) oppLen.size());
      }
      if (friendPathsIntLen.containsKey(playerMv)) {
        MutableCollection<Integer> friendsLen = friendPathsIntLen.get(playerMv);
        path.setFriendsTotalLen(playerMv, friendsLen.sumOfInt(x -> x) / (float) friendsLen.size());
      }
    }
    threadPool.shutdown();
  }

}
