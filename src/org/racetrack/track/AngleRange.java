package org.racetrack.track;

import org.racetrack.karoapi.*;

public class AngleRange {

  private double offset = 0d;
  private double lower = 0d;
  private double upper = 0d;

  public AngleRange(Move move) {
    offset = move.getAngle();
  }

  public void add(Move move) {
    double normalized = getNormalizedAngle(move);

    if (normalized < lower) {
      lower = normalized;
    }
    if (normalized > upper) {
      upper = normalized;
    }
  }

  private int getRange() {
    return (int) (Math.abs(lower) + Math.abs(upper));
  }

  public boolean isRangeSmallerThanPi() {
    return getRange() <= 180;
  }

  /**
   * Tells if move-angle is in angle-range
   */
  public boolean isInRange(Move move) {
    double median = (upper + lower) / 2;
    return Math.abs(getNormalizedAngle(move) - median) < 90;
  }

  public boolean isNotInRange(Move move) {
    double median = (upper + lower) / 2;
    return Math.abs(getNormalizedAngle(move) - median) > 90;
  }

  private double getNormalizedAngle(Move move) {
    double normalized = move.getAngle() - offset;
    if (normalized > 180)
      return normalized - 360;
    if (normalized < -180)
      return normalized + 360;
    return normalized;
  }

}
