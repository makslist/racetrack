package org.racetrack.worker;

import java.time.*;
import java.time.format.*;

public class ConsoleOutput {

  private static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyMMdd/HHmm");

  public static void println(String str) {
    System.out.println(getFormatedTime() + "  " + str);
  }

  public static void println(int gameId, String str) {
    System.out.println(getFormatedTime() + " [" + gameId + "] " + str);
  }

  public static void print(int gameId, String str) {
    System.out.print(getFormatedTime() + " [" + gameId + "] " + str);
  }

  private static String getFormatedTime() {
    return dtf.format(LocalDateTime.now());
  }

}
