package org.racetrack.karoapi;

import java.util.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.json.*;

public class Game implements Comparable<Game> {

  public enum Dir {
    classic, formula1, free
  }

  public enum Crash {
    allowed, forbidden, free;

    public boolean isAllowed() {
      return equals(allowed) || equals(free);
    }
  }

  private static final String API_URL = "games/";
  private static final String API_INFO = "info";
  private static final String API_DETAIL = "details";
  public static final String API_ADD = "game/add.json";

  private static final String KARO_IQ_IDENTIFIER = "!KaroIQ!";

  protected static final String GAME = "game";
  protected static final String ID = "id";
  protected static final String NAME = "name";
  protected static final String MAP = "map";
  protected static final String WITH_CHECKPOINTS = "withCheckpoints";
  protected static final String ZZZ = "zzz";
  protected static final String CRASHALLOWED = "crashallowed";
  protected static final String STARTDIRECTION = "startdirection";
  protected static final String STARTED = "started";
  protected static final String CREATOR = "creator";
  protected static final String CREATED = "created";
  protected static final String FINISHED = "finished";
  protected static final String DRAN_ID = "dranId";
  protected static final String DRAN = "dran";
  protected static final String BLOCKED = "blocked";
  protected static final String PREVIEW = "preview";
  protected static final String LOCATION = "location";
  protected static final String PLAYERS = "players";
  protected static final String OPTIONS = "options";

  public static Game fromJSONString(String jsonString) {
    JSONObject json = new JSONObject(jsonString);
    return Game.fromJSON(json);
  }

  public static Game fromJSON(JSONObject json) {
    return new Game(json);
  }

  public static Game newRandom(String title, String creator, String challenger) {
    KaroMap map = KaroMap.getRandomHighRated();

    Dir dir = // map.isCircuit() && map.hasCps() && Math.random() > 0.5d ? Dir.formula1 :
        Dir.classic;
    Game game = new Game(title != null ? title : map.getName(), map.getId(), dir);
    User bot = User.get(creator);

    game.addPlayer(bot);
    int restPlayerCount = Math.max(Math.round(map.getPlayers() * 0.75f), map.getPlayers() - 2) - 1;
    if (challenger != null) {
      User chall = User.get(challenger);
      game.addPlayer(chall);
      restPlayerCount--;
    }

    List<User> desperates = User.getNonBlocking();
    Random random = new Random();
    while (restPlayerCount > 0) {
      User player = desperates.get(random.nextInt(desperates.size()));
      if (!game.isPlayer(player) && !player.isBot()) {
        if (game.addPlayer(player)) {
          restPlayerCount--;
        }
      }
    }
    return game;
  }

  public static Game newWith(String title, String creator, List<String> players) {
    KaroMap map = KaroMap.getRandomHighRated();

    Game game = new Game(title, map.getId(), Dir.classic);
    User bot = User.get(creator);
    game.addPlayer(bot);

    for (String playerLogin : players) {
      User player = User.get(playerLogin);
      if (player != null && player.isInvitable()) {
        game.addPlayer(player);
      }
    }
    return game;
  }

  public static Game get(int id) {
    Game game = new Game();
    game.id = id;
    game.readGame(true);
    return game;
  }

  protected int id;
  protected String name;
  protected int mapId;
  protected boolean withCheckpoints;
  protected int zzz;
  protected String crashallowed;
  protected String startdirection;
  protected boolean started;
  protected String creator;
  protected String created;
  protected boolean finished;
  protected int dranId;
  protected String dran;
  protected int blocked;
  protected String preview;
  protected String location;
  protected Map<Integer, Player> players;
  protected KaroMap map;
  protected KaroMapSetting settings;

  protected Game() {
  }

  protected Game(Game game) {
    id = game.id;
    name = game.name;
    mapId = game.mapId;
    withCheckpoints = game.withCheckpoints;
    zzz = game.zzz;
    crashallowed = game.crashallowed;
    startdirection = game.startdirection;
    started = game.started;
    creator = game.creator;
    created = game.created;
    finished = game.finished;
    dranId = game.dranId;
    dran = game.dran;
    blocked = game.blocked;
    preview = game.preview;
    location = game.location;
    players = game.players;
    map = game.map;
    settings = game.settings;
  }

