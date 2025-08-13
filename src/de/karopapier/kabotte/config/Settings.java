package de.karopapier.kabotte.config;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class Settings {

    private static Settings settings;
    private static Properties properties;

    private Settings() {
    }

    public static Settings getInstance() {
        return settings != null ? settings : Settings.getInstance(new File("settings.prop"));
    }

    public static Settings getInstance(File file) {
        settings = new Settings();
        settings.load(file);
        return settings;
    }

    private void load(File file) {
        if (!file.exists()) {
            System.out.println("Property file " + file.getAbsolutePath() + " does not exist. Using defaults.");
            properties = new Properties();
            return;
        }
        try {
            properties = new Properties();
            properties.load(new FileReader(file));
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
            return Integer.parseInt(value);
        return 0;
    }

    public String getUserLogin() {
        return get(Property.user);
    }

    public String getPassword() {
        return get(Property.password);
    }

    public boolean withMultiCrash() {
        String multiCrashString = get(Property.withMultiCrash);
        return Boolean.parseBoolean(multiCrashString);
    }

    public boolean activateChatbot() {
        String chatString = get(Property.activateChatbot);
        return Boolean.parseBoolean(chatString);
    }

    public boolean createNewGames() {
        String newGamesString = get(Property.createNewGames);
        return Boolean.parseBoolean(newGamesString);
    }

    public int gamesLoadBackupTimeMinutes() {
        String gamesLoadBackupTimeMinutes = get(Property.gamesLoadBackupTimeMinutes);
        return gamesLoadBackupTimeMinutes != null ? Integer.parseInt(gamesLoadBackupTimeMinutes) : 60;
    }

    public int maxParallelTourThreads() {
        int maxThreads = getInt(Property.maxParallelTourThreads);
        int minThreads = Integer.min(maxThreads, Runtime.getRuntime().availableProcessors() - 1);
        return Integer.max(minThreads, 1);
    }

    public int gtsMaxPlayerCount() {
        return getInt(Property.gtsMaxPlayerCount);
    }

    public int maxExecutionTimeMinutes() {
        return getInt(Property.maxExecutionTimeMinutes);
    }

    public int gtsTranspositionRoundLimit() {
        return getInt(Property.gtsTranspositionRoundLimit);
    }

    public enum Property {

        user, password, activateChatbot, createNewGames, gtsMaxPlayerCount, gtsTranspositionRoundLimit, maxExecutionTimeMinutes, maxParallelTourThreads, withMultiCrash, gamesLoadBackupTimeMinutes

    }

}
