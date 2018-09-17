package org.racetrack.karoapi;

import java.util.*;
import java.util.logging.*;
import java.util.regex.*;

import org.eclipse.collections.api.block.predicate.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.*;
import org.eclipse.collections.api.set.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.json.*;

public class User {

  private static final String API_URL_GAMES = "games?user=";
  private static final String API_URL_FINISHED = "&finished=true";

  private static final String API_USER = "user";
  private static final String API_USERS = "users";
  private static final String API_USER_DRAN = "dran";
  public static final String CHECK = API_USER + "/" + "check";

  private static final String WIKI_IQ_API_PARAM = "action=parse&format=json&page=KaroIQ&prop=links&section=4";
  private static final String WIKI_RE_API_PARAM = "action=parse&format=json&page=Einladeraum&prop=sections";
  private static final String WIKI_RE_USERS_API_URL = "action=parse&format=json&page=Einladeraum&prop=links&section=";

  private static final String ID = "id";
  private static final String LOGIN = "login";
  private static final String COLOR = "color";
  private static final String LAST_VISIT = "lastVisit";
  private static final String SIGNUP = "signup";
  private static final String DRAN = "dran";
  private static final String ACTIVE_GAMES = "activeGames";
  private static final String ACCEPTS_DAY_GAMES = "acceptsDayGames";
  private static final String ACCEPTS_NIGHT_GAMES = "acceptsNightGames";
  private static final String MAX_GAMES = "maxGames";
  private static final String SOUND = "sound";
  private static final String SOUNDFILE = "soundfile";
  private static final String SIZE = "size";
  private static final String BORDER = "border";
  private static final String DESPERATE = "desperate";
  private static final String BIRTHDAY_TODAY = "birthdayToday";
  private static final String KARODAY_TODAY = "karodayToday";
  private static final String BOT = "bot";

  private static final Logger logger = Logger.getLogger(User.class.toString());

  private static MutableMap<String, User> users = Maps.mutable.empty();
  private static MutableSet<String> iqUsers = Sets.mutable.empty();
  private static MutableSet<String> reUsers = Sets.mutable.empty();

  public static final Predicate<User> bots = user -> user.isBot();
  public static final Predicate<User> humans = user -> !user.isBot();

  public static final User get(int id) {
    if (users.isEmpty()) {
      readUsers();
    } else if (users.toSet().noneSatisfy(user -> user.id == id)) {
      readUser(id);
    }
    return users.toSet().detect(user -> user.id == id);
  }

  public static final User get(String login) {
    if (users.isEmpty() || !users.containsKey(login.toLowerCase())) {
      readUsers();
    }
    return users.getOrDefault(login.toLowerCase(), null);
  }

  private static final User readUser(int id) {
    try {
      User user = User.fromJSONString(KaroClient.callApi(API_USER + "/" + id));
      users.put(user.login, user);
      return user;
    } catch (JSONException e) {
      logger.severe("JSONException while parsing: " + e.getMessage());
    }
    return null;
  }

