package org.racetrack.rules.special;

import org.racetrack.karoapi.*;
import org.racetrack.rules.*;

public class CraZZZyRule extends GameRule {

  public static final String TITLE = "CraZZZy Crash Challenge";

  public CraZZZyRule(Game game) {
    super(game);
  }

  // @Override
  // public boolean hasForbidXdFinishline(Move move) {
  // // ensure that at least one crash has happened
  // return isMapCircuit() && hasXdFinishline(move)
  // && (!isXingFinishlineAllowed(move) || !CrashDetector.hasCrashHappend(move, Integer.MAX_VALUE));
  // }

}