  private Game(JSONObject json) {
    id = json.getInt(ID);
    name = json.getString(NAME);
    mapId = json.getInt(MAP);
    withCheckpoints = json.getBoolean(WITH_CHECKPOINTS);
    zzz = json.getInt(ZZZ);
    crashallowed = json.getString(CRASHALLOWED);
    startdirection = json.getString(STARTDIRECTION);
    started = json.getBoolean(STARTED);
    creator = json.getString(CREATOR);
    created = json.getString(CREATED);
    finished = json.getBoolean(FINISHED);
    dranId = json.getInt(DRAN_ID);
    dran = json.getString(DRAN);
    blocked = json.getInt(BLOCKED);
    preview = json.getString(PREVIEW);
    location = json.getString(LOCATION);
  }

  public Game(JSONObject jGame, JSONArray jPlayers, JSONObject jMap) {
    this(jGame);

    if (jPlayers != null) {
      players = Player.getPlayers(jPlayers);
    }
    if (jMap != null) {
      map = KaroMap.fromJSON(jMap);
    }
  }

  private Game(String name, int mapId, Dir direction) {
    this.name = name;
    this.mapId = mapId;
    startdirection = direction.toString();
    players = Maps.mutable.empty();

    withCheckpoints = true;
    setCrashallowed(Crash.forbidden);
    zzz = 2;
  }

  public void readGame(boolean detail) {
    StringBuilder apiUrl = new StringBuilder(API_URL).append(getId()).append("/")
        .append(detail ? API_DETAIL : API_INFO);
    String response = KaroClient.callApi(apiUrl.toString());

    JSONObject json = new JSONObject(response);

    JSONObject jGame = json.getJSONObject(GAME);
    id = jGame.getInt(ID);
    name = jGame.getString(NAME);
    mapId = jGame.getInt(MAP);
    withCheckpoints = jGame.getBoolean(WITH_CHECKPOINTS);
    zzz = jGame.getInt(ZZZ);
    crashallowed = jGame.getString(CRASHALLOWED);
    startdirection = jGame.getString(STARTDIRECTION);
    started = jGame.getBoolean(STARTED);
    creator = jGame.getString(CREATOR);
    created = jGame.getString(CREATED);
    finished = jGame.getBoolean(FINISHED);
    dranId = jGame.getInt(DRAN_ID);
    dran = jGame.getString(DRAN);
    blocked = jGame.getInt(BLOCKED);
    preview = jGame.getString(PREVIEW);
    location = jGame.getString(LOCATION);

    JSONArray jPlayers = json.getJSONArray(PLAYERS);
    players = jPlayers != null ? Player.getPlayers(jPlayers) : Maps.mutable.empty();

    JSONObject jMap = json.getJSONObject(MAP);
    mapId = jMap.getInt(KaroMap.ID);
    map = KaroMap.get(mapId);
    if (map == null) {
      map = KaroMap.fromJSON(jMap);
    }
  }

