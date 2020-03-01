package org.racetrack.gui;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;

import org.racetrack.karoapi.*;

public class MapPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  private KaroMap map;
  private int scale;

  public MapPanel(KaroMap map, int scale) {
    this.map = map;

    setScale(scale);
    setBackground(GamePanel.BACKGROUND_DEFAULT);
    setOpaque(true);
  }

  public void setScale(int scale) {
    this.scale = scale;
    setPreferredSize(new Dimension(map.getCols() * scale, map.getRows() * scale));
    setBounds(0, 0, map.getCols() * scale, map.getRows() * scale);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    Graphics2D g2d = (Graphics2D) g;

    for (int x = 0; x < map.getCols(); x++) {
      for (int y = 0; y < map.getRows(); y++) {
        MapTile mapTile = map.getTileOf(x, y);
        mapTile.draw(g2d, x, y, scale);
      }
    }
  }

  public ChangeListener getCircuitChangeListener() {
    return new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        JCheckBox box = (JCheckBox) e.getSource();
        boolean isEnabled = box.isSelected();
        map.setCircuit(isEnabled);
      }
    };
  }

  public KeyListener getTourLengthMarginKeyListener() {
    return new KeyListener() {
      @Override
      public void keyTyped(KeyEvent e) {
      }

      @Override
      public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          JTextField field = (JTextField) e.getSource();
          String tourLengthSafetyMargin = field.getText();
          if (tourLengthSafetyMargin != null) {
            map.setTourLengthSafetyMargin(Integer.valueOf(tourLengthSafetyMargin));
          }
        }
      }

      @Override
      public void keyPressed(KeyEvent e) {
      }
    };
  }

  public ChangeListener getQuitChangeListener() {
    return new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        JCheckBox box = (JCheckBox) e.getSource();
        boolean isEnabled = box.isSelected();
        map.setQuit(isEnabled);
      }
    };
  }

  public ActionListener getMapSettingsSaveListener() {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        map.saveSettings();
      }
    };
  }

}
