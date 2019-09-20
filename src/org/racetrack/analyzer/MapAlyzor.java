package org.racetrack.analyzer;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.logging.*;

import javax.imageio.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.gui.*;
import org.racetrack.karoapi.*;
import org.racetrack.karoapi.Game.*;
import org.racetrack.rules.*;
import org.racetrack.track.*;

public class MapAlyzor {

  private static final Logger logger = Logger.getLogger(MapAlyzor.class.toString());

  public static void main(String args[]) {
    // for (KaroMap map : KaroMap.getAll()) {
    KaroMap map = KaroMap.get(10007);
    try {
      new MapAlyzor(map, 16).run();
    } catch (Exception e) {
    }
    // }
    // KaroMap map = KaroMap.getMap(new File("./maps/Regenbogen-Boulevard.json"));
  }

  private NumberFormat numberFormatter;

  private KaroMap map;
  private int scale;

  public MapAlyzor(int mapId, Game game, int mapScale) {
    this(KaroMap.get(mapId), mapScale);
  }

  public MapAlyzor(KaroMap map, int mapScale) {
    this.map = map;
    scale = mapScale;

    DecimalFormatSymbols otherSymbols = DecimalFormatSymbols.getInstance();
    otherSymbols.setDecimalSeparator('.');
    otherSymbols.setGroupingSeparator(',');
    numberFormatter = new DecimalFormat("#0.00", otherSymbols);
  }

  public void run() {
    for (MapTile cp : map.getCps()) {
      if (map.getTilesAsMoves(cp).isEmpty()) {
        System.out.println("CPs dont match: " + cp);
      }
    }

    String filteredMapname = !map.getName().equals("") ? map.getName() : String.valueOf(map.getId());
    NumberFormat nf = new DecimalFormat("0000");
    String fileMapname = "./" + nf.format(map.getId()) + " - " + filteredMapname.replaceAll("[^a-zA-Z0-9.-]", "_");

    System.out.print(
        "|-\r\n" + "| [[Datei:Map_" + map.getId() + ".png|50x20px|link=Karte:" + map.getId() + "]] || " + map.getName()
            + " || " + map.getAuthor() + " || " + map.getPlayers() + " || " + numberFormatter.format(map.getRating()));

    analyzeGame(fileMapname, Game.getTestGame(map, false, Dir.classic, Crash.forbidden, 2));
    analyzeGame(fileMapname, Game.getTestGame(map, true, Dir.classic, Crash.forbidden, 2));
    analyzeGame(fileMapname, Game.getTestGame(map, true, Dir.formula1, Crash.forbidden, 2));
    analyzeGame(fileMapname, Game.getTestGame(map, true, Dir.free, Crash.forbidden, 2));
    System.out.print("\n");
  }

  private void analyzeGame(String fileMapname, Game game) {
    GameRule rule = RuleFactory.getInstance(game);
    if (game.getDirection().equals(Dir.classic) && !game.withCps()) {
    } else if (game.getDirection().equals(Dir.classic) && game.getMap().hasCps() && game.withCps()) {
    } else if ((game.getDirection().equals(Dir.formula1) || game.getDirection().equals(Dir.free)) && rule.isMapCircuit()
        && game.withCps()) {
    } else {
      System.out.print(" || || ||");
      return;
    }

    TSP tsp = new TSP(game, rule);
    PathFinder pathfinder = new PathFinder(game, game.getNext(), rule, tsp);
    Paths path = pathfinder.call();

    int minLength = path.getMinTotalLength();
    int minCount = path.getMovesOfRound(1).size();
    int startMoves = map.getTilesAsMoves(MapTile.START).size();
    Map<Short, MutableCollection<Move>> wide = path.getWidestPaths();
    short minPathWidth = Short.MAX_VALUE;
    for (short depth : new FastList<Short>(wide.keySet()).sortThis()) {
      int width = wide.get(depth).size();
      if (width < minPathWidth && depth < minLength) { // last move doesn't count as occupied
        minPathWidth = (short) width;
      }
    }
    System.out.print(" || " + minLength + " || " + minCount + "/" + startMoves + " || " + minPathWidth);

    String fileName = fileMapname + "_" + game.getDirection().name() + (game.withCps() ? "_cps" : "");

    writeAnalyzeFile(path, fileName);
    // writeMoveFile(paths);
    writeImageFile(path, fileName);
  }

  private void writeAnalyzeFile(Paths paths, String fileName) {
    short minLength = (short) paths.getMinTotalLength();
    MutableList<Move> selectedStartMoves = paths.getMovesOfRound(1);
    short minCount = (short) selectedStartMoves.size();
    short minPathWidth = Short.MAX_VALUE;
    Map<Short, MutableCollection<Move>> wide = paths.getWidestPaths();
    Map<Short, MutableCollection<Move>> wideFull = Maps.mutable.empty();

    for (Move move : paths.getPartialMoves()) {
      MutableCollection<Move> lenMoves = wideFull.getOrDefault(move.getTotalLen(), new FastList<>());
      lenMoves.add(move);
      wideFull.putIfAbsent(move.getTotalLen(), lenMoves);
    }

    Comparator<Move> posComparator = (m1, m2) -> {
      int diffX = m1.getX() - m2.getX();
      return diffX != 0 ? diffX : m1.getY() - m2.getY();
    };
    StringBuilder analyzeMoves = new StringBuilder();
    for (short depth : new FastList<Short>(wide.keySet()).sortThis()) {
      int width = wide.get(depth).size();
      analyzeMoves.append("Length: ").append(depth).append(", width: ").append(width).append(" positions: ")
          .append(wide.get(depth).toSortedList(posComparator)).append("\n");
      if (width < minPathWidth && depth < minLength) { // last move doesn't count as occupied
        minPathWidth = (short) width;
      }
    }
    MutableList<Move> startMoves = map.getTilesAsMoves(MapTile.START);
    String result = "Map " + map.getId() + " has " + minCount + "/" + startMoves.size() + " optimal starts, length "
        + minLength + ", bottleneck " + minPathWidth + "\n";
    analyzeMoves.append("\n").append(result);
    // System.out.println(result.toString());

    File file = new File(fileName + ".log");
    FileWriter fw = null;
    try {
      fw = new FileWriter(file);
      fw.write(analyzeMoves.toString());
    } catch (IOException e) {
      logger.warning(e.getMessage());
    } finally {
      try {
        fw.close();
      } catch (IOException e) {
      }
    }
  }

  private void writeImageFile(Paths paths, String fileName) {
    MapPanel mapPanel = new MapPanel(map, scale);
    NavigationPanel navPanel = new NavigationPanel(paths, scale, map.getCols(), map.getRows());

    BufferedImage bi = new BufferedImage(mapPanel.getWidth(), mapPanel.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = bi.createGraphics();
    mapPanel.paint(g2d);
    navPanel.paint(g2d);

    try {
      ImageIO.write(bi, "PNG", new File(fileName + ".png"));
    } catch (IOException ioe) {
      logger.severe(ioe.getMessage());
    }
  }

}
