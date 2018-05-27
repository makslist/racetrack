package org.racetrack.karoapi;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.logging.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.*;
import org.eclipse.collections.api.set.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.json.*;

public class KaroMap {

  private static final String API_MAP = "map";
  private static final String API_MAP_LIST = "list.json";

  protected static final String ID = "id";
  private static final String NAME = "name";
  private static final String AUTHOR = "author";
  private static final String COLS = "cols";
  private static final String ROWS = "rows";
  private static final String RATING = "rating";
  private static final String PLAYERS = "players";
  private static final String MAPCODE = "mapcode";
  private static final String CPS = "cps";
  private static final String ACTIVE = "active";

  private static final Logger logger = Logger.getLogger(KaroMap.class.toString());

  public static Comparator<KaroMap> ratingComparator = (KaroMap m1,
      KaroMap m2) -> (int) (m2.getRating() * 100 - m1.getRating() * 100);

  private static MutableMap<Integer, KaroMap> cachedMaps = Maps.mutable.empty();

  private static final File getFile(int id, File dir) {
    return new File(dir, id + ".json");
  }

  private static File getCacheDir() {
    CodeSource codeSource = KaroMap.class.getProtectionDomain().getCodeSource();
    try {
      File jarFile = new File(codeSource.getLocation().toURI().getPath());
      File jarDir = jarFile.getParentFile();
      File cacheDir = new File(jarDir, "mapcache/");
      return cacheDir;
    } catch (URISyntaxException use) {
      logger.severe(use.getMessage());
    }
    return null;
  }

  public static KaroMap get(int id) {
    String mapString = readMap(id);
    try {
      return KaroMap.fromJSONString(mapString);
    } catch (JSONException jse) {
      System.out.println("JSONException: " + jse.getMessage());
    }
    return null;
  }

  public static KaroMap get(File mapFile) {
    String mapString = readFile(mapFile);
    try {
      return KaroMap.fromJSONString(mapString);
    } catch (JSONException jse) {
      System.out.println("JSONException: " + jse.getMessage());
      System.exit(1);
    }
    return null;
  }

  private static String readMap(int id) {
    File cacheDir = getCacheDir();
    if (!cacheDir.exists()) {
      cacheDir.mkdir();
    }

    File mapFile = getFile(id, cacheDir);
    if (mapFile.exists())
      return readFile(mapFile);
    else {
      try {
        String mapString = KaroClient.callApi(KaroMap.API_MAP + "/" + id);

        mapFile.createNewFile();
        Writer wr = new FileWriter(mapFile);
        wr.write(mapString);
        wr.close();

        return mapString;
      } catch (IOException ioe) {
        logger.severe(ioe.getMessage());
        return null;
      }
    }
  }

  private static String readFile(File mapFile) {
    String mapString = null;
    try {
      BufferedReader reader;
      reader = new BufferedReader(new FileReader(mapFile));

      StringBuilder sb = new StringBuilder();
      String line;
      line = reader.readLine();
      while (line != null) {
        sb.append(line);
        sb.append(System.lineSeparator());
        line = reader.readLine();
      }
      reader.close();
      mapString = sb.toString();
    } catch (FileNotFoundException e) {
      logger.severe("File " + mapFile.getName() + " does not exists. " + e.getMessage());
      System.exit(1);
    } catch (IOException e1) {
      logger.severe("IOException when calling KaroApi: " + e1.getMessage());
    }
    return mapString;
  }

  private static void readMaps() {
    String apiResponse = KaroClient.callApi(API_MAP + "/" + API_MAP_LIST);

    JSONArray array = new JSONArray(apiResponse);
    for (int i = 0; i < array.length(); i++) {
      KaroMap map = KaroMap.fromJSON((JSONObject) array.get(i));
      cachedMaps.put(map.getId(), map);
    }
  }

  public static MutableList<KaroMap> getAll() {
    if (cachedMaps.isEmpty()) {
      readMaps();
    }
    return cachedMaps.toList();
  }

  public static KaroMap getRandomHighRated() {
    MutableList<KaroMap> higherRatedMaps = getAll()
        .select(map -> map.rating > 3.3 && map.getPlayers() >= 3 && map.getId() < 1000);
    return higherRatedMaps.get(new Random().nextInt(higherRatedMaps.size()));
  }

