package de.karopapier.kabotte.chat;

import de.karopapier.api.Chat;
import de.karopapier.api.ChatHandler;
import de.karopapier.api.Game;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatModule implements ChatHandler {

    public static final String WUENSCHE_KAROTAG = "wuensche karotag";

    private static ChatModule chatbot;
    private final Pattern userPattern;
    private final Script script;
    private final String userLogin;

    private ChatModule(String userLogin) {
        this.userLogin = userLogin;
        userPattern = Pattern.compile("@" + userLogin + "|\\A[\\W\\s]*" + userLogin, Pattern.CASE_INSENSITIVE);

        script = Script.getInstance();
    }

    public static ChatModule getInstance(String userLogin) {
        if (chatbot == null || !chatbot.userLogin.equals(userLogin))
            chatbot = new ChatModule(userLogin);
        return chatbot;
    }

    public Optional<Response> respondInCar(Game game) {
        for (Chat chat : game.missedMessages(game.next()))
            if (isAddressedToUser(chat))
                for (String sentence : chat.getSentences()) {
                    String answer = script.sentence(cleanUser(sentence));
                    if (!answer.isEmpty()) {
                        answer = replaceConstants(answer, chat.getUserLogin());
                        return Optional.of(new Response(answer));
                    }
                }
        return Optional.empty();
    }

    public Optional<Response> respond(Chat chat) {
        System.out.println("Received message: " + chat.getText());

        if (isAddressedToUser(chat)) {
            for (String sentence : chat.getSentences()) {
                System.out.println(sentence);
                String answer = script.sentence(cleanUser(sentence));
                if (!answer.isEmpty()) {
                    String cleanedAnswer = replaceConstants(answer, chat.getUserLogin());
                    System.out.println("Answered: " + cleanedAnswer);
                    return Optional.of(new Response(cleanedAnswer));
                }
            }
            return Optional.of(new Response(saySomething()));
        }
        return Optional.empty();
    }

    public Optional<Response> congratulate(String userLogin) {
        if (this.userLogin.equals(userLogin))
            return Optional.empty();
        String answer = script.sentence(WUENSCHE_KAROTAG);
        return Optional.of(new Response(replaceConstants(answer, userLogin)));
    }

    private boolean isAddressedToUser(Chat chat) {
        if (userLogin.equals(chat.getUserLogin()))
            return false;

        for (String sentence : chat.getSentences()) {
            Matcher userMatcher = userPattern.matcher(sentence);
            if (userMatcher.find())
                return true;
        }
        return false;
    }

    private String cleanUser(String sentence) {
        String deleteUser = Pattern.compile("[@]*" + userLogin, Pattern.CASE_INSENSITIVE).matcher(sentence).replaceAll("");
        System.out.println("cleanuser: " + deleteUser);
        return deleteUser.replaceAll("\\s", " ").trim();
    }

    private String saySomething() {
        Key key = script.getKey("xnone");
        if (key != null) {
            String reply = script.decompose(key, "xnone");
            if (!reply.isEmpty())
                return reply;
        }
        return "";
    }

    private String replaceConstants(String answer, String chatPartner) {
        String cleaned = answer.replace("#name", chatPartner);
        cleaned = cleaned.replace("#location", "Frankfurt");

        cleaned = cleaned.replace("#time", new SimpleDateFormat("HH:mm:ss").format(new Date()));
        cleaned = cleaned.replace("#date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));

        return cleaned;
    }

}
