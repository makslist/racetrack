package org.racetrack.worker;

import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.java_websocket.client.*;
import org.racetrack.chat.*;
import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.track.*;

public class BotRunner implements Runnable {

  private static final Logger logger = Logger.getLogger(BotRunner.class.getName());

  private int maxThreads = Integer.max(Settings.getInstance().getInt(Property.maxParallelGameThreads), 1);
  private int minThreads = Integer.min(maxThreads, Runtime.getRuntime().availableProcessors());
  private ExecutorService threadPool = Executors.newWorkStealingPool(minThreads);
  private CompletionService<Paths> pathFinderService = new ExecutorCompletionService<>(threadPool);
  private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  private String userLogin;
  private String password;

  private boolean secureConnection = true;
  private KaroClient karo;
  private User user;
  private boolean withChat;
  private ChatModule chatbot;

  private BlockingQueue<Game> games = new PriorityBlockingQueue<>();
  private BlockingQueue<Chat> chatMsg = new LinkedBlockingQueue<>();
  private Set<Integer> gamesInProcess = new ConcurrentSkipListSet<>();
  private BlockingQueue<Paths> finishedPaths = new LinkedBlockingQueue<>();

  public BotRunner() {
    Settings settings = Settings.getInstance();
    userLogin = settings.get(Property.user);
    password = settings.get(Property.password);
    String secureString = settings.get(Property.secureConnection);
    secureConnection = secureString != null ? Boolean.valueOf(secureString) : true;
    String chatString = settings.get(Property.withChat);
    withChat = chatString != null ? Boolean.valueOf(chatString) : false;

    if (userLogin == null || password == null) {
      System.out.println("No username or password given");
      System.exit(1);
    }
  }

  @Override
  public void run() {
    try {
      karo = new KaroClient(userLogin, password, secureConnection);
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

    // backup thread in case webclient crashed
    scheduler.scheduleWithFixedDelay(() -> {
      games.addAll(user.getDranGames());
    }, 0, 8, TimeUnit.MINUTES);

    // show bot as active in chat by updating chat-site
    scheduler.scheduleWithFixedDelay(() -> {
      if (withChat) {
        karo.updateChat();
      }
    }, 5, 55, TimeUnit.SECONDS);

    // scheduler.scheduleAtFixedRate(() -> {
    // if (withChat) {
    // List<User> karokids = User.getKaroKids();
    // for (User kid : karokids) {
    // ChatResponse congratulation = chatbot.contratulate(kid, "wuensche karotag");
    // if (congratulation.isAnswered()) {
    // karo.chat(congratulation.getText());
    // }
    // }
    // List<User> birthdayKids = User.getBirthdayKids();
    // if (birthdayKids.size() <= 2) {
    // for (User kid : birthdayKids) {
    // ChatResponse congratulation = chatbot.contratulate(kid, "wuensche geburtstag");
    // if (congratulation.isAnswered()) {
    // karo.chat(congratulation.getText());
    // }
    // }
    // }
    // }
    // }, computeDelaySeconds(6, 30, 0), 24 * 60 * 60, TimeUnit.SECONDS);

    new Thread(websocketClient()).start();
    new Thread(chat()).start();
    new Thread(queueGame()).start();
    new Thread(solvePath()).start();
    new Thread(postMove()).start();
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
          Thread.sleep(KaroWebSocketClient.TIME_TO_CHECK_FOR_CONNECTION);
        } catch (InterruptedException e) {
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
          logger.warning(e.getMessage());
        }
      }
    };
  }

  private Runnable queueGame() {
    return () -> {
      while (true) {
        try {
          Game game = games.take();
          // ignore already loaded, but currently unfinished games
          if (gamesInProcess.contains(game.getId())) {
            continue;
          }

          game.refresh();

          try {
            Player player = game.getPlayer(user);
            if (game.isDranPlayer(player)) {
              gamesInProcess.add(game.getId());
              pathFinderService.submit(new BlockingPathFinder(game, player));
            }
          } catch (NullPointerException npe) {
          } catch (OutOfMemoryError oome) {
            // block game by letting it stay in process state
            game = null;
            System.gc();
            // recover system and wait
            Thread.sleep(15 * 1000);
          }
        } catch (InterruptedException e) {
        }
      }
    };
  }

  private Runnable solvePath() {
    return () -> {
      while (true) {
        try {
          finishedPaths.put(pathFinderService.take().get());
        } catch (InterruptedException | ExecutionException e) {
          logger.warning(e.getMessage());
        }
      }
    };
  }

  private Runnable postMove() {
    return () -> {
      while (true) {
        try {
          Paths path = finishedPaths.take();

          Game game = path.getGame();
          if (path.isQuitting()) {
            gamesInProcess.remove(path.getGameId());
            karo.quitGame(path.getGameId());
          } else {
            Move move = path.getBestMove();
            if (move != null) {
              gamesInProcess.remove(path.getGameId());
              if (path.hasComment()) {
                karo.moveWithRadio(path.getGameId(), move, path.getComment());
              } else {
                ChatResponse answer = withChat ? chatbot.respondInCar(game, move, path.getMinTotalLength())
                    : ChatResponse.empty();
                if (answer.isText()) {
                  karo.moveWithRadio(path.getGameId(), move, answer.getText());
                } else {
                  karo.move(path.getGameId(), move);
                }
              }
            } else {
              gamesInProcess.remove(path.getGameId());
              karo.resetAfterCrash(path.getGameId());
            }
          }

          if (path.isCrashAhead()) {
            games.addAll(user.getDranGames());
          }
        } catch (InterruptedException e) {
        }
        System.gc();
      }
    };
  }

  private long computeDelaySeconds(int targetHour, int targetMin, int targetSec) {
    LocalDateTime localNow = LocalDateTime.now(ZoneId.of("CET"));
    ZonedDateTime zonedNow = ZonedDateTime.of(localNow, ZoneId.of("CET"));
    ZonedDateTime zonedNextTarget = zonedNow.withHour(targetHour).withMinute(targetMin).withSecond(targetSec);
    if (zonedNow.isAfter(zonedNextTarget)) {
      zonedNextTarget = zonedNextTarget.plusDays(1);
    }

    Duration duration = Duration.between(zonedNow, zonedNextTarget);
    return duration.getSeconds();
  }

}
