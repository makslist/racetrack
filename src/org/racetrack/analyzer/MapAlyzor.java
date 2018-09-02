package org.racetrack.analyzer;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import javax.imageio.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.gui.*;
import org.racetrack.karoapi.*;
import org.racetrack.karoapi.Game.*;
import org.racetrack.rules.RuleFactory.*;
import org.racetrack.track.*;

public class MapAlyzor {

  private static final Logger logger = Logger.getLogger(MapAlyzor.class.toString());

  public static void main(String args[]) {
    for (KaroMap map : KaroMap.getAll()) {
      try {
        FakeGame game = new FakeGame(map, RuleType.STANDARD, true, Dir.classic, Crash.forbidden, 2);

        MapAlyzor alyzor = new MapAlyzor(map, game, 16);
        alyzor.run();
      } catch (Exception e) {
      }
    }
    // KaroMap map = KaroMap.getMap(new File("./maps/Regenbogen-Boulevard.json"));
  }

  private KaroMap map;
  private FakeGame game;
  private String fileName;
  private int scale;

  public MapAlyzor(int mapId, FakeGame game, int mapScale) {
    this(KaroMap.get(mapId), game, mapScale);
  }

  public MapAlyzor(KaroMap map, FakeGame game, int mapScale) {
    this.map = map;
    String filteredFilename = !map.getName().equals("") ? map.getName() : String.valueOf(map.getId());
    NumberFormat nf = new DecimalFormat("0000");
    fileName = "./" + nf.format(map.getId()) + " - " + filteredFilename.replaceAll("[^a-zA-Z0-9.-]", "_");

    this.game = game != null ? game : new FakeGame(map, RuleType.STANDARD, true, Dir.classic, Crash.forbidden, 2);
    scale = mapScale;
  }

  public void run() {
    ExecutorService threadPool = Executors.newWorkStealingPool();

    Future<Paths> futurePath = threadPool.submit(new MapAlyzePathFinder(game, game.getPlayer()));
    try {
      // System.out.println("\nAnalyzing map " + map.getId() + " (" + map.getName() + ")");
      Paths paths = futurePath.get();

      writeAnalyzeFile(paths);
      writeMoveFile(paths);
      writeImageFile(paths);
    } catch (InterruptedException | ExecutionException e) {
      logger.severe(e.getMessage());
    }

    threadPool.shutdown();
  }

  private void writeAnalyzeFile(Paths paths) {
    short minLength = (short) paths.getMinTotalLength();
    MutableList<Move> selectedStartMoves = paths.getMovesOfRound(0);
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
    StringBuilder result = new StringBuilder().append("Map ").append(map.getId()).append(" has ").append(minCount)
        .append("/").append(startMoves.size()).append(" optimal starts, length ").append(minLength)
        .append(", bottleneck ").append(minPathWidth).append("\n");
    analyzeMoves.append("\n").append(result);
    System.out.println(result.toString());

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

  private void writeMoveFile(Paths path) {
    StringBuilder analyzeMoves = new StringBuilder();
    analyzeMoves.append("Round").append(";").append("x").append(";").append("y").append(";").append("xv").append(";")
        .append("yv").append("\n");

    MutableCollection<Move> allMoves = path.getPartialMoves();
    MutableList<Move> roundMoves = allMoves.toSortedList((m1, m2) -> m1.getTotalLen() - m2.getTotalLen());

    for (Move move : roundMoves) {
      analyzeMoves.append(move.getTotalLen()).append(";").append(move.getX());
      analyzeMoves.append(";").append(move.getY());
      analyzeMoves.append(";").append(move.getXv());
      analyzeMoves.append(";").append(move.getYv()).append(";");
      analyzeMoves.append("\n");
    }

    File file = new File(fileName + ".csv");
    FileWriter fw = null;
    try {
      fw = new FileWriter(file);
      fw.write(analyzeMoves.toString());
      fw.close();
    } catch (IOException e) {
      logger.warning(e.getMessage());
    }
  }

  private void writeImageFile(Paths paths) {
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
