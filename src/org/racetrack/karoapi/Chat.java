package org.racetrack.karoapi;

import java.text.*;
import java.util.*;
import java.util.logging.*;
import java.util.regex.*;

import org.eclipse.collections.impl.list.mutable.*;
import org.json.*;

public class Chat implements Comparable<Chat> {

  private static final Logger logger = Logger.getLogger(Chat.class.toString());
  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  public static final String API_CHAT_URL = "chat/";
  public static final String API_SEND = API_CHAT_URL + "message.json";
  public static final String API_URL_LIST = API_CHAT_URL + "list.json";
  public static final String API_URL_USERS = API_CHAT_URL + "users.json";

  private static final String ID = "id";
  private static final String USER = "user";
  private static final String TEXT = "text";
  private static final String TIME = "time";
  private static final String LINE_ID = "lineId";

  private static final String SENTENCE_SPLITTER = "[\\.!\\?;]";
  private static final Pattern SMILEY_KARO = Pattern.compile("<[a-z0-9=/\"\\. ]+title=\"(?<smiley>[a-z0-9]+)\">");
  private static final Pattern SMILEY_WELCOME = Pattern
      .compile("<img src=\\\"http://smile.welcomes-you.com/[a-zA-Z0-9/\\\"\\-. ]+>");
  private static final Pattern QUOTE = Pattern.compile("&quot;");
  private static final Pattern URLS = Pattern.compile("<a [a-zA-Z0-9\"=:\\./\\?>_ ]+ </a>");
  private static final Pattern SPECIAL_CHARS = Pattern.compile("[\\Q@#§$%&:`\"/\\^°~=-\\|\\*_{}<>[]\\E]");

  public static List<Chat> getMessages() {
    JSONArray array = getJSONArray(API_URL_LIST);
    return array != null ? getMessages(array) : new FastList<>(0);
  }

  public static List<Chat> getMessages(JSONArray array) {
    final List<Chat> chatMessages = new FastList<>(array.length());
    array.forEach(obj -> chatMessages.add(new Chat((JSONObject) obj)));
    return chatMessages;
  }

  public static List<User> getUsers() {
    JSONArray array = getJSONArray(API_URL_USERS);
    if (array == null)
      return new FastList<>(0);

    List<User> chatUsers = new FastList<>();
    array.forEach(obj -> chatUsers.add(User.fromJSON((JSONObject) obj)));
    return chatUsers;
  }

  private static JSONArray getJSONArray(String apiUrl) {
    try {
      return new JSONArray(KaroClient.callApi(apiUrl));
    } catch (JSONException e) {
      logger.severe("JSONException while parsing: " + e.getMessage());
    }
    return new JSONArray();
  }

  private String id;
  private String user;
  private String text;
  private String time;
  private int lineId;

  public Chat(JSONObject json) {
    id = json.getString(ID);
    user = json.getString(USER);
    text = json.getString(TEXT);
    time = json.getString(TIME);
    lineId = json.getInt(LINE_ID);
  }

  public Chat(String user, String text, String time) {
    this.user = user;
    this.text = text;
    this.time = time;
  }

  public String getId() {
    return id;
  }

  public String getUser() {
    return user;
  }

  public String getText() {
    return text;
  }

  public List<String> getSentences() {
    List<String> filteredSentences = new FastList<>(0);

    String filteredmessage = text;
    Matcher smileyMatcher = SMILEY_KARO.matcher(filteredmessage);
    if (smileyMatcher.find()) {
      filteredmessage = smileyMatcher.replaceAll(smileyMatcher.group("smiley"));
    }
    filteredmessage = SMILEY_WELCOME.matcher(filteredmessage).replaceAll("");
    filteredmessage = URLS.matcher(filteredmessage).replaceAll("");
    filteredmessage = QUOTE.matcher(filteredmessage).replaceAll("");

    filteredmessage = filteredmessage.toLowerCase().replaceAll("ß", "ss").replaceAll("ä", "ae").replaceAll("ö", "oe")
        .replaceAll("ü", "ue").replaceAll("'", "");
    filteredmessage = SPECIAL_CHARS.matcher(filteredmessage).replaceAll(" ");

    for (String sentence : filteredmessage.split(SENTENCE_SPLITTER)) {
      filteredSentences.add(sentence.replaceAll("[\\,\\s]+", " ").trim());
    }

    return filteredSentences;
  }

  public String getTime() {
    return time;
  }

  public int getLineId() {
    return lineId;
  }

  public boolean isBefore(Chat other) {
    if (other == null)
      return true;

    try {
      Date thisDate = dateFormat.parse(time);
      Date otherDate = dateFormat.parse(other.time);

      return thisDate.before(otherDate);
    } catch (ParseException e) {
      return false;
    }
  }

  @Override
  public int compareTo(Chat message) {
    return isBefore(message) ? -1 : 1;
  }

  @Override
  public String toString() {
    return "Chat[(" + time + ") " + user + ": " + text + "]";
  }

}
