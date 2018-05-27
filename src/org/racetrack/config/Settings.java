package org.racetrack.config;

import java.io.*;
import java.util.*;

public class Settings {

  private static Settings settings;
  private static Properties properties;

  public static Settings getInstance() {
    if (settings == null) {
      settings = new Settings();
      File file = new File("settings.prop");
      settings.load(file);
    }
    return settings;
  }

  public static Settings getInstance(File file) {
    settings = new Settings();
    settings.load(file);
    return settings;
  }

  private Settings() {
  }

  private void load(File file) {
    if (!file.exists()) {
      System.out.println("Property file " + file.getAbsolutePath() + " does not exist. Using defaults.");
      properties = new Properties();
      return;
    }
    try {
      Reader reader = new FileReader(file);
      properties = new Properties();
      properties.load(reader);
    } catch (IOException e) {
      System.out.println(e.getMessage());
      properties = new Properties();
    }
  }

  public String get(Property key) {
    return properties.getProperty(key.name());
  }

  public int getInt(Property key) {
    String value = properties.getProperty(key.name());
    if (value != null)
      return Integer.valueOf(value);
    return 0;
  }

}
