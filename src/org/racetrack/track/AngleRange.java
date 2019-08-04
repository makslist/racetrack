package org.racetrack.track;

import org.racetrack.karoapi.*;

public class AngleRange {

  private double offset = 0d;
  private double lower = 0d;
  private double upper = 0d;
  private double center = 0d;

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

    center = (upper + lower) / 2;
  }

  public boolean isRangeSmallerThanPi() {
    return (int) (Math.abs(lower) + Math.abs(upper)) <= 180;
  }

  /**
   * Tells if move-angle is in angle-range
   */
  public boolean isInRange(Move move) {
    return Math.abs(getNormalizedAngle(move) - center) < 90;
  }

  public boolean isNotInRange(Move move) {
    return Math.abs(getNormalizedAngle(move) - center) > 90;
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
