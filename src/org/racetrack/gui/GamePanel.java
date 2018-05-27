package org.racetrack.gui;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;

import org.racetrack.karoapi.*;
import org.racetrack.track.*;

public class GamePanel extends JLayeredPane {

  private static final long serialVersionUID = 8715692704972721642L;

  public static final Color BACKGROUND_DEFAULT = new Color(60, 60, 60, 0);
  public static final int DEFAULT_SCALE = 12;

  private MapPanel mapPanel;
  private TrackPanel trackPanel;
  private NavigationPanel navPanel;

  public GamePanel(Game game, User user, int scale) {
    super();

    setOpaque(true);

    if (game != null) {
      KaroMap map = game.getMap();
      mapPanel = new MapPanel(map, scale);

      Player player = game.getPlayer(user);
      PathFinder pathFinder = new BlockingPathFinder(game, player);

      navPanel = new NavigationPanel(pathFinder, scale);
      navPanel.setSize(mapPanel.getSize());
      add(navPanel, JLayeredPane.DEFAULT_LAYER);

      trackPanel = new TrackPanel(player.getMoves(), scale);
      trackPanel.setSize(mapPanel.getSize());
      add(trackPanel, JLayeredPane.DEFAULT_LAYER);

      add(mapPanel, JLayeredPane.FRAME_CONTENT_LAYER);
      setPreferredSize(mapPanel.getSize());
    } else {
      setPreferredSize(new Dimension(500, 500));
    }
  }

  public ChangeListener getCircuitChangeListener() {
    return mapPanel != null ? mapPanel.getCircuitChangeListener() : null;
  }

  public KeyListener getMaxToursKeyListener() {
    return mapPanel != null ? mapPanel.getMaxToursKeyListener() : null;
  }

  public ChangeListener getQuitChangeListener() {
    return mapPanel != null ? mapPanel.getQuitChangeListener() : null;
  }

  public ActionListener getMapSettingsSaveListener() {
    return mapPanel != null ? mapPanel.getMapSettingsSaveListener() : null;
  }

  public ActionListener getNavigateListener() {
    return navPanel == null ? null : navPanel.getPredictNextMoveListener();
  }

  public ChangeListener getResolutionListener() {
    return new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        JSlider slider = (JSlider) e.getSource();
        int scale = slider.getValue();
        mapPanel.setScale(scale);
        navPanel.setScale(scale);
        trackPanel.setScale(scale);

        Dimension size = mapPanel.getSize();
        navPanel.setSize(size);
        trackPanel.setSize(size);
        setPreferredSize(size);

        revalidate();
        repaint();
      }
    };
  }

}
