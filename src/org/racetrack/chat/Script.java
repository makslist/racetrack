package org.racetrack.chat;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.logging.*;

import org.eclipse.collections.api.bag.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.eclipse.collections.impl.map.mutable.*;
import org.json.*;
import org.racetrack.*;

public class Script {

  private static final Logger logger = Logger.getLogger(Script.class.toString());

  private static final String PRE = "pre";
  private static final String POST = "post";
  private static final String KEYS = "keys";
  private static final String SYNONYMS = "syns";

  private static File getDir() {
    CodeSource codeSource = Starter.class.getProtectionDomain().getCodeSource();
    try {
      File jarFile = new File(codeSource.getLocation().toURI().getPath());
      File jarDir = jarFile.getParentFile();
      File cacheDir = new File(jarDir, "chatrules/");
      return cacheDir;
    } catch (URISyntaxException | NullPointerException e) {
      logger.severe("Script directory not found: " + e.getMessage());
      return null;
    }
  }

  public static Script getInstance() {
    Script script = new Script();
    File cacheDir = getDir();
    if (cacheDir == null || !cacheDir.exists())
      return script;

    File[] scripts = cacheDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith("json");
      }
    });
    if (scripts == null || scripts.length == 0) {
      System.err.println("No script files found in " + cacheDir.getAbsolutePath());
    }
    for (File scriptFile : scripts) {
      script.read(scriptFile);
    }
    return script;
  }

  private MutableMap<String, String> pre = Maps.mutable.empty();
  private MutableMap<String, Key> keys = Maps.mutable.empty();
  private MutableList<MutableList<String>> syns = new FastList<>();
  private MutableMap<String, String> post = Maps.mutable.empty();

  private Script() {
  }

  private void read(File scriptFile) {
    StringBuilder script = new StringBuilder();

    try {
      if (scriptFile.exists()) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(scriptFile), "UTF-8"));

        String line = reader.readLine();
        while (line != null) {
          script.append(line).append(System.lineSeparator());
          line = reader.readLine();
        }
        reader.close();
      }
    } catch (IOException e) {
      logger.severe("Error in file: " + scriptFile.getName());
    }

    try {
      JSONObject jsonScript = new JSONObject(script.toString());
      insert(jsonScript);
    } catch (JSONException je) {
      logger.severe("JSON parsing error in file \"" + scriptFile.getName() + "\" at " + je.getMessage());
    }
  }

  private void insert(JSONObject obj) {
    JSONArray jsonPre = obj.optJSONArray(PRE);
    if (jsonPre != null) {
      pre.putAll(getMap(jsonPre));
    }
    JSONArray jsonSyns = obj.optJSONArray(SYNONYMS);
    if (jsonSyns != null) {
      syns.addAll(getListOfList(jsonSyns));
    }
    JSONArray jsonKeys = obj.optJSONArray(KEYS);
    for (int i = 0; jsonKeys != null && i < jsonKeys.length(); i++) {
      Key key = new Key(jsonKeys.getJSONObject(i));
      if (keys.containsKey(key.key())) {
        Key oldKey = keys.get(key.key());
        key = key.mergeDecomps(oldKey); // get "old" key and merge decompositions
        System.out.println("Duplicate key found: " + key.key());
      }
      keys.put(key.key(), key);
    }
    JSONArray jsonPost = obj.optJSONArray(POST);
    if (jsonPost != null) {
      post.putAll(getMap(jsonPost));
    }
  }

  public Key getKey(String key) {
    return keys.get(key);
  }

  /**
   * Break the string s into words. For each word, if isKey is true, then push the key onto the stack.
   */
  public MutableList<Key> getKeyList(String sentence) {
    List<String> words = Arrays.asList(sentence.trim().split("\\s"));
    MutableBag<Key> select = keys.select(key -> words.contains(key.key()));
    return select.toSortedList();
  }

  /**
   * Find a synonym word list given the any word in it.
   */
  public MutableList<String> findSynlist(String word) {
    return syns.detectOptional(words -> words.contains(word.trim())).orElse(new FastList<String>(0));
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
      if (sb.length() != 0) {
        sb.append(" ");
      }
      sb.append(postWord != null ? postWord : word);
    }
    return sb.toString();
  }

  private JSONArray getJSON(Map<String, String> map) {
    JSONArray array = new JSONArray();
    for (String key : map.keySet()) {
      JSONArray trans = new JSONArray();
      trans.put(key).put(map.get(key));
      array.put(trans);
    }
    return array;
  }

  private MutableList<MutableList<String>> getListOfList(JSONArray array) {
    MutableList<MutableList<String>> list = new FastList<>();
    for (int i = 0; i < array.length(); i++) {
      list.add(getList(array.getJSONArray(i)));
    }
    return list;
  }

  private MutableList<String> getList(JSONArray array) {
    MutableList<String> list = new FastList<>();
    for (int j = 0; j < array.length(); j++) {
      list.add(array.getString(j));
    }
    return list;
  }

  private MutableMap<String, String> getMap(JSONArray array) {
    MutableMap<String, String> map = new UnifiedMap<>();
    for (int i = 0; i < array.length(); i++) {
      JSONArray translate = array.getJSONArray(i);
      map.put(translate.getString(0), translate.getString(1));
    }
    return map;
  }

  public void writeToFile(String filename) {
    try {
      File dir = getDir();
      File scriptFile = new File(dir, filename + ".json");

      if (scriptFile != null) {
        if (scriptFile.exists()) {
          scriptFile.delete();
        }

        scriptFile.createNewFile();
        Writer wr = new FileWriter(scriptFile);
        String jsonMap = toJSONObject().toString();
        wr.write(jsonMap);
        wr.close();
      }
    } catch (IOException e) {
      logger.severe(e.getMessage());
    }
  }

  private JSONObject toJSONObject() {
    JSONObject json = new JSONObject();
    json.put(PRE, getJSON(pre));
    json.put(SYNONYMS, new JSONArray(syns));
    json.put(KEYS, new JSONArray(keys.collect(key -> key.toJSONObject())));
    json.put(POST, getJSON(post));
    return json;
  }

}
