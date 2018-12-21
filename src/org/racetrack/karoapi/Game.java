package org.racetrack.karoapi;

import java.util.*;
import java.util.logging.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.json.*;
import org.racetrack.rules.RuleFactory.*;

public class Game {

  public enum Field {
    id, name, map, cps, zzz, crashallowed, startdirection, started, creator, starteddate, finished, next, players, blocked, options;
  }

  public enum Dir {
    classic, formula1, free
  }

  public enum Crash {
    allowed, forbidden, free;

    public boolean isAllowed() {
      return equals(allowed) || equals(free);
    }
  }

  private static final Logger logger = Logger.getLogger(Game.class.getName());

  private static final String API_URL = "games/";
  public static final String API_ADD = "game/add.json";

  private static final String KARO_IQ_IDENTIFIER = "!KaroIQ!";

  protected static final String GAME = "game";

  public static Game fromJSON(JSONObject json) {
    Game game = new Game();
    game.refreshFromJSON(json);
    return game;
  }

  public static Game newRandom(String title, String creator, boolean withIq) {
    KaroMap map = KaroMap.getRandomHighRated();

    Dir dir = Dir.classic;
    String gameTitle = title != null ? title : map.getName();
    if (withIq) {
      gameTitle = gameTitle + " !KaroIQ!";
    }
    Game game = new Game(gameTitle, map.getId(), dir);
    User bot = User.get(creator);

    game.addPlayer(bot);
    int restPlayerCount = Math.max(Math.round(map.getPlayers() * 0.75f), map.getPlayers() - 2) - 1;

    MutableList<User> nonBlocking = User.getNewGameUsers();
    if (withIq) {
      nonBlocking = nonBlocking.select(u -> u.isWithIq());
    }
    Random random = new Random();
    while (restPlayerCount > 0) {
      if (game.addPlayer(nonBlocking.get(random.nextInt(nonBlocking.size())))) {
        restPlayerCount--;
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

  public static Game getFakeGame(KaroMap map, RuleType type, boolean cps, Dir direction, Crash crashallowed, int zzz) {
    Game game = new Game();
    final int userId = 999999;
    final int gameId = 999999;
    final String gameName = "TEST";
    game.id = 299999 + new Random().nextInt(gameId);
    game.map = map;
    game.name = gameName + " (Map: " + map.getId() + ")";
    game.startdirection = direction.toString();
    game.cps = cps;
    game.crashallowed = crashallowed.toString();
    game.zzz = zzz;

    MutableCollection<MapTile> missingCps = cps ? map.getCps() : new FastList<>(0);
    MutableList<Move> possibles = map.getTilesAsMoves(MapTile.START);
    game.next = Player.getFakePlayer(game, userId, missingCps, possibles);
    game.players = Maps.mutable.of(game.next.getId(), game.next);
    return game;
  }

  public static List<Game> getGames(JSONArray array) {
    List<Game> games = new FastList<>(0);
    if (array == null)
      return games;

    for (int i = 0; i < array.length(); i++) {
      JSONObject obj = array.getJSONObject(i);
      games.add(Game.get(obj.getInt(Field.id.toString())));
    }
    return games;
  }

  public static Game get(int id) {
    Game game = new Game();
    game.id = id;
    game.refresh();
    return game;
  }

  protected int id;
  protected String name;
  protected KaroMap map;
  protected boolean cps;
  protected int zzz;
  protected String crashallowed;
  protected String startdirection;
  protected boolean started;
  protected String starteddate;
  protected String creator;
  protected boolean finished;
  protected Player next;
  protected int blocked;
  protected Map<Integer, Player> players;
  protected KaroMapSetting settings;

  private Game() {
  }

  private Game(String name, int mapId, Dir direction) {
    this.name = name;
    map = KaroMap.get(mapId);
    startdirection = direction.toString();
    players = Maps.mutable.empty();

    cps = true;
    crashallowed = Crash.forbidden.toString();
    zzz = 2;
  }

  public Game refresh() {
    String apiUrl = API_URL + getId() + "?mapcode=1&moves=1";
    String response = KaroClient.callApi(apiUrl);

    JSONObject jsonGame = new JSONObject(response);
    return refreshFromJSON(jsonGame);
  }

  private Game refreshFromJSON(JSONObject json) {
    try {
      id = json.getInt(Field.id.toString());
      name = json.getString(Field.name.toString());
      cps = json.getBoolean(Field.cps.toString());
      zzz = json.getInt(Field.zzz.toString());
      crashallowed = json.getString(Field.crashallowed.toString());
      startdirection = json.getString(Field.startdirection.toString());
      started = json.getBoolean(Field.started.toString());
      creator = json.getString(Field.creator.toString());
      starteddate = json.getString(Field.starteddate.toString());
      finished = json.getBoolean(Field.finished.toString());
      JSONArray jPlayers = json.optJSONArray(Field.players.toString());
      if (jPlayers != null) {
        players = jPlayers != null ? Player.getPlayers(this, jPlayers) : Maps.mutable.empty();
      }
      if (!finished) {
        JSONObject nextjson = json.getJSONObject(Field.next.toString());
        next = players.get(nextjson.getInt("id"));
      }

      blocked = json.getInt(Field.blocked.toString());

      JSONObject jMap = json.getJSONObject(Field.map.toString());
      map = KaroMap.get(jMap.getInt(KaroMap.ID));
      if (map == null) {
        map = KaroMap.fromJSON(jMap);
      }
    } catch (JSONException je) {
      logger.warning("Error when parsing game: " + je.getMessage());
      throw je;
    }
    return this;
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public boolean withCps() {
    return cps;
  }

  public int getZzz() {
    return zzz;
  }

  public Player getPlayer(int userId) {
    return players.get(userId);
  }

  public Player getPlayer(User user) {
    int userId = user.getId();
    return getPlayer(userId);
  }

  public Player getNext() {
    return next;
  }

  public boolean isPlayer(User user) {
    return players.containsKey(user.getId());
  }

  public boolean isNextPlayer(Player player) {
    return getNext().equals(player);
  }

  public MutableList<Chat> getMissedMessages() {
    LogMove motion = getNext().getMotion();
    MutableList<Chat> messages = new FastList<Chat>();
    for (Player player : new FastList<>(players.values())) {
      for (LogMove move : player.getMoves().select(move -> move.compareTo(motion) > 0)) {
        if (!move.getMessage().isEmpty()) {
          Chat message = new Chat(player.getName(), move.getMessage(), move.getTime());
          messages.add(message);
        }
      }
    }
    return messages;
  }

  public MutableList<Player> getActivePlayers() {
    return new FastList<>(players.values()).select(p -> p.isActive());
  }

  public MutableList<Player> getNotYetMovedPlayers() {
    return getActivePlayers().select(p -> p.getMove(getCurrentRound()) == null);
  }

  public MutableList<Player> getNearestPlayers(Player player, int count, int maxDist) {
    MutableCollection<Move> possibles = player.getPossibles();
    int currentRound = getCurrentRound();
    MutableList<Player> nearest = getActivePlayers().select(p -> p.equals(player)
        || Math.min(player.getDist(p, currentRound - 1), p.getDist(possibles, currentRound)) <= maxDist);
    nearest.sortThis(
        (o1, o2) -> Math.min(player.getDist(o1, getCurrentRound() - 1), o1.getDist(possibles, getCurrentRound()))
            - Math.min(player.getDist(o2, getCurrentRound() - 1), o2.getDist(possibles, getCurrentRound())));
    return nearest.take(count);
  }

  public int getActivePlayersCount() {
    return new FastList<>(players.values()).count(p -> p.isActive());
  }

  public boolean wasPlayerReInLastRound() {
    int round = getCurrentRound() - 1;
    if (round < 1)
      return false;

    Player player = getNext();
    LogMove move = player.getMove(round);

    if (move != null)
      return new FastList<>(players.values()).allSatisfy(p -> p.equals(player) || move.isBefore(p.getMove(round)));
    else
      return false;
  }

  /**
   * Returns if the active player is the first player in this round.
   */
  public int getPosOfNextPlayer() {
    int round = getCurrentRound();
    return getActivePlayers().count(p -> p.getMoveCount() >= round) + 1;
  }

  public KaroMap getMap() {
    return map;
  }

  public int getCurrentRound() {
    MutableCollection<Player> activePlayers = getActivePlayers();
    if (activePlayers.isEmpty())
      return -1;
    MutableCollection<Integer> moveCounts = activePlayers.collect(each -> each.getMoveCount());
    return moveCounts.min() + 1;
  }

  public boolean isStarted() {
    return getCurrentRound() > 1;
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

  public String getStartdirection() {
    return startdirection;
  }

  public Dir getDirection() {
    return (cps && getMap().hasCps()) ? Dir.valueOf(startdirection) : Dir.classic;
  }

  private boolean addPlayer(User user) {
    return players.put(user.getId(), user.asPlayer()) == null;
  }

  public KaroMapSetting getSetting() {
    if (settings == null) {
      settings = KaroMapSetting.readSettings(map.getId());
    }
    return settings;
  }

  public String asJSON() {
    JSONObject game = new JSONObject();
    game.put(Field.name.toString(), name);
    game.put(Field.startdirection.toString(), startdirection);
    game.put(Field.players.toString(), players.keySet());
    game.put(Field.map.toString(), String.valueOf(map.getId()));
    JSONObject options = new JSONObject();
    options.put(Field.startdirection.toString(), startdirection);
    options.put("withCheckpoints", cps); // workaround for old API
    options.put(Field.zzz.toString(), zzz);
    options.put(Field.crashallowed.toString(), crashallowed);
    game.put(Field.options.toString(), options);

    return game.toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(Field.id.toString()).append(":").append(id);
    sb.append(" ").append(name);
    sb.append(" ").append(players);
    return sb.toString();
  }

}
