package org.racetrack.karoapi;

import java.text.*;
import java.util.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.json.*;

public class LogMove extends Move implements Comparable<LogMove> {

  protected static final String T = "t";
  protected static final String MSG = "msg";

  public static MutableList<LogMove> getPreviousMoves(JSONArray json) {
    MutableList<LogMove> moves = new FastList<>();

    Move predecessor = null;
    for (Object obj : json) {
      JSONObject jsonObj = (JSONObject) obj;
      LogMove move = new LogMove(jsonObj, predecessor);
      move.pathLen = 0;
      moves.add(move);
      predecessor = move;
    }
    return moves;
  }

  public static MutableCollection<Move> getPossibleMoves(JSONArray json, Move previous) {
    MutableCollection<Move> moves = new FastList<>();
    json.forEach(jsonObj -> moves.add(new LogMove((JSONObject) jsonObj, previous)));
    moves.forEach(move -> move.pathLen = 1);
    return moves;
  }

  private String time;
  private String msg;

  public LogMove(JSONObject json) {
    super(json.getInt(X), json.getInt(Y), json.getInt(XV), json.getInt(YV));

    time = json.optString(T);
    msg = json.optString(MSG);
  }

  protected LogMove(JSONObject json, Move predecessor) {
    this(json);

    setPredecessor(predecessor);
  }

  /**
   * Copy constructor
   */
  protected LogMove(LogMove move) {
    super(move);

    time = move.time;
    msg = move.msg;
  }

  public boolean isBefore(LogMove other) {
    if (other == null)
      return true;

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    try {
      Date thisDate = sdf.parse(time);
      Date otherDate = sdf.parse(other.time);

      return thisDate.before(otherDate);
    } catch (ParseException e) {
      return false;
    }
  }

  public String getTime() {
    return time;
  }

  public String getMessage() {
    return msg;
  }

  @Override
  public int compareTo(LogMove move) {
    return isBefore(move) ? -1 : 1;
  }

}