  private static final void readUsers() {
    try {
      users.clear();
      String json = KaroClient.callApi(API_USERS);
      if (json != null) {
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
          User user = User.fromJSON((JSONObject) array.get(i));
          users.put(user.login.toLowerCase(), user);
        }
      }
    } catch (JSONException e) {
      logger.severe("JSONException while parsing: " + e.getMessage());
    }
  }

  private static final void readIqUsers() {
    try {
      String json = KaroClient.callWiki(WIKI_IQ_API_PARAM);
      if (json != null) {
        JSONArray links = new JSONObject(json).getJSONObject("parse").getJSONArray("links");
        iqUsers.addAll(getoginsFromWiki(links));
      }
    } catch (JSONException e) {
      logger.severe("JSONException while parsing: " + e.getMessage());
    }
  }

  private static final void readReUsers() {
    try {
      String json = KaroClient.callWiki(WIKI_RE_API_PARAM);
      if (json != null) {
        List<Integer> indices = new FastList<>();
        JSONArray sections = new JSONObject(json).getJSONObject("parse").getJSONArray("sections");
        for (int i = 0; i < sections.length(); i++) {
          JSONObject section = (JSONObject) sections.get(i);
          String line = section.getString("line");
          if (line.contains("Rundenerster wiederholt letzten Zug") || line.contains("REmulAde")) {
            Integer index = section.getInt("index");
            indices.add(index);
          }
        }
        for (Integer index : indices) {
          json = KaroClient.callWiki(WIKI_RE_USERS_API_URL + index);
          if (json != null) {
            JSONArray links = new JSONObject(json).getJSONObject("parse").getJSONArray("links");
            reUsers.addAll(getoginsFromWiki(links));
          }
        }
      }
    } catch (JSONException e) {
      logger.severe("JSONException while parsing: " + e.getMessage());
    }
  }

  private static final List<String> getoginsFromWiki(JSONArray links) {
    List<String> logins = new FastList<>();
    if (links != null) {
      Pattern usersPattern = Pattern.compile("Benutzer:(?<login>[a-zA-Z0-9]+)");
      for (int i = 0; i < links.length(); i++) {
        String benutzer = ((JSONObject) links.get(i)).getString("*");
        Matcher matcher = usersPattern.matcher(benutzer);
        if (matcher.find()) {
          logins.add(matcher.group("login"));
        }
      }
    }
    return logins;
  }

  public static final MutableList<User> getActive() {
    return getAll().select(user -> user.isActive() && user.isInvitable());
  }

  public static final List<User> getNonBlocking() {
    return getAll().select(user -> user.isActive() && user.isInvitable() && !user.isBlocking());
  }

  public static final List<User> getDesperates() {
    return getActive()
        .select(user -> user.isDesperate() && user.isActive() && user.isInvitable() && !user.isBlocking());
  }

  public static final MutableList<User> getBirthdayKids() {
    readUsers();
    return getAll().select(user -> user.isActive() && user.hasBirthday());
  }

  public static final MutableList<User> getKaroKids() {
    readUsers();
    return getAll().select(user -> user.isActive() && user.hasKaroday());
  }

  public static final MutableList<User> getIqs() {
    if (iqUsers.isEmpty()) {
      readIqUsers();
    }
    return getAll().select(user -> user.isWithIq());
  }

  public static final MutableList<User> getRes() {
    if (reUsers.isEmpty()) {
      readReUsers();
    }
    return getAll().select(user -> user.isWithRe());
  }

  public static final MutableList<User> getAll() {
    if (users.isEmpty()) {
      readUsers();
    }
    return users.toList();
  }

  private static JSONArray getJSONArray(String apiUrl) {
    try {
      return new JSONArray(KaroClient.callApi(apiUrl));
    } catch (JSONException e) {
      logger.severe("JSONException while parsing: " + e.getMessage());
    }
    return new JSONArray();
  }

  public static List<User> pickRandomUsers(List<User> users, int count) {
    Random randomizer = new Random();
    List<User> copy = new FastList<>(users);
    List<User> subsetList = new FastList<>(count);
    for (int i = 0; i < 5; i++) {
      subsetList.add(copy.remove(randomizer.nextInt(copy.size())));
    }
    return subsetList;
  }

  public static User fromJSONString(String jsonString) throws JSONException {
    JSONObject json = new JSONObject(jsonString);
    return new User(json);
  }

  public static User fromJSON(JSONObject json) {
    return new User(json);
  }

  private int id;
  private String login;
  private String color;
  private int lastVisit;
  private int signup;
  private int dran;
  private int activeGames;
  private boolean acceptsDayGames;
  private boolean acceptsNightGames;
  private int maxGames;
  private int sound;
  private String soundfile;
  private int size;
  private int border;
  private boolean desperate;
  private boolean birthdayToday;
  private boolean karodayToday;
  private boolean bot;

  private User(JSONObject json) {
    id = json.getInt(ID);
    login = json.getString(LOGIN);
    color = json.getString(COLOR);
    lastVisit = json.getInt(LAST_VISIT);
    signup = json.getInt(SIGNUP);
    dran = json.getInt(DRAN);
    activeGames = json.getInt(ACTIVE_GAMES);
    acceptsDayGames = json.getBoolean(ACCEPTS_DAY_GAMES);
    acceptsNightGames = json.getBoolean(ACCEPTS_NIGHT_GAMES);
    maxGames = json.getInt(MAX_GAMES);
    sound = json.getInt(SOUND);
    soundfile = json.getString(SOUNDFILE);
    size = json.getInt(SIZE);
    border = json.getInt(BORDER);
    desperate = json.getBoolean(DESPERATE);
    birthdayToday = json.getBoolean(BIRTHDAY_TODAY);
    karodayToday = json.getBoolean(KARODAY_TODAY);
    bot = json.getBoolean(BOT);
  }

  public int getId() {
    return id;
  }

  public String getLogin() {
    return login;
  }

  public String getColor() {
    return color;
  }

  public int getLastVisit() {
    return lastVisit;
  }

  public int getSignup() {
    return signup;
  }

  public int getDran() {
    return dran;
  }

  public int getActiveGames() {
    return activeGames;
  }

  public boolean isAcceptsDayGames() {
    return acceptsDayGames;
  }

  public boolean isAcceptsNightGames() {
    return acceptsNightGames;
  }

  public int getMaxGames() {
    return maxGames;
  }

  public int getSound() {
    return sound;
  }

  public String getSoundfile() {
    return soundfile;
  }

  public int getSize() {
    return size;
  }

  public int getBorder() {
    return border;
  }

  public boolean isDesperate() {
    return desperate;
  }

  public boolean hasBirthday() {
    return birthdayToday;
  }

  public boolean hasKaroday() {
    return karodayToday;
  }

  public boolean isWithIq() {
    if (iqUsers.isEmpty()) {
      readIqUsers();
    }
    return iqUsers.anySatisfy(user -> user.equalsIgnoreCase(login));
  }

  public boolean isWithRe() {
    if (reUsers.isEmpty()) {
      readReUsers();
    }
    return reUsers.anySatisfy(user -> user.equalsIgnoreCase(login));
  }

  public boolean isBot() {
    return bot;
  }

  public List<Game> getGames() {
    return getGames(getJSONArray(API_URL_GAMES + id));
  }

  public List<Game> getFinishedGames() {
    return getGames(getJSONArray(API_URL_GAMES + id + API_URL_FINISHED));
  }

  public List<Game> getDranGames() {
    String apiResponse = KaroClient.callApi(API_USER + "/" + id + "/" + API_USER_DRAN);
    if (!apiResponse.isEmpty()) {
      try {
        JSONArray reponse = new JSONArray(apiResponse);
        return getGames(reponse);
      } catch (JSONException je) {
        logger.severe("Error while reading gamelist for user " + id + ": " + je.getMessage());
      }
    }
    return new FastList<>(0);
  }

  private List<Game> getGames(JSONArray array) {
    if (array == null)
      return new FastList<>(0);

    List<Game> games = new FastList<>();
    for (int i = 0; i < array.length(); i++) {
      JSONObject obj = array.getJSONObject(i);
      games.add(Game.get(obj.getInt(Game.ID)));
    }
    return games;
  }

  public boolean isSame(String login) {
    return this.login.equals(login);
  }

  public boolean isInvitable() {
    return getMaxGames() == 0 || getMaxGames() > getActiveGames();
  }

  public boolean isActive() {
    return getLastVisit() < 3 && getSignup() > 30 && getActiveGames() > 5;
  }

  public boolean isBlocking() {
    return getDran() > 100 && getDran() / (float) getActiveGames() > 0.2;
  }

  public Player asPlayer() {
    return Player.getNew(getId());
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(ID).append(":").append(id).append("\n");
    sb.append(LOGIN).append(":").append(login).append("\n");
    sb.append(LAST_VISIT).append(":").append(lastVisit).append("\n");
    sb.append(ACTIVE_GAMES).append(":").append(activeGames).append("\n");
    sb.append(MAX_GAMES).append(":").append(maxGames).append("\n");
    sb.append(DESPERATE).append(":").append(desperate).append("\n");
    return sb.toString();
  }

}
