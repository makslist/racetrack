package de.karopapier.kabotte.rules;

import de.karopapier.api.Rule;
import de.karopapier.kabotte.rules.special.*;

public class RuleFactory {

    private RuleFactory() {
    }

    public static Rule getInstance(de.karopapier.api.Game game) {
        String title = game.name();

        if (DoubleMoveRule.matches(title))
            return new DoubleMoveRule(game);
        if (REmulAdeRule.matches(title))
            return new REmulAdeRule(game);
        if (NumpadRule.matches(title))
            return new NumpadRule(game);
        if (EdgeRule.matches(title))
            return new EdgeRule(game);
        if (EdgeDirRule.matches(title))
            return new EdgeDirRule(game);
        if (BrakeClampRule.matches(title))
            return new BrakeClampRule(game);
        if (SpeedLimitRule.matches(title))
            return new SpeedLimitRule(game);
        if (X0orY0Rule.matches(title))
            return new X0orY0Rule(game);
        if (KaroLigaRule.matches(title))
            return new KaroLigaRule(game);
        if (NonEdgeRule.matches(title))
            return new NonEdgeRule(game);
        return new MapRule(game);
    }

}
