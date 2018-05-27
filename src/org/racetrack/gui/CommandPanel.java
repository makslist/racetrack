package org.racetrack.gui;

import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;

import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public class CommandPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  private JButton reloadButton;
  private JButton navigateButton;
  private JCheckBox circuitBox;
  private JTextField maxToursField;
  private JCheckBox quitButton;
  private JButton saveButton;
  private JSlider resolutionSlider;

  private KaroMap map;
  private GameRule rule;

  public CommandPanel(KaroMap map, GameRule rule) {
    this.map = map;
    this.rule = rule;

    reloadButton = new JButton("Reload");
    navigateButton = new JButton("Navigate");
    circuitBox = new JCheckBox("Circuit");
    maxToursField = new JTextField(3);
    quitButton = new JCheckBox("Instant Quit");
    saveButton = new JButton("Save");

    resolutionSlider = new JSlider(SwingConstants.HORIZONTAL, 8, 40, GamePanel.DEFAULT_SCALE);
    resolutionSlider.setMajorTickSpacing(4);
    resolutionSlider.setPaintTicks(true);
    resolutionSlider.setPaintLabels(true);
    resolutionSlider.setSnapToTicks(true);

    add(reloadButton);
    add(navigateButton);
    add(circuitBox);
    add(maxToursField);
    add(new JLabel("Max tours"));
    add(quitButton);
    add(saveButton);
    add(resolutionSlider);
  }

  public void addReloadButtonActionListener(ActionListener l) {
    reloadButton.addActionListener(l);
  }

  public void addNavigationButtonActionListener(ActionListener l) {
    navigateButton.addActionListener(l);
  }

  public void addSaveButtonActionListener(ActionListener l) {
    saveButton.addActionListener(l);
  }

  public void addResolutionSliderChangeListener(ChangeListener l) {
    resolutionSlider.addChangeListener(l);
  }

  public void addCircuitBoxChangeListener(ChangeListener l) {
    if (map != null & rule != null) {
      circuitBox.setSelected(rule.isMapCircuit());
    }
    circuitBox.addChangeListener(l);
  }

  public void addMaxToursFieldChangeListener(KeyListener l) {
    if (map != null) {
      maxToursField.setText(String.valueOf(map.getMaxTours()));
    }
    maxToursField.addKeyListener(l);
  }

  public void addQuitBoxChangeListener(ChangeListener l) {
    if (map != null) {
      quitButton.setSelected(map.isQuit());
    }
    quitButton.addChangeListener(l);
  }

}
