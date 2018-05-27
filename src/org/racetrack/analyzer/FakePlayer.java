package org.racetrack.analyzer;

import java.util.*;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.karoapi.*;

public class FakePlayer extends Player {

  public FakePlayer(int id, MutableCollection<MapTile> missingCps, Collection<Move> possibles) {
    this.id = id;
    this.missingCps = missingCps;
    this.possibles = new FastList<>(possibles);
    dran = true;
    status = Status.OK;
    moves = new FastList<>(0);
    moveCount = 0;
    crashCount = 0;
  }

}
