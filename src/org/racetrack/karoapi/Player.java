package org.racetrack.karoapi;

import java.util.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.json.*;

public class Player {

  public enum Field {
    id, name, color, moved, rank, status, moveCount, crashCount, checkedCps, missingCps, motion, possibles, moves;
  }

  protected enum Status {
    OK, LEFT, KICKED
  }

  public static Map<Integer, Player> getPlayers(Game game, JSONArray array) {
    Map<Integer, Player> players = Maps.mutable.empty();
    for (int i = 0; i < array.length(); i++) {
      Player player = new Player(game, (JSONObject) array.get(i));
      players.put(player.getId(), player);
    }
    return players;
  }

  public static Player getNew(int userId) {
    Player player = new Player();
    player.id = userId;
    return player;
  }

  public static Player getFakePlayer(Game game, int id, MutableCollection<MapTile> missingCps,
      Collection<Move> possibles) {
    Player player = new Player();
    player.id = id;
    player.missingCps = missingCps;
    player.possibles = new FastList<>(possibles);
    player.status = Status.OK;
    player.moves = new FastList<>(0);
    player.moveCount = 0;
    player.crashCount = 0;
    return player;
  }

  protected int id;
  private String name;
  @SuppressWarnings("unused")
  private String color;
  @SuppressWarnings("unused")
  private boolean moved;
  private int rank;
  protected Status status;
  protected int moveCount;
  protected int crashCount;
  @SuppressWarnings("unused")
  private MutableCollection<MapTile> checkedCps = new FastList<>(0);
  protected MutableCollection<MapTile> missingCps = new FastList<>(0);
  private LogMove motion;
  protected MutableCollection<Move> possibles = new FastList<>(0);
  protected MutableList<LogMove> moves;
  protected Game game;
  private User user;

  private Player() {
  }

  public Player(Game game, JSONObject json) {
    this.game = game;
    id = json.getInt(Field.id.toString());
    name = json.getString(Field.name.toString());
    color = json.getString(Field.color.toString());
    moved = json.getBoolean(Field.moved.toString());
    rank = json.getInt(Field.rank.toString());
    status = Status.valueOf(json.getString(Field.status.toString()).toUpperCase());
    moveCount = json.getInt(Field.moveCount.toString());
    crashCount = json.getInt(Field.crashCount.toString());
    checkedCps = getCps(json.optJSONArray(Field.checkedCps.toString()));
    missingCps = getCps(json.optJSONArray(Field.missingCps.toString()));
    if (json.has(Field.moves.toString())) {
      moves = LogMove.getPreviousMoves(json.getJSONArray(Field.moves.toString()));
      if (json.has(Field.motion.toString())) {
        motion = new LogMove(json.getJSONObject(Field.motion.toString()));
        int indexOfLastMove = moves.indexOf(motion);
        if (indexOfLastMove >= 0) {
          motion = moves.get(indexOfLastMove);
        }
      }
    }

    if (json.has(Field.possibles.toString())) {
      possibles = Move.getPossibleMoves(json.getJSONArray(Field.possibles.toString()), motion);// lastmove);
    }
  }

  private MutableCollection<MapTile> getCps(JSONArray array) {
    if (array != null) {
      MutableCollection<MapTile> cps = new FastList<>(array.length());
      for (int i = 0; i < array.length(); i++) {
        cps.add(MapTile.valueOf(array.getInt(i)));
      }
      return cps;
    }
    return new FastList<>(0);
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return status.equals(Status.OK) && rank == 0;
  }

  public boolean hasFinished() {
    return status.equals(Status.OK) && rank > 0;
  }

  public int getMoveCount() {
    return moveCount;
  }

  public MutableCollection<MapTile> getMissingCps() {
    return missingCps;
  }

  public LogMove getMotion() {
    return motion;
  }

  public MutableCollection<Move> getPossibles() {
    if (motion == null && possibles.isEmpty()) {
      System.out.println("Bug still exists!");
      MutableList<Move> startMoves = game.getMap().getTilesAsMoves(MapTile.START);
      MutableList<Move> motions = game.getActivePlayers().select(p -> p.moveCount > 0).collect(p -> p.getMotion());
      return startMoves.reject(m -> m.equalsPos(motions));
    }
    return possibles;
  }

  public MutableList<LogMove> getMoves() {
    return moves;
  }

  public LogMove getMove(int round) {
    if (round >= 0) {
      for (LogMove move : moves) {
        if (move.totalLen == round && !move.isCrash())
          return move;
      }
    }
    return null;
  }

  private User getUser() {
    if (user == null) {
      user = User.get(id);
    }
    return user;
  }

  public boolean isBot() {
    return getUser() != null ? getUser().isBot() : false;
  }

  public int getDist(Player player, int round) {
    Move nearMove = player.getMove(round);
    return getMove(round) != null && nearMove != null ? getMove(round).getDist(nearMove) : Integer.MAX_VALUE;
  }

  public int getDist(MutableCollection<Move> possibles, int round) {
    return getMove(round) != null ? getMove(round).getMinDist(possibles) : Integer.MAX_VALUE;
  }

  @Override
  public boolean equals(Object obj) {
    return id == ((Player) obj).id;
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public String toString() {
    return name;
  }

}
