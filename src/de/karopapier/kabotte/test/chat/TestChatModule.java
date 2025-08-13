package de.karopapier.kabotte.test.chat;

import de.karopapier.api.ChatHandler;
import de.karopapier.api.KaroClient;
import de.karopapier.api.User;
import de.karopapier.kabotte.chat.ChatModule;
import de.karopapier.kabotte.config.Settings;

import java.util.Optional;

public class TestChatModule {

    public static void main(String[] args) {
        Settings settings = Settings.getInstance();
        final KaroClient client = new KaroClient(settings.getUserLogin(), settings.getPassword());
        User user = client.login();
        ChatModule chatbot = ChatModule.getInstance(user.login());
        for (User kid : client.getUsers().toList()) {//.filter(u -> !u.isBot() && u.isActive() && u.hasKaroday()).toList()) {
            Optional<ChatHandler.Response> congratulation = chatbot.congratulate(kid.login());
            congratulation.ifPresent(System.out::println);
        }
        System.exit(0);
    }

}
