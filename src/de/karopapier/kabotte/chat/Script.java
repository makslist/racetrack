package de.karopapier.kabotte.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.karopapier.kabotte.Starter;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Script {

    public static final String GOTO = "goto";
    public static final String SYN = "syn";
    public static final Pattern GOTO_PATTERN = Pattern.compile(GOTO + " (?<" + GOTO + ">[a-z]+)");
    public static final Pattern SYN_PATTERN = Pattern.compile("@(?<" + SYN + ">[a-zA-Z]+)");
    public static final Pattern NUMBER_PATTERN = Pattern.compile("\\(([0-9]+)\\)");
    private static final Logger LOGGER = Logger.getLogger(Script.class.toString());
    private final Map<String, String> pre = new HashMap<>();
    private final Map<String, Key> keys = new HashMap<>();
    private final List<List<String>> syns = new ArrayList<>();
    private final Map<String, String> post = new HashMap<>();

    private Script() {
    }

    private static File getDir() {
        try {
            File cacheDir = new File("chatrules/");
            if (!cacheDir.exists()) {
                CodeSource codeSource = Starter.class.getProtectionDomain().getCodeSource();
                File jarFile = new File(codeSource.getLocation().toURI().getPath());
                File jarDir = jarFile.getParentFile();
                cacheDir = new File(jarDir, "chatrules/");
                if (!cacheDir.exists()) {
                    cacheDir = new File(jarDir.getParentFile(), "chatrules/");
                    if (cacheDir.exists()) {
                        System.out.println("Chat scripts found in directory \"" + cacheDir + "\".");
                        return cacheDir;
                    } else {
                        System.out.println("No chat scripts found in directory \"" + cacheDir + "\".");
                        return null;
                    }
                }
            }
            return cacheDir;
        } catch (URISyntaxException | NullPointerException ignored) {
            return null;
        }
    }

    public static Script getInstance() {
        Script script = new Script();
        File cacheDir = getDir();
        if (cacheDir == null || !cacheDir.exists())
            return script;

        File[] scripts = cacheDir.listFiles((dir, name) -> name.endsWith("json"));
        if (scripts == null || scripts.length == 0)
            System.err.println("No script files found in " + cacheDir.getAbsolutePath());
        else
            for (File scriptFile : scripts)
                script.read(scriptFile);
        return script;
    }

    private void read(File scriptFile) {
        if (scriptFile.exists())
            try {
                Script script = new ObjectMapper().readValue(scriptFile, Script.class);
                pre.putAll(script.pre);
                keys.putAll(script.keys);
                syns.addAll(script.syns);
                post.putAll(script.post);
            } catch (IOException e) {
                LOGGER.severe("Error in file: " + scriptFile.getName());
            }
    }

    @SuppressWarnings("unused")
    @JsonProperty("pre")
    public void setPre(String[][] pre) {
        for (final String[] p : pre)
            this.pre.put(p[0], p[1]);
    }

    @SuppressWarnings("unused")
    @JsonProperty("keys")
    public void setKeys(Key[] keys) {
        for (final Key key : keys)
            this.keys.put(key.key(), key);
    }

    @SuppressWarnings("unused")
    @JsonProperty("syns")
    public void setSyns(String[][] syns) {
        for (final String[] s : syns)
            this.syns.add(Arrays.asList(s));
    }

    @SuppressWarnings("unused")
    @JsonProperty("post")
    public void setPost(String[][] post) {
        for (final String[] p : post)
            this.post.put(p[0], p[1]);
    }

    public Key getKey(String key) {
        return keys.get(key);
    }

    /**
     * Break the string s into words. For each word, if isKey is true, then push the key onto the stack.
     */
    public List<Key> getKeyList(String sentence) {
        List<String> words = Arrays.asList(sentence.trim().split("\\s"));
        return keys.values().stream().filter(k -> words.contains(k.key())).sorted().toList();
    }

    /**
     * Find a synonym word list given any word in it.
     */
    public List<String> findSynlist(String word) {
        return syns.stream().filter(w -> w.contains(word.trim())).findFirst().orElse(new ArrayList<>());
    }

    public String translatePre(String sentence) {
        StringBuilder sb = new StringBuilder();
        for (String word : sentence.split(" ")) {
            String string = pre.get(word.trim());
            sb.append(string != null ? string : word).append(" ");
        }
        return sb.toString().trim();
    }

    public String translatePost(String sentence) {
        StringBuilder sb = new StringBuilder();
        for (String word : sentence.split(" ")) {
            String postWord = post.get(word.trim());
            if (!sb.isEmpty())
                sb.append(" ");
            sb.append(postWord != null ? postWord : word);
        }
        return sb.toString();
    }


    /**
     * Process a sentence. Make pre transformations.Scan sentence for keys. Try decompositions for each key.
     */
    String sentence(String sentence) {
        String preTranslate = translatePre(sentence);

        for (Key key : getKeyList(preTranslate)) {
            System.out.println("sentence: " + sentence);
            String reply = decompose(key, preTranslate);
            System.out.println("reply: " + reply);
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
    String decompose(Key key, String sentence) {
        for (Key.Decomp decomp : key.getDecomps()) {
            List<String> match = matchDecomp(sentence, decomp.pattern());
            if (!match.isEmpty()) {
                GotoReply reply = assemble(decomp, match);
                if (reply.gotoKey != null)
                    decompose(reply.gotoKey, sentence);
                else if (reply.reply != null)
                    return reply.reply;
            }
        }
        return "";
    }

    /**
     * Decomposition match, If decomp has no synonyms, do a regular match. Otherwise, try all synonyms.
     */
    private List<String> matchDecomp(String sentence, String pattern) {
        Matcher synMatch = SYN_PATTERN.matcher(pattern);
        if (synMatch.find()) {
            List<String> synons = findSynlist(synMatch.group(SYN));
            for (String synonym : synons) {
                List<String> match = match(sentence, synMatch.replaceAll(synonym));
                if (!match.isEmpty())
                    return match;
            }
            return new ArrayList<>(0);
        }
        return match(sentence, pattern); // no synonyms in decomp pattern
    }

    private List<String> match(String sentence, String pattern) {
        Matcher match = Pattern.compile(pattern.replaceAll("[ ]*\\*[ ]*", "([a-zA-Z0-9 ]*)")).matcher(sentence);

        List<String> matches = new ArrayList<>(0);
        if (match.matches())
            for (int i = 0; i <= match.groupCount(); i++) {
                String group = match.group(i);
                if (group != null)
                    matches.add(group.trim());
            }
        return matches;
    }

    /**
     * Assembly a reply from a decomp rule and the input. If the reassembly rule is goto, return null and give the gotoKey
     * to use. Otherwise, return the response.
     */
    private GotoReply assemble(Key.Decomp decomp, List<String> replys) {
        String rule = decomp.nextRule();

        Matcher matchGoto = GOTO_PATTERN.matcher(rule);
        if (matchGoto.find())
            return new GotoReply(getKey(matchGoto.group(GOTO)), null);

        Matcher matchReplace = NUMBER_PATTERN.matcher(rule);
        while (matchReplace.find()) {
            int n = Integer.parseInt(matchReplace.group(1));
            if (n <= replys.size())
                rule = matchReplace.replaceFirst(translatePost(replys.get(n)));
        }

        return new GotoReply(null, rule);
    }

    static class GotoReply {
        final Key gotoKey;
        final String reply;

        private GotoReply(Key key, String reply) {
            gotoKey = key;
            this.reply = reply;
        }

        @Override
        public String toString() {
            return gotoKey + " / " + reply;
        }
    }

}