  public void refresh() {
    StringBuilder apiUrl = new StringBuilder(API_URL).append(getId()).append("/").append(API_INFO);
    String response = KaroClient.callApi(apiUrl.toString());

    JSONObject json = new JSONObject(response);
    JSONObject jGame = json.getJSONObject(GAME);
    if (jGame != null) {
      id = jGame.getInt(ID);
      name = jGame.getString(NAME);
      mapId = jGame.getInt(MAP);
      withCheckpoints = jGame.getBoolean(WITH_CHECKPOINTS);
      zzz = jGame.getInt(ZZZ);
      crashallowed = jGame.getString(CRASHALLOWED);
      startdirection = jGame.getString(STARTDIRECTION);
      started = jGame.getBoolean(STARTED);
      creator = jGame.getString(CREATOR);
      created = jGame.getString(CREATED);
      finished = jGame.getBoolean(FINISHED);
      dranId = jGame.getInt(DRAN_ID);
      dran = jGame.getString(DRAN);
      blocked = jGame.getInt(BLOCKED);
      preview = jGame.getString(PREVIEW);
      location = jGame.getString(LOCATION);
    }
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public boolean isWithCheckpoints() {
    return withCheckpoints;
  }

  public int getZzz() {
    return zzz;
  }

  protected void setCrashallowed(Crash crashallowed) {
    this.crashallowed = crashallowed.toString();
  }

  public String getDran() {
    return dran;
  }

  public MutableList<Player> getPlayers() {
    if (players == null) {
      readGame(true);
    }
    return new FastList<>(players.values());
  }

  public Player getPlayer(int userId) {
    if (players == null) {
      readGame(true);
    }
    return players.get(userId);
  }

  public Player getPlayer(User user) {
    int userId = user.getId();
    return getPlayer(userId);
  }

  public boolean isTheOnlyHuman(User user) {
    if (user == null || user.isBot())
      return false;
    return getActivePlayers().reject(p -> p.getId() == user.getId()).allSatisfy(p -> p.isBot());
  }

  public Player getDranPlayer() {
    return getPlayer(dranId);
  }

  public boolean isPlayer(User user) {
    return players.containsKey(user.getId());
  }

  public boolean isDranPlayer(Player player) {
    return getDranPlayer().equals(player);
  }

  public MutableList<Chat> getMissedMessages() {
    LogMove lastMove = getDranPlayer().getLastmove();
    MutableList<Chat> messages = new FastList<Chat>();
    for (Player player : getPlayers()) {
      for (LogMove move : player.getMoves().select(move -> move.compareTo(lastMove) > 0)) {
        if (!move.getMessage().isEmpty()) {
          Chat message = new Chat(player.getName(), move.getMessage(), move.getTime());
          messages.add(message);
        }
      }
    }
    return messages;
  }

  public MutableList<Player> getActivePlayers() {
    return getPlayers().select(p -> p.isActive() && !p.hasFinished());
  }

  public MutableList<Player> getAlreadyMovedPlayers() {
    return getActivePlayers().select(p -> p.getMove(getCurrentRound()) != null);
  }

  public MutableList<Player> getNotYetMovedPlayers() {
    return getActivePlayers().select(p -> p.getMove(getCurrentRound()) == null);
  }

  public MutableList<Player> getNearbyPlayers(Player player, int dist) {
    MutableCollection<Move> possibles = player.getPossibles();
    return getActivePlayers()
        .select(p -> player.isNearby(p, getCurrentRound() - 1, dist) || p.isNearby(possibles, getCurrentRound(), dist));
  }

  public int getActivePlayersCount() {
    return getPlayers().count(p -> p.isActive());
  }

  public boolean wasPlayerReInLastRound() {
    int round = getCurrentRound() - 1;
    if (round < 1)
      return false;

    Player player = getDranPlayer();
    LogMove move = player.getMove(round);

    if (move != null)
      return getPlayers().allSatisfy(p -> p.equals(player) || move.isBefore(p.getMove(round)));
    else
      return false;
  }

  /**
   * Returns if the active player is the first player in this round.
   */
  public int getPosInRoundOfCurrentPlayer() {
    int round = getCurrentRound();
    return getActivePlayers().count(p -> p.getMoveCount() >= round) + 1;
  }

  public int getMapId() {
    return mapId;
  }

  public KaroMap getMap() {
    if (map == null) {
      map = KaroMap.get(mapId);
      if (map.isInNight()) {
        readGame(true);
      }
    }
    return map;
  }

  public int getCurrentRound() {
    MutableCollection<Player> activePlayers = getActivePlayers();
    if (activePlayers.isEmpty())
      return -1;
    int maxMoveCount = activePlayers.maxBy(player -> player.getMoveCount()).getMoveCount();
    if (activePlayers.anySatisfy(player -> player.getMoveCount() != maxMoveCount))
      return maxMoveCount - 1;
    return maxMoveCount;
  }

  public boolean isStarted() {
    return getCurrentRound() > 0;
  }

  public boolean isWithIq() {
    return name.contains(KARO_IQ_IDENTIFIER);
  }

  public boolean isCrashAllowed() {
    return Crash.valueOf(crashallowed).isAllowed();
  }

  public boolean isFormula1() {
    return getDirection().equals(Dir.formula1);
  }

  public boolean isClassic() {
    return getDirection().equals(Dir.classic);
  }

  public Dir getDirection() {
    return (withCheckpoints && getMap().hasCps()) ? Dir.valueOf(startdirection) : Dir.classic;
  }

  private boolean addPlayer(User player) {
    return players.put(player.getId(), player.asPlayer()) == null;
  }

  public KaroMapSetting getSetting() {
    if (settings == null) {
      settings = KaroMapSetting.readSettings(mapId);
    }
    return settings;
  }

  public String asJSON() {
    JSONObject game = new JSONObject();
    game.put(NAME, name);
    game.put(STARTDIRECTION, startdirection);
    game.put(PLAYERS, players.keySet());
    game.put(MAP, String.valueOf(mapId));
    JSONObject options = new JSONObject();
    options.put(STARTDIRECTION, startdirection);
    options.put(WITH_CHECKPOINTS, withCheckpoints);
    options.put(ZZZ, zzz);
    options.put(CRASHALLOWED, crashallowed);
    game.put(OPTIONS, options);

    return game.toString();
  }

  @Override
  public int compareTo(Game o) {
    return id - o.id;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(ID).append(":").append(id);
    sb.append(" ").append(name);
    return sb.toString();
  }

}
