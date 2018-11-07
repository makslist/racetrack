package org.racetrack.karoapi;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.concurrent.locks.*;
import java.util.logging.*;

import org.json.*;

public class KaroClient {

  private class Param {
    private String param;
    private String value;

    private Param(String param, String value) {
      this.param = param;
      this.value = value;
    }

    private Param(String param, int value) {
      this.param = param;
      this.value = Integer.toString(value);
    }

    private Param(String param, short value) {
      this.param = param;
      this.value = Short.toString(value);
    }

    @Override
    public String toString() {
      return param + "/" + value;
    }
  }

  private static final long API_MINIMUM_WAITING_TIME = 750;
  private static final long API_LOGIN_REATEMPT_TIME = 6 * 10 * 1000;

  private static final String HTTP_PROTOCOL = "http";
  private static final String HTTPS_PROTOCOL = "https";

  private static final String KARO_HOST = "www.karopapier.de";
  private static final String WIKI_HOST = "wiki.karopapier.de";
  private static final String KARO_API = "api";

  private static final String LOGIN = "login";
  private static final String LOGOFF = "abmelden.php";
  private static final String MOVE = "move.php?";
  private static final String RESET_URL = "showmap.php?";
  private static final String QUIT_URL = "kickplayer.php?";
  private static final String API_SEND_MAIL = "mailer.php";
  private static final String API_SEND_MESSAGES = "messages";

