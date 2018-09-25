package org.racetrack;

import java.awt.*;
import java.io.*;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.analyzer.*;
import org.racetrack.config.*;
import org.racetrack.gui.*;
import org.racetrack.karoapi.*;
import org.racetrack.karoapi.Game.*;
import org.racetrack.rules.RuleFactory.*;
import org.racetrack.worker.*;

public class Starter {

  private static final int SCALE_MIN = 8;
  private static final int SCALE_MAX = 64;

  private enum Param {
    BOT("bot"), PROP("prop"), GUI("gui"), MAPALYZE("mapalyze"), DIR("dir"), MAPSCALE("mapscale");

    private String param;

    private Param(String param) {
      this.param = param;
    }

    public static Param get(String value) {
      for (Param p : values())
        if (p.param.equals(value))
          return p;
      return MAPSCALE;
    }

    @Override
    public String toString() {
      return param;
    }
  }

  public static void main(String[] args) {
    boolean startBot = false;
    boolean startGui = false;

    boolean startMapalyzor = false;
    MutableList<String> analyzeMaps = Lists.mutable.empty();
    int mapScale = 12;
    Dir direction = Dir.classic;

    for (String arg2 : args) {
      switch (arg2.charAt(0)) {
      case '-':
        String[] param = arg2.substring(1).split(":");
        switch (Param.get(param[0])) {
        case BOT:
          startBot = true;
          break;
        case PROP:
          if (param.length >= 2) {
            Settings.getInstance(new File("./" + param[1]));
          }
          break;
        case GUI:
          startGui = true;
          break;
        case MAPALYZE:
          startMapalyzor = true;
          analyzeMaps = new FastList<>(0);
          if (param.length >= 2) {
            String[] mapIds = param[1].split("[;/]");
            for (String mapId : mapIds) {
              try {
                Integer.valueOf(mapId);
                analyzeMaps.add(mapId);
              } catch (NumberFormatException nfe) {
                File file = new File("./" + mapId);
                if (file.exists()) {
                  analyzeMaps.add(mapId);
                } else {
                  System.out.println("File not found: " + param[1] + ".");
                }
              }
            }
          }
          break;
        case DIR:
          if (param.length >= 2) {
            try {
              direction = Dir.valueOf(param[1]);
            } catch (IllegalArgumentException iae) {
              System.out.println("Direction unknown: " + param[1] + ". Using classic instead.");
              direction = Dir.classic;
            }
          }
          break;
        case MAPSCALE:
          if (param.length >= 2) {
            try {
              mapScale = Integer.valueOf(param[1]);
            } catch (NumberFormatException nfe) {
              System.out.println("Mapscale unknown: " + param[1] + ". Using 12 instead.");
              mapScale = 12;
            }
            if (mapScale < SCALE_MIN || mapScale > SCALE_MAX) {
              System.out.println("Mapscale out of bounds: " + param[1] + ". Using 12 instead.");
              mapScale = 12;
            }
          }
          break;
        default:
          break;
        }
        break;
      default:
        System.out.println("Parameters are exspected to begin with \"-\": " + arg2);
        System.exit(1);
      }
    }

    if (startBot) {
      new Thread(new BotRunner()).start();
      return;
    } else if (startGui) {
      EventQueue.invokeLater(new Runnable() {
        @Override
        public void run() {
          KaroGui frame = new KaroGui();
          frame.setVisible(true);
        }
      });
    } else if (startMapalyzor) {
      if (analyzeMaps.isEmpty()) {
        System.out.println("Please provide parameters: e.g. -" + Param.MAPALYZE.toString() + ":1;Tetris.json" + " [-"
            + Param.DIR.toString() + ":(" + Dir.classic.toString() + "|" + Dir.formula1.toString() + "|"
            + Dir.free.toString() + ")]" + " [-" + Param.MAPSCALE.toString() + ":(8-64)]");
      } else {
        runMapalysor(analyzeMaps, mapScale, direction);
      }
    } else {
      StringBuilder help = new StringBuilder("No start command set. Please provide parameters:" + "\n\n");
      help.append("-").append(Param.BOT.toString()).append("\t\t\t\t\t" + "Starts the bot" + "\n");
      help.append("\t-").append(Param.PROP.toString()).append(":file.prop" + "\t\t\t")
          .append("Provide a property file with settings for the bot" + "\n\n");

      help.append("-").append(Param.GUI.toString()).append("\t\t\t\t\t" + "Starts in GUI mode" + "\n");
      help.append("\t-").append(Param.PROP.toString()).append(":file.prop" + "\t\t\t")
          .append("Provide a property file with settings for the bot" + "\n\n");

      help.append("-").append(Param.MAPALYZE.toString()).append(":(mapId[(;|/)map.json])" + "\t\t")
          .append("Map-Analyzer" + "\n");
      help.append("\t\t\t\t\t" + "For json files, provide relative path to map-file from jar)" + "\n");
      help.append("\t-").append(Param.DIR.toString()).append(":[").append(Dir.classic).append("|").append(Dir.formula1)
          .append("|").append(Dir.free).append("]" + "\t" + "The direction to analyze the map").append("\n");
      help.append("\t-").append(Param.MAPSCALE.toString()).append(":(8-64)" + "\t\t")
          .append("Set the size of a tile (in pixel) in the picture of the map" + "\n\n");

      help.append("Possible properties to set are:\n");
      help.append("\t" + Property.maxParallelGameThreads.name() + "\t\t" + "Only in bot mode" + "\n");
      help.append("\t" + Property.withChat.name() + "=(true|false)")
          .append("\t\t" + "Start a chatbot (only in bot mode)" + "\n");
      help.append("\t" + Property.maxParallelTourThreads.name()).append("\t\t")
          .append("Max number of threads to actually calculate the distance of the shortes tour." + "\n");
      help.append("\t\t\t\t\t")
          .append("on multi-processor platforms set this to #cores (or #cores-1 to keep the computer responsive")
          .append("\n");
      help.append("\t" + Property.user.name()).append("\t\t\t\t" + "Login for bot or gui" + "\n");
      help.append("\t" + Property.password.name()).append("\t\t\t" + "Password for login" + "\n");
      help.append("\t" + Property.secureConnection.name() + "=(true|false)" + "\t").append("Use encrypted connection");
      System.out.print(help);
    }
  }

  private static void runMapalysor(MutableList<String> analyzeMaps, int mapScale, Dir direction) {
    System.out.println("MapAlyzor 2.0");

    for (String map : analyzeMaps) {
      try {
        int mapId = Integer.valueOf(map);
        KaroMap karoMap = KaroMap.get(mapId);
        Game fakeGame = Game.getFakeGame(karoMap, RuleType.STANDARD, true, direction, Crash.forbidden, 2);
        MapAlyzor alyzor = new MapAlyzor(karoMap, fakeGame, mapScale);
        alyzor.run();
      } catch (NumberFormatException nfe) {
        File mapFile = new File(map);
        if (mapFile.exists()) {
          KaroMap karoMap = KaroMap.get(mapFile);
          Game fakeGame = Game.getFakeGame(karoMap, RuleType.STANDARD, true, direction, Crash.forbidden, 2);
          MapAlyzor alyzor = new MapAlyzor(karoMap, fakeGame, mapScale);
          alyzor.run();
        }
      }
    }
  }

  private Starter() {
  }

}
