package org.racetrack.analyzer;

import java.util.*;

import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.karoapi.*;
import org.racetrack.rules.RuleFactory.*;

public class FakeGame extends Game {

  private static final int GAME_ID = 99999;
  private static final int USER_ID = 99999;
  private static final String NAME = "TEST";

  public FakeGame(KaroMap map, RuleType type, boolean withCheckpoints, Dir direction, Crash crashallowed, int zzz) {
    id = 299999 + new Random().nextInt(GAME_ID);
    this.map = map;
    mapId = map.getId();
    name = NAME + " (Map: " + mapId + ")";
    startdirection = direction.toString();
    this.withCheckpoints = withCheckpoints;
    setCrashallowed(crashallowed);
    this.zzz = zzz;

    Collection<Move> startTiles = map.getTilesAsMoves(MapTile.START);
    players = Maps.mutable.of(USER_ID, new FakePlayer(USER_ID, map.getCps(), startTiles));
    dranId = USER_ID;
  }

  public FakeGame(KaroMap map, RuleType type, Move startMove, boolean withCheckpoints, Dir direction,
      Crash crashallowed, int zzz) {
    id = 299999 + new Random().nextInt(GAME_ID);
    this.map = map;
    mapId = map.getId();
    name = NAME + " (Map: " + mapId + ")";
    startdirection = direction.toString();
    this.withCheckpoints = withCheckpoints;
    setCrashallowed(crashallowed);
    this.zzz = zzz;

    players = Maps.mutable.of(USER_ID, new FakePlayer(USER_ID, map.getCps(), FastList.newListWith(startMove)));
    dranId = USER_ID;
  }

  public Player getPlayer() {
    return super.getPlayer(USER_ID);
  }

  @Override
  public Player getPlayer(User user) {
    return super.getPlayer(USER_ID);
  }

  @Override
  public KaroMap getMap() {
    if (map == null) {
      map = KaroMap.get(mapId);
    }
    return map;
  }

  public void setStartMove(Move startMove) {
    Collection<Move> startTiles = FastList.newListWith(startMove);
    players = Maps.mutable.of(USER_ID, new FakePlayer(USER_ID, map.getCps(), startTiles));
  }

}
