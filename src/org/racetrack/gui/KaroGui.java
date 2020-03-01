package org.racetrack.gui;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;
import javax.swing.event.*;

import org.racetrack.config.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public class KaroGui extends JFrame {

  private static final long serialVersionUID = 1l;

  private static Comparator<Game> gameIdComparator = new Comparator<Game>() {
    @Override
    public int compare(Game o1, Game o2) {
      return o1.getId() - o2.getId();
    }
  };

  private User user;
  private JPanel mainPanel = new JPanel(new BorderLayout());
  private CommandPanel commandPanel;
  private JScrollPane scrollPane;
  private GamePanel gamePanel;
  private JPanel gamesListPanel;
  private Vector<Game> games = null;

  public KaroGui() {
    final Settings settings = Settings.getInstance();
    String userId = settings.getUserLogin();
    String password = settings.getPassword();
    boolean secureConnection = settings.useSecureConnection();

    if (userId == null || password == null) {
      System.out.println("No username or password given");
      System.exit(1);
    } else {
      KaroClient karo = new KaroClient(userId, password, secureConnection, false);
      if (karo.logIn()) {
        user = karo.getUser();
        setTitle("Karopapier.de - User: " + user.getLogin());

        games = new Vector<>(user.getNextGames());
        Collections.sort(games, gameIdComparator);
      } else {
        System.err.println("No user found! Please check username and password.");
        System.exit(1);
      }

      setLocation(50, 50);
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

      initListGamesPanel();

      setContentPane(mainPanel);

      initGamePanels(!games.isEmpty() ? games.firstElement() : null);

      pack();
    }
  }

  private void initListGamesPanel() {
    JList<Game> gamesList = new JList<>(games);
    if (!games.isEmpty()) {
      gamesList.setSelectedIndex(0);
    }
    gamesList.addListSelectionListener(getListListenerForSettingGamePanel());

    JScrollPane scrollPane = new JScrollPane();
    scrollPane.setViewportView(gamesList);

    gamesListPanel = new JPanel(new BorderLayout());
    gamesListPanel.setPreferredSize(new Dimension(250, 0));
    gamesListPanel.add(scrollPane, BorderLayout.CENTER);
    mainPanel.add(gamesListPanel, BorderLayout.WEST);
  }

  private void initGamePanels(Game game) {
    KaroMap map = game != null ? game.getMap() : null;

    gamePanel = new GamePanel(game != null ? game : null, user, GamePanel.DEFAULT_SCALE);
    scrollPane = new JScrollPane(gamePanel);
    scrollPane.getVerticalScrollBar().setUnitIncrement(32);
    scrollPane.getHorizontalScrollBar().setUnitIncrement(32);
    scrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
    mainPanel.add(scrollPane, BorderLayout.CENTER);
    commandPanel = new CommandPanel(map, game != null ? RuleFactory.getInstance(game) : null);
    mainPanel.add(commandPanel, BorderLayout.NORTH);

    commandPanel.addNavigationButtonActionListener(gamePanel.getNavigateListener());
    commandPanel.addReloadButtonActionListener(getReloadGameListListener());
    commandPanel.addSaveButtonActionListener(gamePanel.getMapSettingsSaveListener());
    commandPanel.addCircuitBoxChangeListener(gamePanel.getCircuitChangeListener());
    commandPanel.addMaxToursFieldChangeListener(gamePanel.getTourLengthMarginKeyListener());
    commandPanel.addQuitBoxChangeListener(gamePanel.getQuitChangeListener());
    commandPanel.addResolutionSliderChangeListener(gamePanel.getResolutionListener());

    mainPanel.revalidate();
    mainPanel.repaint();
  }

  private ActionListener getReloadGameListListener() {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        games = new Vector<>(user.getNextGames());
        Collections.sort(games, gameIdComparator);

        mainPanel.remove(commandPanel);
        mainPanel.remove(scrollPane);
        mainPanel.remove(gamesListPanel);

        initListGamesPanel();

        initGamePanels(!games.isEmpty() ? games.firstElement() : null);
      }
    };
  }

  private ListSelectionListener getListListenerForSettingGamePanel() {
    return new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          Game game = (Game) ((JList<?>) e.getSource()).getSelectedValue();
          mainPanel.remove(commandPanel);
          mainPanel.remove(scrollPane);

          initGamePanels(game);
        }
      }
    };
  }

}
