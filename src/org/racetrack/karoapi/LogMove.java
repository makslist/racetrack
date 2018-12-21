package org.racetrack.karoapi;

import java.text.*;
import java.util.*;

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

  private String time;
  private String msg;

  public LogMove(JSONObject json) {
    super(json.getInt(X), json.getInt(Y), json.getInt(XV), json.getInt(YV));

    time = json.optString(T);
    msg = json.optString(MSG);
    crash = json.optInt(CRASH) == 1 ? true : false;
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
  public boolean equals(Object obj) {
    if (obj instanceof LogMove) {
      LogMove move = (LogMove) obj;
      return x == move.x && y == move.y && xv == move.xv && yv == move.yv && (time != null && time.equals(move.time));
    } else {
      Move move = (Move) obj;
      return move.equals(this);
    }
  }

  @Override
  public int compareTo(LogMove move) {
    return isBefore(move) ? -1 : 1;
  }

}