  public static KaroMap fromJSONString(String jsonString) {
    JSONObject json = new JSONObject(jsonString);
    return KaroMap.fromJSON(json);
  }

  public static KaroMap fromJSON(JSONObject json) {
    return new KaroMap(json);
  }

  private int id;
  private String name;
  private String author;
  private int cols;
  private int rows;
  private double rating;
  private int players;
  private String mapcode;
  private char[][] map;
  private Set<MapTile> cps = Sets.mutable.empty();
  private boolean active = true;
  private KaroMapSetting settings;

  public KaroMap(String mapcode) {
    this.mapcode = mapcode;
    map = readMapFromMapcode();
    cols = map[0].length;
    rows = map.length;
  }

  private KaroMap(JSONObject json) {
    id = json.optInt(ID);
    name = json.optString(NAME);
    author = json.optString(AUTHOR);
    rating = json.optDouble(RATING);
    players = json.optInt(PLAYERS);
    mapcode = json.optString(MAPCODE);
    map = readMapFromMapcode();
    cols = Integer.max(json.optInt(COLS), map[0].length);
    rows = Integer.max(json.optInt(ROWS), map.length);
    JSONArray cpArray = json.optJSONArray(CPS);
    if (cpArray != null) {
      for (int i = 0; i < cpArray.length(); i++) {
        MapTile cp = MapTile.valueOf(cpArray.getInt(i));
        if (getTilesAsMoves(cp).isEmpty()) {
          System.out.println("Checkpoint " + cp + " does not exist on map " + id + ".");
        } else {
          cps.add(cp);
        }
      }
    }
    active = json.optBoolean(ACTIVE);
  }

