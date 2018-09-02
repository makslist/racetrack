package org.racetrack.chat;

import java.text.*;
import java.util.*;
import java.util.regex.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.karoapi.*;

public class ChatModule {

  enum Replacements {
    NEWGAME("#newgame"), NEWGAMESOME("#newgamewithsome"), NEWGAMEWITH("#newgamewith"), NAME("#name"), LOCATION(
        "#location"), TIME("#time"), DATE("#date"), CALC("#calc"), ANALYZE("#analyze"), HUMAN(""), BOT("");

    private String replace;

    private Replacements(String replace) {
      this.replace = replace;
    }

    public boolean matches(String text) {
      return Pattern.compile(replace).matcher(text).find();
    }
  }

  private static ChatModule chatbot;

  private final Pattern userPattern;

  public static ChatModule getInstance(String userLogin) {
    if (chatbot == null || !chatbot.userLogin.equals(userLogin)) {
      chatbot = new ChatModule(userLogin);
    }
    return chatbot;
  }

  private class GotoReply {
    private Key gotoKey;
    private String reply;

    private GotoReply() {
    }

    private GotoReply(Key key, String reply) {
      gotoKey = key;
      this.reply = reply;
    }

    @Override
    public String toString() {
      return gotoKey + " / " + reply;
    }
  }

  private Script script;
  private String userLogin;

  private ChatModule(String userLogin) {
    this.userLogin = userLogin;
    userPattern = Pattern.compile("@" + userLogin + "|\\A[\\W\\s]*" + userLogin + "|" + userLogin + "[\\W\\s]*\\Z",
        Pattern.CASE_INSENSITIVE);

    script = Script.getInstance();
  }

  public ChatResponse respondInCar(Game game, Move currentMove) {
    for (Chat chat : game.getMissedMessages()) {
      if (isAddressedToUser(chat) || game.isTheOnlyHuman(User.get(chat.getUser()))) {
        for (String sentence : chat.getSentences()) {
          String answer = sentence(cleanUser(sentence));
          if (!answer.isEmpty()) {
            answer = replaceConstants(answer, chat.getUser());
            return new ChatResponse(answer);
          }
        }
      }
    }
    return ChatResponse.empty();
  }

  public ChatResponse respond(Chat chat) {
    if (isAddressedToUser(chat)) {
      for (String sentence : chat.getSentences()) {
        String answer = sentence(cleanUser(sentence));
        if (!answer.isEmpty()) {
          if (Replacements.NEWGAME.matches(answer)) {
            String challengerLogin = chat.getUser();
            String title = "Ich dreh' eine Runde mit " + challengerLogin + "!";

            if (Replacements.NEWGAMESOME.matches(answer))
              return new ChatResponse(Game.newWith(title, userLogin, Lists.mutable.with(challengerLogin)));
            else if (Replacements.NEWGAMEWITH.matches(answer)) {
              MutableList<String> players = Lists.mutable.of(challengerLogin);
              players.withAll(Arrays.asList(answer.split("[ ,;/]")));

              return new ChatResponse(Game.newWith(title, userLogin, players));
            } else if (Replacements.NEWGAME.matches(answer)) {
            }

          } else {
            String replaceConstants = replaceConstants(answer, chat.getUser());
            return new ChatResponse(replaceConstants);
          }
        }
      }
      return new ChatResponse(saySomething());
    }
    return ChatResponse.empty();
  }

  public ChatResponse contratulate(User user, String event) {
    if (userLogin.equals(user.getLogin()))
      return ChatResponse.empty();
    String answer = sentence(event);
    return new ChatResponse(replaceConstants(answer, user.getLogin()));
  }

  private boolean isAddressedToUser(Chat chat) {
    if (userLogin.equals(chat.getUser()))
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
    return deleteUser.replaceAll("\\s", " ").trim();
  }

  private String saySomething() {
    Key key = script.getKey("xnone");
    if (key != null) {
      String reply = decompose(key, "xnone");
      if (!reply.isEmpty())
        return reply;
    }
    return "";
  }

  /**
   * Process a sentence. Make pre transformations.Scan sentence for keys. Try decompositions for each key.
   */
  private String sentence(String sentence) {
    String preTranslate = script.translatePre(sentence);

    for (Key key : script.getKeyList(preTranslate)) {
      String reply = decompose(key, preTranslate);
      if (!reply.isEmpty())
        return reply;
    }
    return "";
  }

  /**
   * Decompose a string according to the given key. Try each decomposition rule in order. If it matches, assemble a
   * reply and return it. If assembly fails, try another decomposition rule. If assembly is a goto rule, return null and
   * give the key. If assembly succeeds, return the reply;
   */
  private String decompose(Key key, String sentence) {
    for (Decomp decomp : key.getDecomps()) {
      List<String> match = matchDecomp(sentence, decomp.pattern());
      if (!match.isEmpty()) {
        GotoReply reply = assemble(decomp, match);
        if (reply.gotoKey != null) {
          decompose(reply.gotoKey, sentence);
        } else if (reply.reply != null)
          return reply.reply;
      }
    }
    return "";
  }

  /**
   * Decomposition match, If decomp has no synonyms, do a regular match. Otherwise, try all synonyms.
   */
  private MutableList<String> matchDecomp(String sentence, String pattern) {
    Matcher synMatch = Pattern.compile("@(?<syn>[a-zA-Z]+)").matcher(pattern);
    if (synMatch.find()) {
      MutableList<String> synons = script.findSynlist(synMatch.group("syn"));
      for (String synonym : synons) {
        MutableList<String> match = match(sentence, synMatch.replaceAll(synonym));
        if (!match.isEmpty())
          return match;
      }
      return new FastList<>(0);
    }
    return match(sentence, pattern); // no synonyms in decomp pattern
  }

  private MutableList<String> match(String sentence, String pattern) {
    Matcher match = Pattern.compile(pattern.replaceAll("[ ]*\\*[ ]*", "([a-zA-Z0-9 ]*)")).matcher(sentence);

    MutableList<String> matches = new FastList<>(0);
    if (match.matches()) {
      for (int i = 0; i <= match.groupCount(); i++) {
        String group = match.group(i);
        if (group != null) {
          matches.add(group.trim());
        }
      }
    }
    return matches;
  }

  /**
   * Assembly a reply from a decomp rule and the input. If the reassembly rule is goto, return null and give the gotoKey
   * to use. Otherwise return the response.
   */
  private GotoReply assemble(Decomp decomp, List<String> replys) {
    String rule = decomp.nextRule();

    Matcher matchGoto = Pattern.compile("goto (?<goto>[a-z]+)").matcher(rule);
    if (matchGoto.find())
      return new GotoReply(script.getKey(matchGoto.group("goto")), null);

    Matcher matchReplace = Pattern.compile("\\(([0-9]+)\\)").matcher(rule);
    while (matchReplace.find()) {
      int n = Integer.parseInt(matchReplace.group(1));
      if (n <= replys.size()) {
        rule = matchReplace.replaceFirst(script.translatePost(replys.get(n)));
      }
    }

    return new GotoReply(null, rule);
  }

  private String replaceConstants(String answer, String chatPartner) {
    String cleaned = answer.replace("#name", chatPartner);
    cleaned = cleaned.replace("#location", "Frankfurt");

    cleaned = cleaned.replace("#time", new SimpleDateFormat("HH:mm:ss").format(new Date()));
    cleaned = cleaned.replace("#date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));

    return cleaned;
  }

}
