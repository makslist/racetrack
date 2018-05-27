package org.racetrack.analyzer;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.track.*;

public class AnalyzeGame extends Game {

  public static AnalyzeGame getActivePlayerGame(Game game, AnalyzeMove move, GameRule rule) {
    AnalyzeGame anaGame = new AnalyzeGame(game);
    anaGame.activePlayersGame = true;

    Player movePlayer = move.getPlayer();
    anaGame.dranId = movePlayer.getId();
    anaGame.dran = movePlayer.getName();

    MutableCollection<MapTile> missingCps = Lists.mutable.empty();
    if (game.isWithCheckpoints()) {
      missingCps = game.getMap().getCps();
      missingCps.removeIf(tile -> rule.hasXdCp(new Paths(anaGame, FastList.newListWith(move)), tile));
    }
    anaGame.players = Maps.mutable.of(anaGame.dranId,
        new AnalyzePlayer(movePlayer, FastList.newListWith(move), missingCps));

    return anaGame;
  }

  public static AnalyzeGame getPassivePlayerGame(Game game, AnalyzeMove logMove, MutableCollection<Move> impossibles,
      GameRule rule) {
    AnalyzeGame anaGame = new AnalyzeGame(game);

    Player movePlayer = logMove.getPlayer();
    anaGame.dranId = movePlayer.getId();
    anaGame.dran = movePlayer.getName();

    MutableCollection<Move> possibles = new FastList<>(0);
    MutableCollection<Move> predecessors = logMove.getNonCrashPredecessors();
    if (predecessors.isEmpty()) {
      possibles.addAll(game.getMap().getTilesAsMoves(MapTile.START));
    } else {
      possibles.addAll(predecessors.flatCollect(Move::getNext));
      possibles = possibles.select(rule.filterMap());
    }
    possibles.removeIf(move -> impossibles.anySatisfy(imp -> imp.equalsPos(move)));

    if (possibles.isEmpty()) {
      possibles = FastList.newListWith(logMove);
    }

    MutableCollection<MapTile> missingCps = game.isWithCheckpoints() ? game.getMap().getCps() : Lists.mutable.empty();
    missingCps.removeIf(tile -> rule.hasXdCp(new Paths(game, predecessors), tile));

    anaGame.players = Maps.mutable.of(anaGame.dranId, new AnalyzePlayer(movePlayer, possibles, missingCps));

    return anaGame;
  }

  public static AnalyzeGame getBlockPlayerGame(Game game, Player player, MutableCollection<Move> moves,
      MutableCollection<Move> impossibles, GameRule rule) {
    AnalyzeGame anaGame = new AnalyzeGame(game);

    anaGame.dranId = player.getId();
    anaGame.dran = player.getName();

    MutableCollection<Move> possibles = moves.select(rule.filterMap()).reject(Move.equalsPosition(impossibles));
    if (possibles.isEmpty()) { // Pathfinder only allows a valid move as start
      possibles.addAll(player.getLastmove().getMovesAfterCrash(game.getZzz()).flatCollect(Move::getNext));
    }

    MutableCollection<MapTile> missingCps = game.isWithCheckpoints() ? game.getMap().getCps() : Lists.mutable.empty();
    missingCps.removeIf(tile -> rule.hasXdCp(new Paths(game, FastList.newListWith(player.getLastmove())), tile));

    anaGame.players = Maps.mutable.of(anaGame.dranId, new AnalyzePlayer(player, possibles, missingCps));

    return anaGame;
  }

  private boolean activePlayersGame = false;

  public AnalyzeGame(int gameId) {
    id = gameId;
    readGame(true);
  }

  protected AnalyzeGame(Game game) {
    super(game);
  }

  public boolean isActivePlayersGame() {
    return activePlayersGame;
  }

}
