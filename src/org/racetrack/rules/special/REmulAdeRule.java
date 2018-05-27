package org.racetrack.rules.special;

import java.util.concurrent.locks.*;

import org.eclipse.collections.api.block.predicate.*;
import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.api.list.*;
import org.eclipse.collections.api.map.primitive.*;
import org.eclipse.collections.impl.map.mutable.primitive.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.*;
import org.racetrack.track.*;

public class REmulAdeRule extends GameRule {

  public static final String TITLE_RE = "§ Rundenerster wiederholt seinen Zug §";
  public static final String TITLE_REMULADE = "§ REmulAde §";

  private static final float MAX_SAFE_VALUE = 3f;
  private static final float RE_COOLDOWN = 0.45f;
  private static final float SINGLE_MOVE_RE_COOLDOWN = 0.5f;

  private final ReadWriteLock reSafeLock = new SeqLock(false);
  private MutableIntFloatMap reSafeCount = new IntFloatHashMap(2 << 20);

  private Predicate<Move> isRepeat = move -> move.isRepeat();
  private Predicate<Move> isReSafe = move -> isReSafe(move) > 1.3f;

  public REmulAdeRule(Game game) {
    super(game);
  }

  @Override
  public Paths filterPossibles(Paths possibles) {
    MutableCollection<Move> nextMoves = possibles.getEndMoves();

    if (possibles.isInCurrentRound()
        && game.getPosInRoundOfCurrentPlayer() <= Math.floorDiv(game.getActivePlayersCount(), 7) + 1) {
      if (nextMoves.noneSatisfy(isRepeat))
        return Paths.onlyFiltered(possibles, nextMoves.select(isReSafe));

      if (game.getActivePlayersCount() <= 3 && game.wasPlayerReInLastRound()) {
        Paths filtered = Paths.onlyFiltered(possibles, nextMoves.select(isReSafe));
        filtered.setComment("RE-Schutz: Wiederholung.");
        return filtered;
      }

      MutableList<Player> playerOnField = game.getPlayers()
          .select(player -> nextMoves.anySatisfy(move -> move.isRepeat() && move.equalsPos(player.getLastmove())));
      if (!playerOnField.isEmpty()) {
        Paths filtered = Paths.onlyFiltered(possibles, nextMoves.select(isReSafe));
        filtered.setComment("RE-Schutz: Spieler " + playerOnField.getFirst().getName() + " steht noch auf dem Feld.");
        return filtered;
      }
      return possibles.filterPossibles(isRepeat);
    }

    return Paths.onlyFiltered(possibles, nextMoves.select(isReSafe));
  }

  @Override
  public MutableCollection<Move> filterNextMv(Move move) {
    MutableCollection<Move> nextMoves = super.filterNextMv(move);

    if (nextMoves.isEmpty()) {
      reSafeLock.writeLock().lock();
      reSafeCount.put(move.hashCode(), 0f);
      reSafeLock.writeLock().unlock();
      return nextMoves;
    }

    return nextMoves.select(isReSafe);
  }

  private float isReSafe(Move move) {
    int key = move.hashCode();
    SeqLock.SeqReadLock lock = (SeqLock.SeqReadLock) reSafeLock.readLock();
    try {
      while (true) {
        long counter = lock.tryReadLock();
        float isReSafe = reSafeCount.getOrThrow(key);
        if (lock.retryReadLock(counter))
          return isReSafe != -1 ? isReSafe : 1;
      }
    } catch (IllegalStateException ise) {
      reSafeLock.writeLock().lock();
      reSafeCount.put(key, -1f);
      reSafeLock.writeLock().unlock();
      float maxReSafe = 0f;
      MutableCollection<Move> nextMoves = move.getNext().select(mapRule)
          .toSortedList((s1, s2) -> (int) (s1.getSpeed() - s2.getSpeed()));
      if (nextMoves.isEmpty()) {
        maxReSafe = hasXdCp(move, MapTile.FINISH) ? MAX_SAFE_VALUE : 0f;
      } else {
        Move rePeat = nextMoves.detect(isRepeat);
        if (rePeat != null) {
          maxReSafe = isReSafe(rePeat) + (rePeat.getTaxiSpeed() > 1 ? RE_COOLDOWN : SINGLE_MOVE_RE_COOLDOWN);
        } else {
          for (Move nextMove : nextMoves) {
            float reSafe = isReSafe(nextMove);
            if (reSafe > maxReSafe) {
              maxReSafe = reSafe;
            }
            if (maxReSafe >= MAX_SAFE_VALUE) {
              break;
            }
          }
          maxReSafe *= 1.5f;
        }
      }
      maxReSafe = Math.min(maxReSafe, MAX_SAFE_VALUE);
      reSafeLock.writeLock().lock();
      reSafeCount.put(key, maxReSafe);
      reSafeLock.writeLock().unlock();
      return maxReSafe;
    }
  }

}
