package org.racetrack.karoapi;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.java_websocket.client.*;
import org.java_websocket.handshake.*;
import org.json.*;

public class KaroWebSocketClient extends WebSocketClient {

  enum Event {
    IDENT, JOIN, OK, EVENT
  }

  private static final Logger logger = Logger.getLogger(KaroWebSocketClient.class.toString());

  private static final String WS_PROTOCOL = "ws://";
  @SuppressWarnings("unused")
  private static final String WSS_PROTOCOL = "wss://";
  private static final String URL = "turted.karopapier.de/socket.io/?EIO=3&transport=websocket";

  public static final int TIME_TO_CHECK_FOR_CONNECTION = 2 * 60 * 1000;

  private int pingInterval = Integer.MAX_VALUE;
  private String userLogin;
  private Queue<Game> gameQueue;
  private Queue<Chat> chatMsg;
  private ScheduledExecutorService scheduler;

  public KaroWebSocketClient(String userLogin, Queue<Game> gameQueue, Queue<Chat> chatMsg, boolean secureConnection)
      throws URISyntaxException {
    super(new URI(WS_PROTOCOL + URL));

    this.userLogin = userLogin;
    this.gameQueue = gameQueue;
    this.chatMsg = chatMsg;
  }

  @Override
  public void onOpen(ServerHandshake handshakedata) {
    if (handshakedata.getHttpStatus() != 101) {
      close();
    }
  }

  @Override
  public void onClose(int code, String reason, boolean remote) {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
    logger.fine("WebSocket closed with exit code " + code + " additional info: " + reason);
  }

  @Override
  public void onError(Exception ex) {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
    logger.fine("WebSocket error occurred: " + ex.getMessage());
  }

  @Override
  public void onMessage(String message) {
    if (message.startsWith("0")) {
      JSONObject json = new JSONObject(message.substring(1, message.length()));
      pingInterval = json.getInt("pingInterval");

      scheduler = Executors.newScheduledThreadPool(1);
      scheduler.scheduleWithFixedDelay(() -> {
        send("2");
      }, pingInterval, pingInterval, TimeUnit.MILLISECONDS);
    }

    else if (message.startsWith("3")) {
      // ping
    }

    // login
    else if (message.equals("40")) {
      // "40"
      String ident = getIdent(userLogin);
      send(ident);
      String channelLivelog = getChannel("livelog");
      send(channelLivelog);
      String channelKarochat = getChannel("karochat");
      send(channelKarochat);
    }
    // API answers
    else if (message.startsWith("42")) {
      JSONArray jsonArray = new JSONArray(message.substring(2, message.length()));
      Event event = Event.valueOf(jsonArray.getString(0));

      if (Event.EVENT.equals(event)) {
        JSONObject jsonObj = jsonArray.getJSONObject(1);
        if ("yourTurn".equals(jsonObj.getString("event"))) {
          JSONObject payload = jsonObj.getJSONObject("payload");
          int gId = payload.getInt("gid");
          gameQueue.add(Game.get(gId));
        } else if ("CHAT:MESSAGE".equals(jsonObj.getString("event"))) {
          JSONObject payload = jsonObj.getJSONObject("payload");
          JSONObject chatmsg = payload.getJSONObject("chatmsg");
          String user = chatmsg.getString("user");
          String text = chatmsg.getString("text");
          String time = chatmsg.getString("time");
          Chat msg = new Chat(user, text, time);
          chatMsg.add(msg);
        }

      } else if (Event.OK.equals(event)) {
      }
    }
  }

  private String getIdent(String userLogin) {
    JSONObject payload = new JSONObject();
    payload.put("username", userLogin);
    return new StringBuffer().append("42[\"" + Event.IDENT.toString() + "\",").append(payload.toString()).append("]")
        .toString();
  }

  private String getChannel(String channel) {
    JSONObject payload = new JSONObject();
    payload.put("channel", channel);
    return new StringBuffer().append("42[\"" + Event.JOIN.toString() + "\",").append(payload.toString()).append("]")
        .toString();
  }

}
