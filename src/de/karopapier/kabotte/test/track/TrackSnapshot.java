package de.karopapier.kabotte.test.track;

import de.karopapier.api.Game.Dir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrackSnapshot {

    private final List<GameMode> modes = new ArrayList<>();
    private int mapId;

    public TrackSnapshot() {
    }

    public TrackSnapshot(int mapId) {
        this.mapId = mapId;
    }

    @SuppressWarnings("unused")
    public int getMapId() {
        return mapId;
    }

    @SuppressWarnings("unused")
    public void setMapId(int mapId) {
        this.mapId = mapId;
    }

    @SuppressWarnings("unused")
    public List<GameMode> getModes() {
        return modes;
    }

    @SuppressWarnings("unused")
    public void setModes(GameMode[] modes) {
        this.modes.addAll(Arrays.asList(modes));
    }

    public boolean addOrCheck(GameMode data) {
        int index = modes.indexOf(data);
        if (index != -1) {
            GameMode check = modes.get(index);
            if (check.moveCount != data.moveCount || check.optimalStarts != data.optimalStarts) {
                System.err.println("map: " + mapId + " mode:" + check.startDirection + " cps:" + check.cps
                        + (check.moveCount != data.moveCount ? " movecounts: " + data.moveCount + "/" + check.moveCount : "")
                        + (check.optimalStarts != data.optimalStarts
                        ? " optimal starts: " + data.optimalStarts + "/" + check.optimalStarts
                        : ""));
                return false;
            }
        } else {
            modes.add(data);
        }
        return true;
    }

    public static class GameMode {
        public Dir startDirection;
        public boolean cps;
        public int optimalStarts;
        public int moveCount;

        public GameMode() {
        }

        public GameMode(Dir startDirection, boolean cps, int optimalStarts, int moveCount) {
            this.startDirection = startDirection;
            this.cps = cps;
            this.optimalStarts = optimalStarts;
            this.moveCount = moveCount;
        }

        public void setOptimalStarts(int optimalStarts) {
            this.optimalStarts = optimalStarts;
        }

        public void setMoveCount(int moveCount) {
            this.moveCount = moveCount;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof GameMode data))
                return false;
            return startDirection == data.startDirection && cps == data.cps;
        }
    }

}
