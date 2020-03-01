package org.racetrack.worker;

import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.java_websocket.client.*;
import org.racetrack.chat.*;
import org.racetrack.concurrent.*;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.karoapi.KaroClient.*;
import org.racetrack.track.*;

public class BotRunner implements Runnable, GameHandler, ChatHandler {

  private static final Logger logger = Logger.getLogger(BotRunner.class.getName());

  private ExecutorService priorityExecutor = new PriorityTaskThreadPoolExecutor(1,
      new DistinctPriorityBlockingQueue<>());
  private CompletionService<GameAction> moveFinder = new PriorityTaskCompletionService<GameAction>(priorityExecutor);

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

  private Set<Integer> skipGames = new HashSet<Integer>();

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

    for (Game game : user.getNextGames()) {
      processGame(game);
    }

    // show bot as active in chat by updating chat-site
    scheduler.scheduleWithFixedDelay(() -> {
      if (withChat) {
        karo.updateChat();
      }
    }, 5, 55, TimeUnit.SECONDS);

    scheduler.scheduleAtFixedRate(() -> {
      System.out.println("checking for karokids.");
      if (withChat) {
        for (User kid : User.getKaroKids()) {
          ChatResponse congratulation = chatbot.contratulate(kid, "wuensche karotag");
          if (congratulation.isAnswered()) {
            karo.chat(congratulation.getText());
          }
        }
      }
    }, computeDelayMinutes(6, 30), 24 * 60L, TimeUnit.MINUTES);

    // creates games every day
    scheduler.scheduleAtFixedRate(() -> {
      if (withNewGames) {
        Game random = Game.newRandom(null, userLogin, new Random().nextBoolean());
        karo.addGame(random);

        Karolenderblatt blatt = Karolenderblatt.getToday();
        String title = "!KaroIQ!lenderblatt: " + blatt.getLine();
        Game karolenderGame = Game.newRandom(title, userLogin, false);
        karo.addGame(karolenderGame);
      }
    }, computeDelayMinutes(7, 0), 24 * 60L, TimeUnit.MINUTES);

    scheduler.scheduleAtFixedRate(() -> {
      List<Integer> games = new ArrayList<Integer>(skipGames);
      skipGames.clear();
      for (Integer gameId : games) {
        moveFinder
            .submit(new MoveChooser(Game.get(gameId), user, Settings.getInstance().maxExecutionTimeMinutes() * 4));
      }
    }, computeDelayMinutes(0, 30), 24 * 60L, TimeUnit.MINUTES);

    new Thread(websocketClient(), "WebSocketControl").start();
    new Thread(postMove(), "MovePoster").start();
  }

  private Runnable websocketClient() {
    return () -> {
      WebSocketClient client = null;
      while (true) {
        if (client == null || client.getConnection().isClosed()) {
          try {
            client = new KaroWebSocketClient(user.getLogin(), secureConnection, this, this);
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

  private Runnable postMove() {
    return () -> {
      while (true) {
        GameAction action = null;
        try {
          Future<GameAction> futureAction = moveFinder.poll(2 + Settings.getInstance().maxExecutionTimeMinutes(),
              TimeUnit.MINUTES);
          if (futureAction == null) {
            for (Game game : user.getNextGames()) {
              processGame(game);
            }
            continue;
          }

          action = futureAction.get();
        } catch (InterruptedException e) {
          return;
        } catch (ExecutionException e) {
          logger.warning(e.getMessage());
        }

        Game game = action.getGame();
        if (action.isNotNext()) {
          continue;
        } else if (action.skipGame()) {
          skipGames.add(game.getId());
          ConsoleOutput.println(game.getId(), "Game added to skiplist. (" + action.getComment() + ")");
          continue;
        } else if (action.quitGame()) {
          karo.quitGame(game.getId());
        } else if (action.isCrash()) {
          karo.resetAfterCrash(game.getId());
          processGame(game.update());
        } else {
          Move move = action.getMove();
          if (move != null) {
            try {
              if (action.hasComment()) {
                karo.moveWithRadio(game.getId(), move, action.getComment());
              } else {
                ChatResponse answer = withChat ? chatbot.respondInCar(game, move) : ChatResponse.empty();
                if (answer.isText()) {
                  karo.moveWithRadio(game.getId(), move, answer.getText());
                } else {
                  if (action.isFinishingMove() && game.getFinishedPlayer().isEmpty()) {
                    karo.moveWithRadio(game.getId(), move, Emoticon.GOLD.toString());
                  } else {
                    karo.move(game.getId(), move);
                  }
                }
              }
            } catch (PostingMoveFailedException e) {
              if (game.update().isNextPlayer(user.asPlayer())) {
                ConsoleOutput.println(game.getId(), e.getMessage());
              }
            }
          }
        }

      }
    };

  }

  protected long computeDelayMinutes(int targetHour, int targetMin) {
    LocalDateTime localNow = LocalDateTime.now(ZoneId.of("CET"));
    ZonedDateTime zonedNow = ZonedDateTime.of(localNow, ZoneId.of("CET"));
    ZonedDateTime zonedNextTarget = zonedNow.withHour(targetHour).withMinute(targetMin).withSecond(0);
    if (zonedNow.isAfter(zonedNextTarget)) {
      zonedNextTarget = zonedNextTarget.plusDays(1);
    }

    Duration duration = Duration.between(zonedNow, zonedNextTarget);
    return duration.getSeconds() / 60;
  }

  @Override
  public void processGame(Game game) {
    if (game != null && !skipGames.contains(game.getId())) {
      moveFinder.submit(new MoveChooser(game, user, Settings.getInstance().maxExecutionTimeMinutes()));
    }
  }

  @Override
  public void respondToMessage(Chat message) {
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
  }

}
