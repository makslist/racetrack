package de.karopapier.kabotte.rules;

import de.karopapier.api.*;
import de.karopapier.api.Game.Dir;
import de.karopapier.api.KaroMap.Tile;
import de.karopapier.kabotte.track.PathFinder;
import de.karopapier.kabotte.utils.SeqLock;
import org.eclipse.collections.api.map.primitive.MutableLongBooleanMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongBooleanHashMap;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;

public class MapRule implements Rule {

    protected static final String SPECIAL_MARKER = "(�|$)";
    private static final int MAX_DIST_FINISH_FOR_F1 = 7;

    protected final KaroMap map;
    protected final Rule.Flagman flagman = new Flagman();
    private final ReadWriteLock offTrackLock = new SeqLock(false);
    private final MutableLongBooleanMap offTrack = new LongBooleanHashMap(2 << 18);
    private final KaroMapSetting setting;
    protected Game game;
    private Boolean isMapCircuitCached;

    public MapRule(Game game) {
        this(game.map());
        this.game = game;
    }

    protected MapRule(KaroMap map) {
        this.map = map;
        setting = KaroMapSetting.readSettings(map);
        PathFinder.qualifying(this, this.map.getStartMoves(Tile.START));
    }

    public boolean hasCrossed(int x, int y, int xv, int yv, Collection<KaroMap.Tile> tiles, boolean withCpRule) {
        int xStart = x - xv;
        int yStart = y - yv;
        int x0 = x;
        int y0 = y;
        int incx = -Integer.compare(xv, 0);
        int incy = -Integer.compare(yv, 0);
        int dx = Math.abs(xv);
        int dy = Math.abs(yv);
        int pdx;
        int pdy;
        int qdx;
        int qdy;
        int ddx;
        int ddy;
        float el;
        float es;
        if (dx > dy) {
            pdx = incx;
            pdy = 0;
            qdx = 0;
            qdy = incy;
            ddx = incx;
            ddy = incy;
            es = (float) dy;
            el = (float) dx;
        } else {
            pdx = 0;
            pdy = incy;
            qdx = incx;
            qdy = 0;
            ddx = incx;
            ddy = incy;
            es = (float) dx;
            el = (float) dy;
        }

        float err = (el - es) / 2f;
        while (true) {
            KaroMap.Tile tile = map.getTileOf(x0, y0);
            if (tiles.contains(tile))
                return true;
            if (withCpRule && tile.isCpOrFinish())
                return false;
            if (x0 == xStart && y0 == yStart)
                return false;

            if (err < 0f) {
                err += el;
                x0 += qdx;
                y0 += qdy;
            } else if (err > 0f) {
                err -= es;
                x0 += pdx;
                y0 += pdy;
            } else {
                err += el;
                err -= es;
                x0 += ddx;
                y0 += ddy;
            }
        }
    }

    private boolean isOnTrack(int x, int y, int xv, int yv) {
        long key = Move.hashKey(x, y, xv, yv);
        SeqLock.SeqReadLock lock = (SeqLock.SeqReadLock) offTrackLock.readLock();
        try {
            while (true) {
                long counter = lock.tryReadLock();
                boolean isOffTrack = offTrack.getOrThrow(key);
                if (lock.retryReadLock(counter))
                    return !isOffTrack;
            }
        } catch (IllegalStateException ise) {
            boolean isOffTrack = !map.contains(x, y) || hasCrossed(x, y, xv, yv, Tile.OFF_TRACK, false);
            offTrackLock.writeLock().lock();
            offTrack.put(key, isOffTrack);
            offTrackLock.writeLock().unlock();
            return !isOffTrack;
        }
    }

    public boolean isMapCircuit() {
        if (isMapCircuitCached != null)
            return isMapCircuitCached;

        if (setting.isSet() && setting.isCircuit() != null)
            isMapCircuitCached = setting.isCircuit();
        else if (map.isClustered(Tile.FINISH)) {
            isMapCircuitCached = flagman.isContinuousFinishline() ? Boolean.TRUE : Boolean.FALSE;
        } else
            isMapCircuitCached = Boolean.FALSE;

        return isMapCircuitCached;
    }

    public Collection<Move> filteredPossibles(Player player) {
        return player.nextMoves(game, this);
    }

    @Deprecated
    public Collection<Move> filterNext(Move move) {
        return move.next(this);
    }

