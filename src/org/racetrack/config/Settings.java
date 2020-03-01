package org.racetrack.config;

import java.io.*;
import java.util.*;

public class Settings {

  public enum Property {

    maxParallelTourThreads, user, password, secureConnection, withChat, withNewGames, useBetaApi, maxExecutionTimeMinutes, withMultiCrash

  }

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

  public String getUserLogin() {
    return get(Property.user);
  }

  public String getPassword() {
    return get(Property.password);
  }

  public boolean useSecureConnection() {
    String secureString = get(Property.secureConnection);
    return secureString != null ? Boolean.valueOf(secureString) : true;
  }

  public boolean withMultiCrash() {
    String multiCrashString = get(Property.withMultiCrash);
    return multiCrashString != null ? Boolean.valueOf(multiCrashString) : false;
  }

  public boolean activateChatbot() {
    String chatString = get(Property.withChat);
    return chatString != null ? Boolean.valueOf(chatString) : false;
  }

  public boolean createNewGames() {
    String newGamesString = get(Property.withNewGames);
    return newGamesString != null ? Boolean.valueOf(newGamesString) : false;
  }

  public boolean useBetaApi() {
    String useBetaApiString = get(Property.useBetaApi);
    return useBetaApiString != null ? Boolean.valueOf(useBetaApiString) : false;
  }

  public int getMaxParallelTourThreads() {
    int maxThreads = getInt(Property.maxParallelTourThreads);
    int minThreads = Integer.min(maxThreads, Runtime.getRuntime().availableProcessors() - 1);
    return Integer.max(minThreads, 1);
  }

  public int maxExecutionTimeMinutes() {
    return getInt(Property.maxExecutionTimeMinutes);
  }

}
