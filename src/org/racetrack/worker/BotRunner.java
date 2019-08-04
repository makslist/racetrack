package org.racetrack.worker;

import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.java_websocket.client.*;
import org.racetrack.chat.*;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.track.*;

public class BotRunner implements Runnable {

  private static final Logger logger = Logger.getLogger(BotRunner.class.getName());

  private ExecutorService executorService = Executors.newSingleThreadExecutor();
  private CompletionService<GameAction> moveFinder = new ExecutorCompletionService<>(executorService);
  private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  private String userLogin;
  private String password;

  private boolean secureConnection = true;
  private KaroClient karo;
  private User user;
  private boolean withChat;
  private boolean withNewGames;
  private boolean useBetaApi;
  private ChatModule chatbot;

  private BlockingQueue<Game> games = new LinkedBlockingQueue<>();
  private BlockingQueue<Chat> chatMsg = new LinkedBlockingQueue<>();
  private Set<Integer> ignoreGames = new HashSet<Integer>();

  public BotRunner() {
    Settings settings = Settings.getInstance();
    userLogin = settings.getUserLogin();
    password = settings.getPassword();
    secureConnection = settings.useSecureConnection();
    withChat = settings.activateChatbot();
    withNewGames = settings.createNewGames();
    useBetaApi = settings.useBetaApi();

    if (userLogin == null || password == null) {
      System.out.println("No username or password given");
      System.exit(1);
    }
  }

  @Override
  public void run() {
    try {
      karo = new KaroClient(userLogin, password, secureConnection, useBetaApi);
      if (karo.logIn()) {
        user = karo.getUser();

        chatbot = ChatModule.getInstance(user.getLogin());

        String startUpMessage = "Bot initialized for user " + user.getLogin() + "(" + user.getId() + ").";
        logger.fine(startUpMessage);
        System.out.println(startUpMessage);
      } else {
        logger.warning("Login with user: " + userLogin + " failed!");
        System.exit(1);
      }
    } catch (NumberFormatException e) {
      return;
    }

    games.addAll(user.getNextGames());

    // show bot as active in chat by updating chat-site
    scheduler.scheduleWithFixedDelay(() -> {
      if (withChat) {
        karo.updateChat();
      }
    }, 5, 55, TimeUnit.SECONDS);

    scheduler.scheduleAtFixedRate(() -> {
      if (withChat) {
        List<User> karokids = User.getKaroKids();
        for (User kid : karokids) {
          ChatResponse congratulation = chatbot.contratulate(kid, "wuensche karotag");
          if (congratulation.isAnswered()) {
            karo.chat(congratulation.getText());
          }
        }
      }
    }, computeDelayMinutes(6, 30, 0), 24 * 60 * 60, TimeUnit.SECONDS);

    // creates games every day
    scheduler.scheduleAtFixedRate(() -> {
      if (withNewGames) {
        Game random = Game.newRandom(null, userLogin, new Random().nextBoolean());
        karo.addGame(random);

        Karolenderblatt blatt = Karolenderblatt.getToday();
        String title = "!KaroIQ!lenderblatt: " + blatt.getLine();
        Game karolenderGame = Game.newRandom(title, userLogin, false);
        karo.addGame(karolenderGame);

        MutableList<User> bots = User.getActive().select(User.bots);
        MutableList<User> battleBots = bots.select(
            user -> new FastList<>().with("DeepPink", "DeepGray", "Slybotone", "Botrix").contains(user.getLogin()));
      }
    }, computeDelayMinutes(7, 0, 0), 24 * 60 * 60, TimeUnit.SECONDS);

    new Thread(websocketClient(), "WebSocketControl").start();
    new Thread(chat(), "ChatRespond").start();
    new Thread(queueGame(), "GameQueue").start();
    new Thread(postCalculatedMove(), "MovePoster").start();
  }

  private Runnable websocketClient() {
    return () -> {
      WebSocketClient client = null;
      while (true) {
        if (client == null || client.getConnection().isClosed()) {
          try {
            client = new KaroWebSocketClient(user.getLogin(), games, chatMsg, secureConnection);
            client.connect();
            logger.fine("WebSocket connection established.");
          } catch (URISyntaxException use) {
          }
        }
        try {
          Thread.sleep(KaroWebSocketClient.TIME_TO_CHECK_FOR_CONNECTION_MILLIS);
        } catch (InterruptedException e) {
          client.close();
          return;
        }
      }
    };
  }

  private Runnable chat() {
    return () -> {
      while (true) {
        try {
          Chat message = chatMsg.take();
          if (withChat) {
            ChatResponse answer = chatbot.respond(message);
            if (answer.isAnswered()) {
              if (karo.logIn()) {
                if (answer.isGameCreated()) {
                  karo.addGame(answer.getGame());
                } else if (answer.isText()) {
                  karo.chat(answer.getText());
                }
              }
            }
          }
        } catch (InterruptedException e) {
          return;
        }
      }
    };
  }

  private Runnable queueGame() {
    return () -> {
      while (true) {
        try {
          Game game = games.take();
          if (!ignoreGames.contains(game.getId())) {
            moveFinder.submit(new MoveChooser(game, user));
          }
        } catch (InterruptedException e) {
          return;
        } catch (NullPointerException | OutOfMemoryError e) {
          logger.severe("MoveFinder Exception: " + e.getMessage());
        }
      }
    };
  }

  private Runnable postCalculatedMove() {
    return () -> {
      while (true) {
        try {
          Future<GameAction> futureAction = moveFinder.poll(2 + Settings.getInstance().maxExecutionTimeMinutes(),
              TimeUnit.MINUTES);

          if (futureAction == null) {
            games.addAll(user.getNextGames()); // retrieve new games when timeout occures
            continue;
          }

          GameAction action = futureAction.get();
          Game game = action.getGame();
          if (action.isNotNext()) {
            continue;
          }
          if (action.skipGame()) {
            ignoreGames.add(game.getId());
            logger.warning(game.getId() + ": Game added to skiplist.");
            continue;
          }
          if (action.quitGame()) {
            karo.quitGame(game.getId());
          } else {
            Move move = action.getMove();
            if (move != null) {
              if (action.hasComment()) {
                karo.moveWithRadio(game.getId(), move, action.getComment());
              } else {
                ChatResponse answer = withChat ? chatbot.respondInCar(game, move) : ChatResponse.empty();
                if (answer.isText()) {
                  karo.moveWithRadio(game.getId(), move, answer.getText());
                } else {
                  karo.move(game.getId(), move);
                }
              }
            } else {
              karo.resetAfterCrash(game.getId());
              games.put(game.update());
            }
          }

        } catch (InterruptedException e) {
          return;
        } catch (ExecutionException e) {
          logger.warning(e.getMessage());
        }
      }
    };

  }

  protected long computeDelayMinutes(int targetHour, int targetMin, int targetSec) {
    LocalDateTime localNow = LocalDateTime.now(ZoneId.of("CET"));
    ZonedDateTime zonedNow = ZonedDateTime.of(localNow, ZoneId.of("CET"));
    ZonedDateTime zonedNextTarget = zonedNow.withHour(targetHour).withMinute(targetMin).withSecond(targetSec);
    if (zonedNow.isAfter(zonedNextTarget)) {
      zonedNextTarget = zonedNextTarget.plusDays(1);
    }

    Duration duration = Duration.between(zonedNow, zonedNextTarget);
    return duration.getSeconds() / 60;
  }

}