  public JSONObject toJSONObject() {
    JSONObject json = new JSONObject();

    saveMapToMapcode();
    json.put(ID, id);
    json.put(NAME, name);
    json.put(AUTHOR, author);
    json.put(COLS, cols);
    json.put(ROWS, rows);
    json.put(RATING, rating);
    json.put(PLAYERS, players);
    json.put(MAPCODE, mapcode);
    JSONArray cpArray = new JSONArray();
    for (MapTile cp : cps) {
      cpArray.put(cp.asString());
    }
    json.put(CPS, cpArray);
    json.put(ACTIVE, active);

    return json;
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getAuthor() {
    return author;
  }

  public int getCols() {
    return cols;
  }

  public int getRows() {
    return rows;
  }

  public double getRating() {
    return rating;
  }

  public int getPlayers() {
    return players;
  }

  public String getMapcode() {
    return mapcode;
  }

  public MutableSet<MapTile> getCps() {
    return Sets.mutable.withAll(cps);
  }

  public boolean isActive() {
    return active;
  }

  public boolean hasCps() {
    return !cps.isEmpty();
  }

  public Boolean isCircuit() {
    return getSetting().isCircuit();
  }

  public void setCircuit(boolean circuit) {
    getSetting().setCircuit(circuit);
  }

  public int getMaxTours() {
    return getSetting().getMaxTours();
  }

  public void setMaxTours(int maxTours) {
    getSetting().setMaxTours(maxTours);
  }

  public boolean isQuit() {
    return getSetting().isQuit();
  }

  public void setQuit(boolean quit) {
    getSetting().setQuit(quit);
  }

  public boolean isSettingSet() {
    return getSetting().isSet();
  }

  public KaroMapSetting getSetting() {
    if (settings == null) {
      settings = KaroMapSetting.readSettings(id);
    }
    return settings;
  }

  public void saveSettings() {
    settings.writeSettings();
  }

  private char[][] readMapFromMapcode() {
    String[] split = mapcode.split("\\R|(/n/r|/n)");
    char[][] map = new char[split.length][];
    for (int i = 0; i < split.length; i++) {
      map[i] = split[i].toCharArray();
    }
    return map;
  }

  private void saveMapToMapcode() {
    StringBuilder sb = new StringBuilder();
    for (int j = 0; j < getRows(); j++) {
      for (int i = 0; i < getCols(); i++) {
        sb.append(getTileOf(i, j).asString());
      }
      if (j != getRows() - 1) {
        sb.append('\r').append('\n');
      }
    }
    mapcode = sb.toString();
  }

  private void writeToFile() {
    File mapFile = null;
    try {
      File dir = getCacheDir();
      mapFile = getFile(id, dir);

      if (mapFile != null) {
        if (mapFile.exists()) {
          mapFile.delete();
        }

        mapFile.createNewFile();
        Writer wr = new FileWriter(mapFile);
        String jsonMap = toJSONObject().toString();
        wr.write(jsonMap);
        wr.close();
      }
    } catch (IOException ioe) {
      logger.severe(ioe.getMessage());
    }
  }

  public boolean isInNight() {
    return !getTilesAsMoves(MapTile.NIGHT).isEmpty();
  }

  public void mergeNightMap(KaroMap lightMap) {
    boolean mapChanged = false;
    for (int j = 0; j < getRows(); j++) {
      for (int i = 0; i < getCols(); i++) {
        if (getTileOf(i, j).equals(MapTile.NIGHT)) {
          MapTile lightUp = lightMap.getTileOf(i, j);
          if (!lightUp.equals(MapTile.NIGHT)) {
            setTileOf(i, j, lightUp);
            mapChanged = true;
          }
        }
      }
    }
    if (mapChanged) {
      writeToFile();
    }
  }

  public boolean contains(Move move) {
    return contains(move.getX(), move.getY());
  }

  public boolean contains(int x, int y) {
    return (x >= 0 && x < getCols() && y >= 0 && y < getRows());
  }

  public boolean isCpClustered(MapTile cp) {
    MutableList<Move> tileMoves = getTilesAsMoves(cp);
    MutableCollection<MutableCollection<Move>> clusters = new FastList<>(0);

    while (!tileMoves.isEmpty()) {
      MutableCollection<Move> cluster = new FastList<>();
      cluster.add(tileMoves.remove(0));

      for (Move move : tileMoves) {
        for (Move clusterMove : cluster) {
          if (move.getDist(clusterMove) <= 3) {
            cluster.add(move);
            break;
          }
        }
      }
      tileMoves.removeAll(cluster);
      if (isNeighborTilesDriveable(cluster)) {
        clusters.add(cluster);
      }
    }

    return clusters.size() == 1;
  }

  private boolean isNeighborTilesDriveable(Collection<Move> moves) {
    for (Move move : moves) {
      for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
          int x = move.getX() + i, y = move.getY() + j;
          if (contains(x, y) && MapTile.REACHABLE_NEIGHBORS_FOR_FINISH.contains(getTileOf(x, y)))
            return true;
        }
      }
    }
    return false;
  }

  public boolean isMoveNeighborOf(Move move, Collection<MapTile> tiles) {
    for (int i = -1; i <= 1; i++) {
      for (int j = -1; j <= 1; j++) {
        if (tiles.contains(getTileOf(move.x + i, move.y + j)))
          return true;
      }
    }
    return false;
  }

  public MutableList<Move> getTilesAsMoves(MapTile tile) {
    MutableList<Move> tiles = new FastList<>(0);
    for (int j = 0; j < getRows(); j++) {
      for (int i = 0; i < getCols(); i++) {
        if (tile.equals(getTileOf(i, j))) {
          tiles.add(new Move(i, j, 0, 0, null));
        }
      }
    }
    return tiles;
  }

  public MapTile getTileOf(int x, int y) {
    return MapTile.valueOf(map[y][x]);
  }

  private void setTileOf(int x, int y, MapTile tile) {
    map[y][x] = tile.asChar();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(ID).append(":").append(id).append("\n");
    sb.append(NAME).append(":").append(name).append("\n");
    sb.append(AUTHOR).append(":").append(author).append("\n");
    sb.append(COLS).append(":").append(cols).append("\n");
    sb.append(ROWS).append(":").append(rows).append("\n");
    sb.append(RATING).append(":").append(rating).append("\n");
    sb.append(PLAYERS).append(":").append(players).append("\n");
    sb.append(MAPCODE).append(":").append(mapcode).append("\n");
    sb.append(CPS).append(":").append(cps.toString()).append("\n");
    return sb.toString();
  }

}
