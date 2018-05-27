package org.racetrack.worker;

import java.util.*;

import org.racetrack.karoapi.*;

public class CliProgressBar {

  private enum Type {
    PATHS("Paths"), TOURS("Tours"), BLOCKER("Block"), ANALYZE("Analyze");

    private String value;

    private Type(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  private static int MAX_BAR_LENGTH = 50;

  public static CliProgressBar getPathBar(Game game, int max) {
    return new CliProgressBar(game, max, Type.PATHS);
  }

  public static CliProgressBar getTourBar(Game game, int max) {
    return new CliProgressBar(game, max, Type.TOURS);
  }

  public static CliProgressBar getBlockerBar(Game game, int max) {
    return new CliProgressBar(game, max, Type.BLOCKER);
  }

  public static CliProgressBar getAnalyzeBar(Game game, int max) {
    return new CliProgressBar(game, max, Type.ANALYZE);
  }

  private Type type;

  private boolean isFinished = false;

  private int gameId, mapId;
  private int max = 0, progress = 0;
  private long startTime;
  private long endTime;

  private CliProgressBar(Game game, int max, Type type) {
    gameId = game.getId();
    mapId = game.getMapId();
    this.max = max;

    this.type = type;
    startTime = System.currentTimeMillis();
  }

  public synchronized void incProgress() {
    progress++;
    if (!isFinished && progress >= max) {
      progress = max;
      isFinished = true;
      endTime = System.currentTimeMillis();
    }
    print();
  }

  private void print() {
    int percent = max == 0 ? 0 : (int) Math.round((((float) progress) / max) * 100);

    StringBuilder bar = getHeader();
    if (!isFinished) {
      int barLength = Math.round(MAX_BAR_LENGTH * percent / 100);
      for (int i = 0; i < MAX_BAR_LENGTH; i++) {
        if (i < barLength) {
          bar.append("=");
        } else if (i == barLength) {
          bar.append(">");
        } else {
          bar.append(" ");
        }
      }
      bar.append("] %3d%%");
      System.out.printf("", gameId, mapId, percent);
    } else {
      double time = ((endTime - startTime) / 1000d);
      bar.append(" %tT  Calculation took:  ").append("%7.2f").append(" seconds").append("%5s").append("] %3d%%%n");
      System.out.printf("", gameId, mapId, Calendar.getInstance(), time, "", percent);
    }
  }

  private StringBuilder getHeader() {
    StringBuilder header = new StringBuilder("\r");
    header.append("Game: %6d (Map: %3d) ").append(type.getValue()).append(" [");
    return header;
  }

}
