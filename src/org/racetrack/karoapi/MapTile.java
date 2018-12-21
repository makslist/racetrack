package org.racetrack.karoapi;

import java.awt.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.impl.list.mutable.*;

public enum MapTile {

  ROAD('O', new Color(210, 210, 210), new Color(180, 180, 180)), GRASS('X', new Color(0, 200, 0),
      new Color(0, 180, 0)), NIGHT('.', Color.BLACK, Color.BLACK), FINISH('F', Color.WHITE, Color.BLACK), GOLD('G',
          new Color(255, 201, 14), new Color(255, 255, 0)), LAVA('L', new Color(240, 0, 0), new Color(180, 0, 0)), SNOW(
              'N', Color.WHITE, new Color(220, 220, 220)), PARC('P', new Color(200, 200, 200),
                  new Color(120, 120, 120)), START('S', new Color(60, 60, 60), new Color(160, 160, 160)), TAR('T',
                      Color.BLACK, new Color(40, 40, 40)), MOUNTAIN('V', new Color(100, 100, 100), Color.BLACK), WATER(
                          'W', new Color(0, 60, 200), new Color(0, 30, 100)), SAND('Y', new Color(230, 230, 115),
                              new Color(200, 200, 100)), MUD('Z', new Color(100, 70, 0), new Color(90, 60, 0)), ONE('1',
                                  new Color(0, 102, 255), Color.BLACK), TWO('2', new Color(0, 100, 200),
                                      Color.WHITE), THREE('3', new Color(0, 255, 102), Color.BLACK), FOUR('4',
                                          new Color(0, 200, 0), Color.WHITE), FIVE('5', new Color(255, 255, 0),
                                              Color.BLACK), SIX('6', new Color(200, 200, 0), Color.WHITE), SEVEN('7',
                                                  new Color(255, 0, 0), Color.BLACK), EIGHT('8', new Color(200, 0, 0),
                                                      Color.WHITE), NINE('9', new Color(255, 0, 255), Color.BLACK);

  public static final MutableCollection<MapTile> CHECKPOINTS = FastList.newListWith(ONE, TWO, THREE, FOUR, FIVE, SIX,
      SEVEN, EIGHT, NINE);
  public static final MutableCollection<MapTile> CP_AND_FINISH = FastList.newListWith(ONE, TWO, THREE, FOUR, FIVE, SIX,
      SEVEN, EIGHT, NINE, FINISH);
  public static final MutableCollection<MapTile> DRIVEABLE = FastList.newListWith(START, ROAD, FINISH, ONE, TWO, THREE,
      FOUR, FIVE, SIX, SEVEN, EIGHT, NINE);
  public static final MutableCollection<MapTile> REACHABLE_NEIGHBORS_FOR_FINISH = FastList.newListWith(START, ROAD, ONE,
      TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE);
  public static final MutableCollection<MapTile> OFF_TRACK = FastList.newListWith(GOLD, LAVA, SNOW, PARC, TAR, MOUNTAIN,
      WATER, GRASS, SAND, MUD);
  private static final MutableCollection<MapTile> FINISH_LIST = FastList.newListWith(FINISH);
  private static final MutableCollection<MapTile> ONE_LIST = FastList.newListWith(ONE);
  private static final MutableCollection<MapTile> TWO_LIST = FastList.newListWith(TWO);
  private static final MutableCollection<MapTile> THREE_LIST = FastList.newListWith(THREE);
  private static final MutableCollection<MapTile> FOUR_LIST = FastList.newListWith(FOUR);
  private static final MutableCollection<MapTile> FIVE_LIST = FastList.newListWith(FIVE);
  private static final MutableCollection<MapTile> SIX_LIST = FastList.newListWith(SIX);
  private static final MutableCollection<MapTile> SEVEN_LIST = FastList.newListWith(SEVEN);
  private static final MutableCollection<MapTile> EIGHT_LIST = FastList.newListWith(EIGHT);
  private static final MutableCollection<MapTile> NINE_LIST = FastList.newListWith(NINE);

  private static Color TILE_BOUND_COLOR = new Color(210, 210, 210);

  public static MapTile valueOf(char id) {
    for (MapTile tile : values()) {
      if (tile.value == id)
        return tile;
    }
    return null;
  }

  public static MapTile valueOf(int id) {
    return valueOf(Integer.toString(id).charAt(0));
  }

  private char value;
  private Color foregroundColor;
  private Color backgroundColor;

  MapTile(char value, Color foregroundColor, Color backgroundColor) {
    this.value = value;
    this.foregroundColor = foregroundColor;
    this.backgroundColor = backgroundColor;
  }

  public String asString() {
    return String.valueOf(value);
  }

  public char asChar() {
    return value;
  }

  public boolean isStart() {
    return equals(START);
  }

  public boolean isFinish() {
    return equals(FINISH);
  }

  public boolean isCp() {
    return CHECKPOINTS.contains(this);
  }

  public boolean isCpOrFinish() {
    return isFinish() || isCp();
  }

  public boolean isOffTrack() {
    return OFF_TRACK.contains(this);
  }

  public void draw(Graphics2D g2d, int x, int y, int scale) {
    g2d.setColor(backgroundColor);
    g2d.fillRect(x * scale, y * scale, scale, scale);

    float step = OFF_TRACK.contains(this) || ROAD.equals(this) ? 2f : 3f;
    g2d.setColor(foregroundColor);
    for (int i = 0; i < scale; i++) {
      for (int j = 0; j < scale; j++) {
        if (i % 2 != j % 2) {
          int stepInt = Math.round(step);
          g2d.fillRect(x * scale + Math.round(i * step), y * scale + Math.round(j * step), stepInt, stepInt);
        }
      }
    }

    g2d.setColor(TILE_BOUND_COLOR);
    g2d.drawRect(x * scale, y * scale, scale, scale);
  }

  public MutableCollection<MapTile> asList() {
    switch (this) {
    case FINISH:
      return FINISH_LIST;
    case ONE:
      return ONE_LIST;
    case TWO:
      return TWO_LIST;
    case THREE:
      return THREE_LIST;
    case FOUR:
      return FOUR_LIST;
    case FIVE:
      return FIVE_LIST;
    case SIX:
      return SIX_LIST;
    case SEVEN:
      return SEVEN_LIST;
    case EIGHT:
      return EIGHT_LIST;
    case NINE:
      return NINE_LIST;
    default:
      return FastList.newListWith(this);
    }
  }

}
