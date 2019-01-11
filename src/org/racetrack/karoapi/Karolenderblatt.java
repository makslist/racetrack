package org.racetrack.karoapi;

import java.text.*;
import java.util.*;
import java.util.logging.*;

import org.json.*;

public class Karolenderblatt {

  private static final String API_MAP = "karolenderblatt";

  protected static final String POSTED = "posted";
  private static final String LINE = "line";

  private static DateFormat df = new SimpleDateFormat("yyyy-MM-dd");

  private static final Logger logger = Logger.getLogger(Karolenderblatt.class.toString());

  public static Karolenderblatt getToday() {
    return Karolenderblatt.get(new Date());
  }

  public static Karolenderblatt get(Date date) {
    String blatt = KaroClient.callApi(Karolenderblatt.API_MAP + "/" + df.format(date));
    try {
      JSONArray array = new JSONArray(blatt);
      return new Karolenderblatt(array);
    } catch (JSONException jse) {
      logger.severe("Reading Karolenderblatt failed: " + jse.getMessage());
    }
    return null;
  }

  public static Karolenderblatt fromJSON(JSONArray json) {
    return new Karolenderblatt(json);
  }

  private Date posted;
  private String line;

  private Karolenderblatt(JSONArray array) {
    JSONObject obj = array.getJSONObject(0);

    try {
      posted = df.parse(obj.getString(POSTED));
    } catch (JSONException | ParseException e) {
      e.printStackTrace();
    }
    line = obj.getString(LINE);
  }

  public Date getPosted() {
    return posted;
  }

  public String getLine() {
    return line;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(POSTED).append(":").append(df.format(posted)).append("\n");
    sb.append(LINE).append(":").append(line).append("\n");
    return sb.toString();
  }

}
