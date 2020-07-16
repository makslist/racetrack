package org.racetrack.chat;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.list.mutable.*;

import com.github.openjson.*;

public class Key implements Comparable<Key> {

  private static final String KEY = "key";
  private static final String RANK = "rank";
  private static final String DECOMB = "decomb";

  private String key;
  private int rank;
  private MutableList<Decomp> decomp;

  public Key(String key, int rank, MutableList<Decomp> decomp) {
    this.key = key;
    this.rank = rank;
    this.decomp = decomp;
  }

  public Key(String key, int rank) {
    this.key = key;
    this.rank = rank;
    decomp = new FastList<>(0);
  }

  public Key(JSONObject obj) {
    key = obj.getString(KEY).toLowerCase();
    rank = obj.getInt(RANK);
    decomp = new FastList<>();
    JSONArray array = obj.getJSONArray(DECOMB);
    for (int i = 0; i < array.length(); i++) {
      decomp.add(new Decomp(array.getJSONObject(i)));
    }
  }

  public Key mergeDecomps(Key other) {
    rank = rank < other.rank ? rank : other.rank;
    decomp.addAll(other.decomp);
    return this;
  }

  public String key() {
    return key;
  }

  public Key addDecomp(Decomp decomp) {
    this.decomp.add(decomp);
    return this;
  }

  public MutableList<Decomp> getDecomps() {
    return decomp;
  }

  public JSONObject toJSONObject() {
    JSONObject json = new JSONObject();
    json.put(KEY, key);
    json.put(RANK, rank);
    json.put(DECOMB, new JSONArray(decomp.collect(decomp -> decomp.toJSONObject())));
    return json;
  }

  @Override
  public String toString() {
    return key;
  }

  @Override
  public boolean equals(Object o) {
    Key other = (Key) o;
    return key.equals(other.key);
  }

  @Override
  public int compareTo(Key key) {
    return rank - key.rank;
  }

}
