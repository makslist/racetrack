package org.racetrack.analyzer;

import java.io.*;
import java.util.*;
import java.util.Map.*;
import java.util.concurrent.*;

import org.eclipse.collections.api.block.predicate.*;
import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.track.*;
import org.racetrack.worker.*;

public class GameAnalyzer {

  private static final Collection<String> NOT_INCLUDE_PLAYER = Arrays.asList("KarasersBIZEPS");

  private GameAnalyzer() {
  }

  public static String runningGame(Game game, Move currentPlayerMove, int currentPlayersPathLength) {
    int currentRound = game.getCurrentRound();

    MutableMap<Player, Integer> playerPathsLength = Maps.mutable.empty();

    Player currentPlayer = game.getDranPlayer();
    playerPathsLength.put(currentPlayer, currentPlayersPathLength + 2);// add 2 for start and parc ferme
    GameRule rule = RuleFactory.getInstance(game);
    TSP tsp = new TSP(game);

    ExecutorService executor = Executors.newWorkStealingPool();
    MutableCollection<Move> passedMoves = FastList.newListWith(currentPlayerMove);
    MutableList<Player> otherPlayers = game.getActivePlayers().reject(player -> player.equals(currentPlayer));

    Predicate<Player> alreadyMoved = player -> player.getMove(currentRound) != null;
    for (Player player : otherPlayers.select(alreadyMoved)) {
      LogMove move = player.getMove(currentRound);
      passedMoves.add(move);
      AnalyzeMove anaMove = new AnalyzeMove(move, player);
      AnalyzeGame activeGame = AnalyzeGame.getActivePlayerGame(game, anaMove, rule);
      try {
        int minTotalLength = executor.submit(new PathFinder(activeGame, player, rule, tsp)).get().getMinTotalLength();
        playerPathsLength.put(player, minTotalLength + 2);// add 2 for start and parc ferme
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
    }

    for (Player player : otherPlayers.reject(alreadyMoved)) {
      Move logMove = player.getMove(currentRound - 1);
      AnalyzeGame passiveGame = AnalyzeGame.getBlockPlayerGame(game, player, FastList.newListWith(logMove), passedMoves,
          rule);
      try {
        int minTotalLength = executor.submit(new PathFinder(passiveGame, player, rule, tsp)).get().getMinTotalLength();
        playerPathsLength.put(player, minTotalLength + 2); // add 2 for start and parc ferme
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
    }

    executor.shutdown();

    StringJoiner joiner = new StringJoiner(", ");
    MutableList<Entry<Player, Integer>> pathLengths = new FastList<>(playerPathsLength.entrySet());
    for (Map.Entry<Player, Integer> playerLength : pathLengths.sortThis((l1, l2) -> l1.getValue() - l2.getValue())) {
      joiner.add(playerLength.getKey().getName() + ":" + playerLength.getValue());
    }
    return joiner.toString();
  }

  public static void completedGame(int gameId) {
    Game game = new AnalyzeGame(gameId);
    List<AnalyzeRound> rounds = new FastList<>(0);

    Comparator<Player> botsLast = (p1, p2) -> p1.isBot() && !p2.isBot() ? 1
        : (!p1.isBot() && p2.isBot()) ? -1 : p1.getName().compareToIgnoreCase(p2.getName());

    List<Player> players = game.getPlayers().select(player -> !NOT_INCLUDE_PLAYER.contains(player.getName()))
        .sortThis(botsLast);

    for (int roundNumber = 1;; roundNumber++) {
      MutableList<AnalyzeMove> roundMoves = new FastList<>();
      for (Player player : players) {
        LogMove move = player.getMove(roundNumber);
        if (move != null && !move.isCrash()) {
          roundMoves.add(new AnalyzeMove(move, player));
        }
      }

      if (roundMoves.isEmpty()) {
        break;
      }

      AnalyzeRound round = new AnalyzeRound(roundNumber, game, roundMoves);
      rounds.add(round);
    }

    ExecutorService executor = Executors.newWorkStealingPool();
    CompletionService<Paths> completionService = new ExecutorCompletionService<>(executor);

    GameRule rule = RuleFactory.getInstance(game);
    TSP tsp = new TSP(game);

    for (AnalyzeRound round : rounds) {
      for (AnalyzeGame anaGame : round.getGames(rule)) {
        Player player = anaGame.getDranPlayer();
        Future<Paths> path = completionService.submit(new PathFinder(anaGame, player, rule, tsp));
        round.setPaths(anaGame, path);
      }
    }

    String exportHeader = getExportHeader(game, players);
    StringBuilder export = new StringBuilder(exportHeader);
    CliProgressBar progressBar = CliProgressBar.getAnalyzeBar(game, rounds.size());
    for (AnalyzeRound round : rounds) {
      export.append(round.print(players));
      progressBar.incProgress();
    }

    try {
      FileWriter fw = new FileWriter(game.getId() + ".csv");
      fw.write(export.toString());
      fw.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    executor.shutdown();
  }

  private static String getExportHeader(Game game, List<Player> players) {
    StringBuilder exportHeader = new StringBuilder();
    exportHeader.append("\"").append("Game ").append(game.getId()).append(" (")
        .append(game.getName().replace("\"", "\'")).append(")").append("\"").append(System.lineSeparator());

    exportHeader.append(System.lineSeparator()).append("\"").append(game.getId()).append("\"");
    exportHeader.append(";").append("\"Runde\"");
    for (Player player : players) {
      exportHeader.append(";").append("\"").append(player.getName()).append("\"");
    }
    exportHeader.append(";").append("\"").append("dran").append("\"");
    exportHeader.append(";").append("\"").append("Crash").append("\"").append(System.lineSeparator());
    return exportHeader.toString();
  }

}
