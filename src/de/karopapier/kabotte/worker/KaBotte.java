package de.karopapier.kabotte.worker;

import de.karopapier.api.*;
import de.karopapier.kabotte.chat.ChatModule;
import de.karopapier.kabotte.config.Blacklist;
import de.karopapier.kabotte.config.Settings;
import de.karopapier.kabotte.track.Action;
import de.karopapier.kabotte.track.MoveChooser;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;

public class KaBotte implements Runnable, Gamer {

    private static final int GAME_LOAD_BACKUP_TIME_DEFAULT_SECONDS = 300;

    private final KaBotteConsole console;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> new Thread(r, "Cron"));
    private final CompletionService<Action> moveFinder = new ExecutorCompletionService<>(Executors.newSingleThreadExecutor());
    private final String userLogin;
    private final String password;
    private final Settings settings;
    private final Set<Integer> skipGames = new HashSet<>();
    private final ChatModule chatbot;
    private KaroClient karo;
    private User user;
    private final Runnable gameLoadJob = () -> user.yourTurnGames().forEach(g -> yourTurn(Game.get(g.id())));
    private ScheduledFuture<?> gameLoadBackupJob;

    public KaBotte(Settings settings) {
        this.settings = settings;
        console = new KaBotteConsole(KaBotteConsole.OutPutLevel.VERBOSE);
        userLogin = this.settings.getUserLogin();
        password = this.settings.getPassword();
        chatbot = this.settings.activateChatbot() ? ChatModule.getInstance(userLogin) : null;

        if (userLogin == null || password == null) {
            console.error(-1, "No username or password given");
            System.exit(1);
        }
    }

    private static long delayUntilInMinutes(int targetHour, int targetMin) {
        LocalDateTime localNow = LocalDateTime.now(ZoneId.of("CET"));
        ZonedDateTime zonedNow = ZonedDateTime.of(localNow, ZoneId.of("CET"));
        ZonedDateTime zonedNextTarget = zonedNow.withHour(targetHour).withMinute(targetMin).withSecond(0);
        if (zonedNow.isAfter(zonedNextTarget))
            zonedNextTarget = zonedNextTarget.plusDays(1);

        Duration duration = Duration.between(zonedNow, zonedNextTarget);
        return duration.getSeconds() / 60;
    }

    @Override
    public void run() {
        karo = new KaroClient(userLogin, password).addGameHandler(this);//.logLevel(Level.FINEST);//.readOnly();TODO setting
        if (chatbot != null)
            karo.addChatHandler(chatbot);

        user = karo.login();
        if (user != null) {
            System.out.println("Bot initialized for user " + user.login());
        } else {
            System.out.println("Login with user: " + userLogin + " failed!");
            System.exit(1);
        }

        scheduleJobs();

        Thread.currentThread().setName("MoveExecutor");
        while (!Thread.currentThread().isInterrupted())
            play();

        System.exit(0);
    }

    @Override
    public void yourTurn(Game game) {
        if (game != null && !skipGames.contains(game.id())) {
            MoveChooser chooser = new MoveChooser(game, user, settings);
            chooser.setConsole(console);
            moveFinder.submit(chooser);
        }
    }

    @Override
    public void play() {
        try {
            Future<Action> futureAction = moveFinder.poll(Integer.min(10, settings.maxExecutionTimeMinutes()), TimeUnit.MINUTES);
            Action action = futureAction != null ? futureAction.get() : null;
            if (action != null) {
                Game game = action.getGame();
                if (action.isNotMyTurn()) {
                    console.verbose(game.id(), "User is not next!");
                } else if (action.skipTurn()) {
                    skipGames.add(game.id());
                    console.info(game.id(), "Game added to skiplist. (" + action.getComment() + ")");
                } else if (action.withdraw()) {
                    karo.quitGame(game.id());
                    console.warning(game.id(), "Quitted game!");
                } else if (action.crashed()) {
                    karo.resetAfterCrash(game.id());
                    yourTurn(game);
                } else {
                    game = game.reload();
                    if (game.isYourTurn(user.asPlayer())) {
                        Move move = action.getMove();
                        Optional<ChatModule.Response> answer = chatbot != null ? chatbot.respondInCar(game) : Optional.empty();
                        if (action.hasComment())
                            karo.move(game.id(), move, action.getComment());
                        else if (answer.isPresent()) {
                            karo.move(game.id(), move, answer.get().getText());
                            console.verbose(game.id(), "Response: " + answer.get().getText());
                        } else if (action.tookCheckeredFlag() && game.getFinishedPlayers().isEmpty())
                            karo.move(game.id(), move, Emoticon.GOLD.toString());
                        else
                            karo.move(game.id(), move, null);
                    }
                }
            }
        } catch (InterruptedException e) {
            console.warning(-1, "Gamer interrupted.");
        } catch (ExecutionException e) {
            console.error(-1, e.getMessage());
        }
    }

    private void scheduleJobs() {
        scheduler.scheduleAtFixedRate(() -> {
            boolean websocketConnected = karo.isWebsocketConnected();
            if (gameLoadBackupJob == null || ((websocketConnected && gameLoadBackupJob.getDelay(TimeUnit.SECONDS) < 10) || !websocketConnected &&
                    gameLoadBackupJob.getDelay(TimeUnit.SECONDS) > GAME_LOAD_BACKUP_TIME_DEFAULT_SECONDS / 2)) {
                if (gameLoadBackupJob != null)
                    gameLoadBackupJob.cancel(false);
                gameLoadBackupJob = scheduler.scheduleAtFixedRate(gameLoadJob, 5, (websocketConnected ? Integer.max(settings.gamesLoadBackupTimeMinutes(), GAME_LOAD_BACKUP_TIME_DEFAULT_SECONDS) : 5), TimeUnit.SECONDS);
            }
        }, 2, 60, TimeUnit.SECONDS);

//        gameLoadBackupJob = scheduler.scheduleAtFixedRate(gameLoadJob, 5, Integer.max(settings.gamesLoadBackupTimeMinutes(), GAME_LOAD_BACKUP_TIME_DEFAULT_SECONDS), TimeUnit.SECONDS); TODO reactivate

        if (chatbot != null) {
            // show bot as active in chat
            scheduler.scheduleWithFixedDelay(() -> karo.updateChatStatus(), 5, 55, TimeUnit.SECONDS);

            scheduler.scheduleAtFixedRate(() -> {
                console.verbose(-1, "Checking for karokids.");
                for (User kid : karo.getUsers().filter(u -> !u.bot() && u.isActive() && u.karodayToday()).toList()) {
                    Optional<ChatHandler.Response> congratulation = chatbot.congratulate(kid.login());
                    congratulation.ifPresent(r -> karo.chat(r.getText(), null));
                }
            }, delayUntilInMinutes(6, 30), 24 * 60L, TimeUnit.MINUTES);
        }

        if (settings.createNewGames()) // creates games every day
            scheduler.scheduleAtFixedRate(() -> karo.addGame(NewGame.createRandom(null, user,
                            karo.getUsers().filter(u -> !Blacklist.getInstance().isBlacklisted(u.login())), true)),
                    delayUntilInMinutes(7, 0), 2 * 24 * 60L, TimeUnit.MINUTES);

        // process skipped games at night with longer duration
        scheduler.scheduleAtFixedRate(() -> {
            List<Integer> games = new ArrayList<>(skipGames);
            skipGames.clear();
            games.forEach(gameId -> yourTurn(Game.get(gameId)));
        }, delayUntilInMinutes(0, 30), 24 * 60L, TimeUnit.MINUTES);
    }

}
