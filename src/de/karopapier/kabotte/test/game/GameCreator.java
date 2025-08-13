package de.karopapier.kabotte.test.game;

import de.karopapier.api.KaroClient;
import de.karopapier.api.NewGame;
import de.karopapier.kabotte.config.Blacklist;
import de.karopapier.kabotte.config.Settings;

public class GameCreator {

    public static void main(String[] args) {
        Settings settings = Settings.getInstance();
        final KaroClient client = new KaroClient(settings.getUserLogin(), settings.getPassword());
        final NewGame newGame = NewGame.createRandom("Test", client.login(),
                client.getUsers().filter(u -> !Blacklist.getInstance().isBlacklisted(u.login())), false);
        client.addGame(newGame);
        System.exit(0);
    }

}
