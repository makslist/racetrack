package org.racetrack.karoapi;

import java.util.*;
import java.util.logging.*;

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

  protected static final Logger logger = Logger.getLogger(Player.class.toString());

  public static Map<Integer, Player> getPlayers(Game game, JSONArray array) {
    Map<Integer, Player> players = Maps.mutable.empty();
    for (int i = 0; i < array.length(); i++) {
      Player player = new Player(game, (JSONObject) array.get(i));
      players.put(player.getId(), player);
    }
    return players;
  }

  public static Player getNew(int userId, String name) {
    Player player = new Player();
    player.id = userId;
    player.name = name;
    return player;
  }

  public static Player getFakePlayer(Game game, int id, MutableCollection<MapTile> missingCps,
      Collection<Move> possibles) {
    Player player = new Player();
    player.game = game;
    player.id = id;
    player.name = "Test Dummy";
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
    moveCount = motion == null ? 0 : motion.getTotalLen() + 1;

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
    return possibles;
  }

  public MutableCollection<Move> getNextMoves() {
    MutableCollection<Move> moves = motion != null ? motion.getNext() : game.getMap().getTilesAsMoves(MapTile.START);
    MutableList<Move> blocked = game.getActivePlayers().select(p -> p.moveCount == moveCount + 1)
        .collect(p -> p.getMotion());
    MutableCollection<Move> possibleMoves = moves.reject(m -> m.equalsPos(blocked));
    if (possibles.size() > possibleMoves.size()) {
      logger.severe("Possibles when calculating possibles: " + possibles + " / " + possibleMoves);
    }
    return possibleMoves;
  }

  public MutableList<LogMove> getMoves() {
    return moves;
  }

  public LogMove getMove(int round) {
    return moves.detect(m -> m.totalLen == round - 1 && !m.isCrash());
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
    LogMove move = getMove(round);
    return move != null && nearMove != null ? move.getDist(nearMove) : Integer.MAX_VALUE;
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
