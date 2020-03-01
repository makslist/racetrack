package org.racetrack.gui;

import java.awt.*;

import javax.swing.*;

import org.eclipse.collections.api.collection.*;
import org.racetrack.karoapi.*;

public class TrackPanel extends JPanel {

  private static final long serialVersionUID = -7680729436783774602L;

  private MutableCollection<LogMove> moves;
  private int scale;

  public TrackPanel(MutableCollection<LogMove> moves, int scale) {
    this.moves = moves;
    this.scale = scale;

    setBackground(GamePanel.BACKGROUND_DEFAULT);
  }

  public void setScale(int scale) {
    this.scale = scale;
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2d = (Graphics2D) g;

    for (Move move : moves) {
      if (move.getPathLen() <= 0) {
        g2d.setColor(Color.WHITE);
        int x1 = (move.getX() - move.getXv()) * scale + scale / 2;
        int y1 = (move.getY() - move.getYv()) * scale + scale / 2;
        if (move.isCrash()) {
          x1 = move.getPred().getX() * scale + scale / 2;
          y1 = move.getPred().getY() * scale + scale / 2;
          g2d.setColor(Color.RED);
        }
        int x2 = (move.getX()) * scale + scale / 2;
        int y2 = (move.getY()) * scale + scale / 2;
        g2d.drawLine(x1, y1, x2, y2);
        g2d.fillOval(x2 - scale / 4, y2 - scale / 4, scale / 2, scale / 2);
        Font font = new Font(g2d.getFont().getFontName(), Font.PLAIN, 9);
        g2d.setFont(font);
        g2d.setColor(Color.BLACK);
        g2d.drawString(Integer.toString(move.getTotalLen()), (x2 + x1) / 2, (y2 + y1) / 2);
      }
    }
  }

}