    public boolean isValid(Move move) {
        return isValid(move.x, move.y, move.xv, move.yv, move.round, move.preds);
    }

    @Override
    public boolean isValid(int x, int y, int xv, int yv, int round, Collection<Move> predecessors) {
        return true;
    }

    @Override
    public boolean isValidMap(int x, int y, int xv, int yv, int round, Collection<Move> predecessors) {
        return (xv != 0 || yv != 0) && isOnTrack(x, y, xv, yv);
    }

    public boolean hasCrossedCp(Move move, Tile cp) {
        return hasCrossed(move.x, move.y, move.xv, move.yv, cp.asList(), true);
    }

    public boolean hasCrossedYet(Move move, List<Tile> tiles) {
        List<Move> moves = move != null ? List.of(move) : List.of();//.getPreds().toList();
        while (!moves.isEmpty()) {
            if (moves.stream().anyMatch(m -> hasCrossed(m.x, m.y, m.xv, m.yv, tiles, true)))
                return true;
            moves = moves.stream().flatMap(Move::getPreds).toList();
        }
        return false;
    }

    @Override
    public boolean hasToCrossFinish(Move move) {
        return isMapCircuit() && game.isFormula1() && (move == null || !hasCrossedYet(move, Tile.FINISH.asList()));
    }

    public boolean isFinishLineCrossedLegally(Move move) {
        return !hasCrossedFinish(move) || flagman.takeCheckeredFlag(move, game.direction());
    }

    public boolean hasCrossedFinish(Move move) {
        return isMapCircuit() && (game.isFormula1() || game.isClassic()) && hasCrossed(move.x, move.y, move.xv, move.yv, Tile.FINISH.asList(), false);
    }

    @Override
    public boolean areNextSafe(Move move, int depth) {
        return depth == 0 || move.next(this).stream().anyMatch(m -> areNextSafe(m, depth - 1));
    }

    public Optional<Boolean> withdraw(Player player) {
        if (game != null && (game.creator().equals(player.name()) || game.currentRound() > 2))
            return Optional.of(false);
        if (setting.getQuitModes().noCps())
            return Optional.of(true);
        if (setting.getQuitModes().cps() && game.isClassic())
            return Optional.of(true);
        if (setting.getQuitModes().formula1() && game.isFormula1() && isMapCircuit())
            return Optional.of(true);
        if (setting.getQuitModes().free() && game.isFree())
            return Optional.of(true);
        if (game != null && (game.name().contains("CraZZZy Crash Challenge"))) //TODO move in separate Rule
            return Optional.of(false);
        if (game != null && (game.isCrashAllowed() && game.zzz() >= 6))
            return Optional.of(true);
        if (game != null && (game.isCrashAllowed() && game.isWithIq()))
            return Optional.of(true);
        return Optional.empty();
    }

    public Rule.Flagman flagman() {
        return flagman;
    }

    public static class Flagman implements Rule.Flagman {

        private double offset;
        private double lower = 0d;
        private double upper = 0d;
        private double center = 0d;
        private int startFinishDist = 0;

        public void add(Move move) {
            if (startFinishDist == 0) {
                startFinishDist = move.round;
                offset = move.angle();
            }
            double angle = getNormalizedAngle(move);
            lower = Math.min(lower, angle);
            upper = Math.max(angle, upper);
            center = (upper + lower) / 2;
        }

        public boolean isContinuousFinishline() {
            if (!isSmallerThanPi() || 0 >= startFinishDist) return false;
            return startFinishDist <= MAX_DIST_FINISH_FOR_F1;
        }

        private boolean isSmallerThanPi() {
            return (int) (Math.abs(lower) + Math.abs(upper)) <= 180;
        }

        /**
         * Tells if move-angle is in angle-range
         */
        public boolean takeCheckeredFlag(Move move, Dir direction) {
            return switch (direction) {
                case formula1 -> Math.abs(getNormalizedAngle(move) - center) < 90;
                case classic -> Math.abs(getNormalizedAngle(move) - center) > 90;
                default -> true;
            };
        }

        private double getNormalizedAngle(Move move) {
            double normalized = move.angle() - offset;
            if (normalized > 180)
                return normalized - 360;
            if (normalized < -180)
                return normalized + 360;
            return normalized;
        }
    }

}