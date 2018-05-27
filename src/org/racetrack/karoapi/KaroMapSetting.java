package org.racetrack.karoapi;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.logging.*;

import org.json.*;

public class KaroMapSetting {

  private static final Logger logger = Logger.getLogger(KaroMapSetting.class.toString());

  private static final String ID = "id";
  private static final String CIRCUIT = "circuit";
  private static final String MAX_TOURS = "maxTours";
  private static final String QUIT = "quit";
  private static final String PATH_MOVE_WEIGHT_POW = "pathWeightPow";
  private static final String PATH_SUCC_WEIGHT_MOD = "pathSuccCountMod";

  public static KaroMapSetting readSettings(int id) {
    String settingString = null;
    try {
      CodeSource codeSource = KaroMapSetting.class.getProtectionDomain().getCodeSource();
      File jarFile = new File(codeSource.getLocation().toURI().getPath());
      File jarDir = jarFile.getParentFile();
      File settingsDir = new File(jarDir, "mapsettings/");

      if (!settingsDir.exists()) {
        settingsDir.mkdir();
      }

      File file = new File(settingsDir, id + ".json");

      if (file.exists()) {
        logger.fine("Loading settings-file " + file);
        BufferedReader reader = new BufferedReader(new FileReader(file));

        StringBuilder sb = new StringBuilder();
        String line = reader.readLine();
        while (line != null) {
          sb.append(line);
          sb.append(System.lineSeparator());
          line = reader.readLine();
        }
        settingString = sb.toString();
        reader.close();
        JSONObject setting = new JSONObject(settingString);
        return new KaroMapSetting(setting, file);
      } else
        return new KaroMapSetting(id, file);

    } catch (FileNotFoundException fnfe) {
      logger.severe(fnfe.getMessage());
    } catch (IOException ioe) {
      logger.severe(ioe.getMessage());
    } catch (URISyntaxException use) {
      logger.severe(use.getMessage());
    }

    return new KaroMapSetting(id, null);
  }

  private int id;
  private File file;
  private Boolean circuit;
  private int maxTours = -1;
  private Boolean quit; // marks map as innavigable and leads to quitting a game on this map
  private double pathWeightPow = Double.NaN;
  private int pathSuccCountMod = -1;
  private boolean set;

  public KaroMapSetting(int id, File file) {
    this.id = id;
    this.file = file;
    set = false;
  }

  public KaroMapSetting(JSONObject json, File file) {
    this.file = file;
    id = json.getInt(ID);
    String circuitString = json.optString(CIRCUIT);
    circuit = circuitString.equals("") ? null : Boolean.valueOf(circuitString);
    maxTours = json.optInt(MAX_TOURS);
    String quitString = json.optString(QUIT);
    quit = quitString.equals("") ? null : Boolean.valueOf(quitString);
    pathWeightPow = json.optDouble(PATH_MOVE_WEIGHT_POW);
    pathSuccCountMod = json.optInt(PATH_SUCC_WEIGHT_MOD, -1);
    set = true;
  }

  public int getId() {
    return id;
  }

  public Boolean isCircuit() {
    return circuit;
  }

  public void setCircuit(boolean circuit) {
    this.circuit = circuit;
  }

  public int getMaxTours() {
    return maxTours;
  }

  public void setMaxTours(int maxTours) {
    this.maxTours = maxTours;
  }

  public boolean isQuit() {
    return quit != null ? quit : false;
  }

  public void setQuit(boolean quit) {
    this.quit = quit;
  }

  public double getPathWeightPow() {
    return pathWeightPow;
  }

  public int getPathSuccCountMod() {
    return pathSuccCountMod;
  }

  public boolean isSet() {
    return set;
  }

  public JSONObject toJSONObject() {
    JSONObject json = new JSONObject();
    json.put(ID, id);
    if (circuit != null) {
      json.put(CIRCUIT, circuit);
    }
    json.put(MAX_TOURS, getMaxTours());
    if (quit != null) {
      json.put(QUIT, quit);
    }
    return json;
  }

  public void writeSettings() {
    if (file != null) {
      if (file.exists()) {
        file.delete();
      }

      try {
        file.createNewFile();
        Writer wr = new FileWriter(file);
        wr.write(toJSONObject().toString());
        wr.close();
      } catch (IOException ioe) {
        logger.severe(ioe.getMessage());
      }
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("id:").append(id).append("/").append("circuit:").append(circuit).append("/");
    sb.append("maxTours").append(maxTours).append("/").append("quit:").append(quit);
    return sb.toString();
  }

}
