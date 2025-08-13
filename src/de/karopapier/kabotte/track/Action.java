package de.karopapier.kabotte.track;

import de.karopapier.api.Game;
import de.karopapier.api.Move;

import java.util.ArrayList;
import java.util.List;

public class Action {

    public final Game game;
    public final List<GTS.WeightedMove> moveScores = new ArrayList<>();
    public boolean withdraw;
    public boolean skipTurn;
    public boolean notMyTurn;
    public boolean crashed;
    public boolean checkeredFlag;
    public String comment;
    private Move move;

    private Action(Game game) {
        this.game = game;
    }

    public Action(Game game, List<GTS.WeightedMove> moveScores, Move move, boolean checkeredFlag, String comment) {
        this.game = game;
        this.moveScores.addAll(moveScores);
        this.move = move;
        this.checkeredFlag = checkeredFlag;
        this.comment = comment;
    }

    public static Action withdraw(Game game) {
        Action action = new Action(game);
        action.withdraw = true;
        return action;
    }

    public static Action notMyTurn(Game game) {
        Action action = new Action(game);
        action.notMyTurn = true;
        return action;
    }

    public static Action skipTurn(Game game, String reason) {
        Action action = new Action(game);
        action.skipTurn = true;
        action.comment = reason != null ? reason : "";
        return action;
    }

    public static Action crashed(Game game) {
        Action action = new Action(game);
        action.crashed = true;
        return action;
    }

    public Game getGame() {
        return game;
    }

    public boolean withdraw() {
        return withdraw;
    }

    public boolean isNotMyTurn() {
        return notMyTurn;
    }

    public boolean skipTurn() {
        return skipTurn;
    }

    public boolean crashed() {
        return crashed;
    }

    public Move getMove() {
        return move;
    }

    public boolean tookCheckeredFlag() {
        return checkeredFlag;
    }

    public boolean hasComment() {
        return comment != null && !comment.isEmpty();
    }

    public String getComment() {
        return comment;
    }

    public String toString() {
        return game.id() + " (" + game.name() + ") quit: " + withdraw + " skip: " + skipTurn + " notNext: " + notMyTurn + " crash: " + crashed + " move: " + move + " checkeredFlag: " + checkeredFlag + " comment: " + comment;
    }

}
