package org.racetrack.karoapi;

import java.text.*;
import java.util.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.json.*;
import org.racetrack.track.*;

public class Player {

  protected enum Status {
    OK, LEFT, KICKED
  }

  private static final String ID = "id";
  private static final String NAME = "name";
  private static final String COLOR = "color";
  private static final String DRAN = "dran";
  private static final String MOVED = "moved";
  private static final String POSITION = "position";
  private static final String STATUS = "status";
  private static final String MOVE_COUNT = "moveCount";
  private static final String CRASH_COUNT = "crashCount";
  private static final String CHECKED_CPS = "checkedCps";
  private static final String MISSING_CPS = "missingCps";
  private static final String LASTMOVE = "lastmove";
  private static final String POSSIBLES = "possibles";
  private static final String MOVES = "moves";

  public static Map<Integer, Player> getPlayers(JSONArray array) {
    Map<Integer, Player> players = Maps.mutable.empty();
    for (int i = 0; i < array.length(); i++) {
      Player player = new Player((JSONObject) array.get(i));
      players.put(player.getId(), player);
    }
    return players;
  }

  public static Player getNew(int userId) {
    return new Player(userId);
  }

  protected int id;
  private String name;
  private String color;
  protected boolean dran;
  private boolean moved;
  private int position;
  protected Status status;
  protected int moveCount;
  protected int crashCount;
  private MutableCollection<MapTile> checkedCps;
  protected MutableCollection<MapTile> missingCps;
  private LogMove lastmove;
  protected MutableCollection<Move> possibles;
  protected MutableList<LogMove> moves;
  private User user;

  protected Player() {
  }

  public Player(int id) {
    this.id = id;
  }

  public Player(JSONObject json) {
    id = json.getInt(ID);
    name = json.getString(NAME);
    color = json.getString(COLOR);
    dran = json.getBoolean(DRAN);
    moved = json.getBoolean(MOVED);
    position = json.getInt(POSITION);
    status = Status.valueOf(json.getString(STATUS).toUpperCase());
    moveCount = json.getInt(MOVE_COUNT);
    crashCount = json.getInt(CRASH_COUNT);
    checkedCps = getCps(json.optJSONArray(CHECKED_CPS));
    missingCps = getCps(json.optJSONArray(MISSING_CPS));
    if (json.has(MOVES)) {
      moves = LogMove.getPreviousMoves(json.getJSONArray(MOVES));
    }

    if (json.has(LASTMOVE)) {
      lastmove = new LogMove(json.getJSONObject(LASTMOVE));
    }
    int indexOfLastMove = moves.indexOf(lastmove);
    if (indexOfLastMove >= 0) {
      lastmove = moves.get(indexOfLastMove);
    }

    if (json.has(POSSIBLES)) {
      possibles = LogMove.getPossibleMoves(json.getJSONArray(POSSIBLES), lastmove);
    }
  }

  public Player(Player player) {
    id = player.id;
    name = player.name;
    color = player.color;
    dran = player.dran;
    moved = player.moved;
    position = player.position;
    status = player.status;
    moveCount = player.moveCount;
    crashCount = player.crashCount;
    checkedCps = player.checkedCps;
    missingCps = player.missingCps;
    lastmove = player.lastmove;
    possibles = player.possibles;
    moves = player.moves;
    user = player.user;
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

  public String getColor() {
    return color;
  }

  public boolean isDran() {
    return dran;
  }

  public boolean isMoved() {
    return moved;
  }

  public int getPosition() {
    return position;
  }

  public Status getStatus() {
    return status;
  }

  public boolean isActive() {
    return status.equals(Status.OK);
  }

  public int getMoveCount() {
    return moveCount;
  }

  public int getCrashCount() {
    return crashCount;
  }

  public MutableCollection<MapTile> getCheckedCps() {
    return checkedCps;
  }

  public MutableCollection<MapTile> getMissingCps() {
    return missingCps;
  }

  public LogMove getLastmove() {
    return lastmove;
  }

  public MutableCollection<Move> getPossibles() {
    return possibles;
  }

  public Paths getPossiblesAsPaths(Game game) {
    return new Paths(game, possibles);
  }

  public MutableList<LogMove> getMoves() {
    return moves;
  }

  public LogMove getMove(int round) {
    if (round >= 0) {
      for (LogMove move : moves) {
        if (move.totalLen == round - 1 && !move.isCrash())
          return move;
      }
    }
    return null;
  }

  public double getAvgSpeed() {
    int moveCount = 0;
    double trackLength = 0d;
    for (LogMove move : moves) {
      if (move.isMoving()) {
        moveCount++;
        trackLength += move.getSpeed();
      }
    }
    return trackLength / moveCount;
  }

  public String getTrackAnalysis() {
    int moveCount = 0;
    double trackLength = 0d;
    for (LogMove move : moves) {
      if (move.isMoving()) {
        moveCount++;
        trackLength += move.getSpeed();
      }
    }
    NumberFormat formatter = new DecimalFormat("#0.00");
    return getName() + ": " + (moveCount > 0
        ? (formatter.format(trackLength / (moveCount * moveCount)) + "/" + formatter.format(trackLength / moveCount))
        : "0/0") + "[" + moveCount + "]";
  }

  public User getUser() {
    if (user == null) {
      user = User.get(id);
    }
    return user;
  }

  public boolean isBot() {
    return getUser() != null ? getUser().isBot() : false;
  }

  @Override
  public boolean equals(Object obj) {
    return id == ((Player) obj).id;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(ID).append(":").append(id).append("\n");
    sb.append(NAME).append(":").append(name).append("\n");
    sb.append(DRAN).append(":").append(dran).append("\n");
    sb.append(MOVED).append(":").append(moved).append("\n");
    sb.append(POSITION).append(":").append(position).append("\n");
    sb.append(MOVE_COUNT).append(":").append(moveCount).append("\n");
    return sb.toString();
  }

}
