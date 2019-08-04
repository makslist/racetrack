package org.racetrack.gui;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

import org.eclipse.collections.api.list.*;
import org.racetrack.karoapi.*;
import org.racetrack.track.*;

public class NavigationPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  private PathFinder pathFinder;
  private Paths paths = null;
  private int scale;

  public NavigationPanel(PathFinder pathFinder, int scale) {
    this.pathFinder = pathFinder;
    this.scale = scale;

    setBackground(GamePanel.BACKGROUND_DEFAULT);
  }

  public NavigationPanel(Paths paths, int scale, int cols, int rows) {
    this.paths = paths;
    this.scale = scale;

    setBackground(GamePanel.BACKGROUND_DEFAULT);
    setPreferredSize(new Dimension(cols * scale, rows * scale));
    setBounds(0, 0, cols * scale, rows * scale);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
    int brightnessRange = 100;

    if (paths != null) {
      int endLevel = paths.getMinTotalLength();
      int startLevel = endLevel - paths.getMinLength();
      for (int i = endLevel + 1; i > startLevel; i--) {
        MutableList<Move> levelMoves = paths.getMovesOfRound(i);

        for (Move move : levelMoves) {
          int offset = endLevel - (move.getTotalLen() - move.getPathLen());
          if (move.getPathLen() >= 1) {
            if (move.getPathLen() == 1) {
              g2d.setColor(Color.BLACK);
            } else {
              int brightness = 50 + brightnessRange - (brightnessRange / offset) * move.getPathLen();
              g2d.setColor(new Color(brightness, brightness, brightness));
            }
            int x1 = (move.getX() - move.getXv()) * scale + scale / 2;
            int y1 = (move.getY() - move.getYv()) * scale + scale / 2;
            if (move.isCrash()) {
              x1 = move.getPred().getX() * scale + scale / 2;
              y1 = move.getPred().getY() * scale + scale / 2;
              g2d.setColor(Color.RED);
            }
            int x2 = move.getX() * scale + scale / 2;
            int y2 = move.getY() * scale + scale / 2;
            g2d.drawLine(x1, y1, x2, y2);
            g2d.fillOval(x2 - scale / 4, y2 - scale / 4, scale / 2, scale / 2);
            Font font = new Font(g2d.getFont().getFontName(), Font.PLAIN, 9);
            g2d.setFont(font);
            g2d.drawString(Short.toString(move.getTotalLen()), (x2 + x1) / 2, (y2 + y1) / 2);
          }
        }
      }
    }
  }

  public void setScale(int scale) {
    this.scale = scale;
  }

  public ActionListener getPredictNextMoveListener() {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        try {
          PathWorker pathWorker = new PathWorker((JFrame) SwingUtilities.getWindowAncestor(NavigationPanel.this),
              pathFinder);
          pathWorker.execute();
          paths = pathWorker.get();

          pathWorker.cancel(true);
        } catch (Exception ex) {
          ex.printStackTrace();
        }

        getParent().revalidate();
        getParent().repaint();
      }
    };
  }

  private class PathWorker extends SwingWorker<Paths, Void> {
    private final JFrame frame;
    private PathFinder finder;

    public PathWorker(JFrame frame, PathFinder finder) {
      frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      this.frame = frame;
      this.finder = finder;
    }

    @Override
    public Paths doInBackground() throws Exception {
      return finder.call();
    }

    @Override
    public void done() {
      frame.setCursor(Cursor.getDefaultCursor());
    }
  }

}
