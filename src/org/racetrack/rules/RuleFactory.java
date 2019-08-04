package org.racetrack.rules;

import org.racetrack.karoapi.*;
import org.racetrack.rules.special.*;

public class RuleFactory {

  public enum RuleType {

    STANDARD("Non"), RE(REmulAdeRule.TITLE_RE), REMULADE(REmulAdeRule.TITLE_REMULADE), EDGE(EdgeRule.TITLE), EDGE_DIR(
        EdgeDirRule.TITLE), PRIME_ACC(PrimeAccRule.TITLE), REPEAT_MOVE(DoubleMoveRule.TITLE), NUMPAD(
            NumpadRule.TITLE), BRAKE_CLAMP(BrakeClampRule.TITLE), SPEED_LIMIT(
                SpeedLimitRule.TITLE), X_OR_Y_0(XorY0Rule.TITLE), CCC(CraZZZyRule.TITLE);

    public static RuleType valueOfTitle(String gameTitle) {
      if (gameTitle == null)
        return STANDARD;
      for (RuleType rule : values()) {
        if (gameTitle.contains(rule.title))
          return rule;
      }
      return STANDARD;
    }

    private String title;

    private RuleType(String title) {
      this.title = title;

    }

    @Override
    public String toString() {
      return title;
    }

  }

  public static GameRule getInstance(Game game) {
    String title = game.getName();
    RuleType rule = RuleType.valueOfTitle(title);

    switch (rule) {
    case REPEAT_MOVE:
      return new DoubleMoveRule(game);
    case PRIME_ACC:
      return new PrimeAccRule(game);
    case RE:
      return new REmulAdeRule(game);
    case REMULADE:
      return new REmulAdeRule(game);
    case NUMPAD:
      return new NumpadRule(game);
    case EDGE:
      return new EdgeRule(game);
    case EDGE_DIR:
      return new EdgeDirRule(game);
    case BRAKE_CLAMP:
      return new BrakeClampRule(game);
    case SPEED_LIMIT:
      return new SpeedLimitRule(game);
    case X_OR_Y_0:
      return new XorY0Rule(game);
    default:
      return new GameRule(game);
    }
  }

  private RuleFactory() {
  }

}