  private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:50.0) Gecko/20100101 Firefox/50.0";
  private static final String UTF_8 = StandardCharsets.UTF_8.name();
  private static final String LATIN_1 = StandardCharsets.ISO_8859_1.name();
  private static final String CONTENT_TYPE_JSON = "application/json; charset=" + UTF_8;
  private static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded; charset=" + UTF_8;

  private static final Logger logger = Logger.getLogger(KaroClient.class.toString());

  public static String callApi(String subURL) {
    String url = HTTP_PROTOCOL + "://" + KARO_HOST + "/" + KARO_API + "/" + subURL;
    return senApiRequest(url);
  }

  public static String callWiki(String query) {
    String url = HTTP_PROTOCOL + "://" + WIKI_HOST + "/" + "api.php" + "?" + query;
    return senApiRequest(url);
  }

  private static String senApiRequest(String url) {
    StringBuilder response = new StringBuilder();

    try {
      URL karoUrl = new URL(url);
      BufferedReader in = new BufferedReader(new InputStreamReader(karoUrl.openStream()));

      logger.finest("Calling Karopapier for " + url);
      String inputLine;
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }

      in.close();
      logger.finest("Call KaroApi successfully ended.");
    } catch (MalformedURLException mue) {
      logger.severe("Malformed Url: " + mue.getMessage());
    } catch (IOException ioe) {
      logger.fine("IOException when calling KaroApi: " + ioe.getMessage());
    }

    return response.toString();
  }

  private String login;
  private String password;
  private boolean secureConnection;
  private User user;
  private CookieManager cookieManager = new CookieManager();

  private long lastApiRequestTime = 0;
  private ReentrantLock karoAccessLock = new ReentrantLock();

  public KaroClient(String login, String password, boolean secureConnection) {
    this.login = login;
    this.password = password;
    this.secureConnection = secureConnection;

    CookieHandler.setDefault(cookieManager);
  }

  private void waitForApiAccess() {
    karoAccessLock.lock();
    try {
      long lastCallDelta = System.currentTimeMillis() - lastApiRequestTime;
      if (lastCallDelta < API_MINIMUM_WAITING_TIME) {
        Thread.sleep(API_MINIMUM_WAITING_TIME - lastCallDelta);
      }
      lastApiRequestTime = System.currentTimeMillis();
    } catch (InterruptedException e) {
    } finally {
      karoAccessLock.unlock();
    }
  }

  private String getURL(String file) {
    return (secureConnection ? HTTPS_PROTOCOL : HTTP_PROTOCOL) + "://" + KARO_HOST + "/" + file;
  }

  private String getApiURL(String file) {
    return (secureConnection ? HTTPS_PROTOCOL : HTTP_PROTOCOL) + "://" + KARO_HOST + "/" + KARO_API + "/" + file;
  }

  @SuppressWarnings("unused")
  private String getWikiURL(String query) {
    return (secureConnection ? HTTPS_PROTOCOL : HTTP_PROTOCOL) + "://" + WIKI_HOST + "/" + "api.php" + "?" + query;
  }

  public User getUser() {
    return user;
  }

  public boolean logIn() {
    String checkUser = sendGetRequest(getApiURL(User.CHECK));
    while (true) {
      try {
        User userCheck = User.fromJSONString(checkUser);
        if (userCheck.isSame(login))
          return true;
      } catch (JSONException je) {
        user = null;
      }

      Param loginParam = new Param("login", login);
      Param passParam = new Param("password", password);
      String params = getJSONParamString(loginParam, passParam);
      String loginResponse = sendPostRequest(getApiURL(LOGIN), params, CONTENT_TYPE_JSON);
      try {
        User logInUser = User.fromJSONString(loginResponse);

        if (logInUser.isSame(login)) {
          user = logInUser;
          return true;
        }
      } catch (JSONException je) {
        user = null;
        logger.severe("Login user: " + login + " failed.");
      }
      try {
        Thread.sleep(KaroClient.API_LOGIN_REATEMPT_TIME);
      } catch (InterruptedException e) {
        logger.severe(e.getMessage());
      }
    }
  }

  public boolean logOff() {
    String logOffPage = sendGetRequest(getURL(LOGOFF));
    return logOffPage.contains("Du bist abgemeldet.");
  }

  public boolean resetAfterCrash(int gameId) {
    if (!logIn())
      return false;

    logger.fine("Reset after crash " + gameId);
    Param gid = new Param("GID", gameId);
    String resetPage = sendGetRequest(getURL(RESET_URL) + getParamString(UTF_8, gid));
    return !resetPage.contains("AUAAAA!");
  }

  public boolean quitGame(int gameId) {
    if (!logIn())
      return false;

    String quitUrl = getURL(QUIT_URL);
    Param gid = new Param("GID", gameId);
    Param uid = new Param("UID", user.getId());
    String params = getParamString(UTF_8, gid, uid);
    String quitPage = sendGetRequest(quitUrl + params);
    if (!quitPage.contains("Du willst Dich selbst aus dem Spiel kicken?"))
      return false;

    Param sicher = new Param("sicher", 1);
    Param submit = new Param("SUBMIT", "Aussteigen???");
    params = getParamString(UTF_8, sicher, gid, uid, submit);
    String quitResponse = sendPostRequest(quitUrl, params, CONTENT_TYPE_FORM);
    return quitResponse.contains("Fertig, Du bist draussen...");
  }

  public boolean addGame(Game game) {
    if (!logIn())
      return false;

    String params = "game=" + game.asJSON();
    String response = sendPostRequest(getApiURL(Game.API_ADD), params, CONTENT_TYPE_FORM);
    try {
      JSONObject jsonObj = new JSONObject(response).getJSONObject("game");
      return jsonObj != null;
    } catch (JSONException je) {
      logger.severe(je.getMessage());
      return false;
    }
  }

  public boolean move(int gameId, Move move) {
    if (!logIn())
      return false;

    Param gid = new Param("GID", gameId);
    if (move.getXv() == 0 && move.getYv() == 0) {
      Param startx = new Param("startx", move.getX());
      Param starty = new Param("starty", move.getY());
      return postMove(getURL(MOVE) + getParamString(UTF_8, gid, startx, starty));
    } else {
      Param xpos = new Param("xpos", move.getX());
      Param ypos = new Param("ypos", move.getY());
      Param xvec = new Param("xvec", move.getXv());
      Param yvec = new Param("yvec", move.getYv());
      return postMove(getURL(MOVE) + getParamString(UTF_8, gid, xpos, ypos, xvec, yvec));
    }
  }

  public boolean moveWithRadio(int gameId, Move move, String message) {
    if (!logIn())
      return false;

    Param gid = new Param("GID", gameId);
    Param xpos = new Param("xpos", move.getX());
    Param ypos = new Param("ypos", move.getY());
    Param xvec = new Param("xvec", move.getXv());
    Param yvec = new Param("yvec", move.getYv());
    Param movemessage = new Param("movemessage", message);
    return postMove(getURL(MOVE) + getParamString(UTF_8, gid, xpos, ypos, xvec, yvec, movemessage));
  }

  private boolean postMove(String moveUrl) {
    if (!logIn())
      return false;

    String movePage = sendGetRequest(moveUrl);
    boolean postOk = !movePage.isEmpty() && !movePage.contains("Du Schummler")
        && !movePage.contains("Du musst schon angemeldet sein");
    if (!postOk) {
      logger.warning("Posting move: " + moveUrl + " not successful.");
    }
    return postOk;
  }

  public boolean updateChat() {
    if (!logIn())
      return false;

    try {
      String chatList = sendGetRequest(getApiURL(Chat.API_URL_LIST));
      if (!chatList.isEmpty()) {
        JSONArray array = new JSONArray(chatList);
        Chat.getMessages(array);
        return true;
      }
    } catch (JSONException je) {
    }
    return false;
  }

  public boolean chat(String message) {
    try {
      Param msg = new Param("msg", message);
      String params = getJSONParamString(msg);
      String chatResponse = sendPostRequest(getApiURL(Chat.API_SEND), params, CONTENT_TYPE_JSON);

      String user = new JSONArray(chatResponse).optJSONObject(0).getString("user");
      return user == login;
    } catch (JSONException je) {
      return false;
    }
  }

  public boolean sendMessage(int userId, String message) {
    Param dateSepParam = new Param("dateSeparator", "true");
    Param userParam = new Param("userId", String.valueOf(userId));
    Param messageParam = new Param("text", String.valueOf(message));
    String params = getJSONParamString(dateSepParam, userParam, messageParam);

    String response = sendPostRequest(getApiURL(API_SEND_MESSAGES), params, CONTENT_TYPE_JSON);
    try {
      int contactId = new JSONObject(response).getInt("contact_id");
      return contactId == userId;
    } catch (JSONException je) {
      return false;
    }
  }

  public boolean sendMail(int userId, String message) {
    Param schicken = new Param("SCHICKEN", "Nix");
    Param to = new Param("to", userId);
    Param mailtext = new Param("mailtext", message);

    String params = getParamString(LATIN_1, schicken, to, mailtext);
    String response = sendPostRequest(getURL(API_SEND_MAIL), params, CONTENT_TYPE_FORM);
    return response.contains("Mail verschickt!!");
  }

  private String sendGetRequest(String url) {
    BufferedReader in = null;
    try {
      URL urlObj = new URL(url);
      HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
      conn.setUseCaches(false);
      conn.setRequestProperty("User-Agent", USER_AGENT);
      conn.setRequestProperty("Content-Type", CONTENT_TYPE_JSON);

      waitForApiAccess();
      int responseCode = conn.getResponseCode();
      logger.finer("Sending 'GET' request to URL: " + url + " Response Code: " + responseCode);
      if (responseCode == 401) // 401 Unauthorized - currently not logged in
        return "";

      in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      String inputLine;
      StringBuffer response = new StringBuffer();

      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }

      return response.toString();
    } catch (MalformedURLException mue) {
      logger.severe(mue.getMessage());
    } catch (IOException ioe) {
      logger.severe(ioe.getMessage());
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException ioe) {
          logger.severe(ioe.getMessage());
        }
      }
    }
    return "";
  }

  private String sendPostRequest(String url, String postParams, String contentType) {
    DataOutputStream out = null;
    BufferedReader in = null;
    try {
      URL urlObj = new URL(url);
      HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
      conn.setRequestMethod("POST");
      conn.setDoOutput(true);
      conn.setUseCaches(false);
      conn.setRequestProperty("User-Agent", USER_AGENT);
      conn.setRequestProperty("Content-Type", contentType);
      conn.setRequestProperty("Content-Length", Integer.toString(postParams.length()));

      out = new DataOutputStream(conn.getOutputStream());
      waitForApiAccess();
      logger.fine("Sending 'Params': " + postParams);
      out.writeBytes(postParams);
      out.flush();

      logger.fine("Sending 'POST' request to URL: " + url + " Response Code: " + conn.getResponseCode());
      in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      String inputLine;
      StringBuffer response = new StringBuffer();

      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine).append("\n");
      }
      return response.toString();
    } catch (MalformedURLException mue) {
      logger.severe(mue.getMessage());
    } catch (IOException ioe) {
      logger.fine(ioe.getMessage());
    } finally {
      try {
        if (out != null) {
          out.close();
        }
        if (in != null) {
          in.close();
        }
      } catch (IOException ioe) {
        logger.severe(ioe.getMessage());
      }
    }
    return "";
  }

  private String getParamString(String encoding, Param... paramList) {
    StringBuilder result = new StringBuilder();
    for (Param param : paramList) {
      try {
        if (result.length() > 0) {
          result.append("&");
        }
        result.append(URLEncoder.encode(param.param, encoding)).append("=");
        result.append(URLEncoder.encode(param.value, encoding));
      } catch (UnsupportedEncodingException e) {
      }
    }
    return result.toString();
  }

  private String getJSONParamString(Param... paramList) {
    JSONObject params = new JSONObject();
    for (Param param : paramList) {
      try {
        params.put(param.param, param.value);
      } catch (JSONException e) {
      }
    }
    return params.toString();
  }

}
