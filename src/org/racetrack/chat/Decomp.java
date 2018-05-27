package org.racetrack.chat;

import java.util.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.json.*;

public class Decomp {

  private static final String REASEMB = "reasemb";
  private static final String PATTERN = "pattern";

  /** The decomp pattern */
  private String pattern;
  /** The reassembly list */
  private MutableList<String> reasemb;

  public Decomp(String pattern, boolean mem, MutableList<String> reasemb) {
    this.pattern = pattern;
    this.reasemb = reasemb;
  }

  public Decomp(String pattern, boolean mem) {
    this.pattern = pattern;
    reasemb = new FastList<>(0);
  }

  public Decomp(JSONObject obj) {
    pattern = obj.getString(PATTERN).toLowerCase();
    reasemb = new FastList<>();
    JSONArray array = obj.getJSONArray(REASEMB);
    for (int i = 0; i < array.length(); i++) {
      reasemb.add(array.getString(i));
    }
  }

  public Decomp addReasmb(String reasmb) {
    reasemb.add(reasmb);
    return this;
  }

  public String pattern() {
    return pattern;
  }

  public String nextRule() {
    if (reasemb.isEmpty()) {
      System.out.println("No reassembly rule.");
      return null;
    }
    return reasemb.get(new Random().nextInt(reasemb.size()));
  }

  public JSONObject toJSONObject() {
    JSONObject json = new JSONObject();
    json.put(PATTERN, pattern);
    json.put(REASEMB, reasemb);
    return json;
  }

  @Override
  public String toString() {
    return new StringBuilder().append(pattern).append(" ").append(reasemb).toString();
  }

}
