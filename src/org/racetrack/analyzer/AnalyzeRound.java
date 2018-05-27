package org.racetrack.analyzer;

import java.util.*;
import java.util.concurrent.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.track.*;

public class AnalyzeRound {

  private int round;
  private Game game;
  private MutableList<AnalyzeMove> moves;

  private Map<Player, Map<Integer, AnalyzeGame>> games = Maps.mutable.empty();
  private Map<AnalyzeGame, Future<Paths>> analyzedGames = Maps.mutable.empty();

  public AnalyzeRound(int round, Game game, MutableList<AnalyzeMove> moves) {
    this.round = round;
    this.game = game;
    this.moves = moves;
  }

  public Collection<AnalyzeGame> getGames(GameRule rule) {
    Collection<AnalyzeGame> gamesToAnalyze = new FastList<>();

    for (AnalyzeMove move : moves) {
      Player player = move.getPlayer();
      games.put(player, Maps.mutable.empty());
    }

    MutableCollection<Move> passed = new FastList<>();
    MutableCollection<AnalyzeMove> notPassed = new FastList<>(moves);

    moves.sortThis();
    for (int i = 0; i < moves.size(); i++) {
      AnalyzeMove activeMove = moves.get(i);
      AnalyzeGame activeGame = AnalyzeGame.getActivePlayerGame(game, activeMove, rule);
      gamesToAnalyze.add(activeGame);

      games.get(activeMove.getPlayer()).put(i, activeGame);

      passed.add(activeMove);
      notPassed.remove(activeMove);
      for (AnalyzeMove passiveMove : notPassed) {
        AnalyzeGame passiveGame = AnalyzeGame.getPassivePlayerGame(game, passiveMove, passed, rule);
        gamesToAnalyze.add(passiveGame);
        games.get(passiveMove.getPlayer()).put(i, passiveGame);
      }
    }
    return gamesToAnalyze;
  }

  public void setPaths(AnalyzeGame game, Future<Paths> path) {
    if (game != null) {
      analyzedGames.put(game, path);
    }
  }

  private AnalyzeGame getGameOrLast(Map<Integer, AnalyzeGame> map, int moveInRound) {
    for (int i = moveInRound; i >= 0; i--) {
      if (map.containsKey(i))
        return map.get(i);
    }
    return null;
  }

  public String print(List<Player> players) {
    StringBuilder exportRound = new StringBuilder();

    for (Future<Paths> path : analyzedGames.values()) {
      try {
        path.get();
      } catch (InterruptedException | ExecutionException e) {
      }
    }

    for (int i = 0; i < players.size(); i++) {
      StringBuilder exportPlayer = new StringBuilder().append("\"").append(game.getId()).append("\"");
      exportPlayer.append(";").append("\"").append(round).append("\"");
      boolean playerMoved = false;
      String dranPlayer = null;
      StringBuilder crashPlayer = new StringBuilder();
      for (Player player : players) {
        exportPlayer.append(";");
        if (games.containsKey(player)) {
          Map<Integer, AnalyzeGame> map = games.get(player);
          AnalyzeGame game;
          if (map.containsKey(i)) {
            game = map.get(i);
            if (game.isActivePlayersGame()) {
              dranPlayer = game.getDran();
            }
          } else {
            game = getGameOrLast(map, i - 1);
          }
          playerMoved = true;
          if (analyzedGames.containsKey(game)) {
            try {
              Future<Paths> futurePath = analyzedGames.get(game);
              Paths path = futurePath.get();
              if (path.isCrashAhead()) {
                if (crashPlayer.length() != 0) {
                  crashPlayer.append(",");
                }
                crashPlayer.append(player.getName());
              }
              exportPlayer.append("\"").append(path.getMinTotalLength()).append("\"");
            } catch (InterruptedException | ExecutionException e) {
            }
          }
        }
      }
      exportPlayer.append(";").append("\"").append(dranPlayer != null ? dranPlayer : "").append("\"");
      exportPlayer.append(";").append("\"").append(crashPlayer).append("\"");
      exportPlayer.append(System.lineSeparator());
      if (playerMoved) {
        exportRound.append(exportPlayer);
      }
      exportPlayer = new StringBuilder();
    }
    return exportRound.toString();
  }

}
