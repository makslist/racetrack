package org.racetrack.analyzer;

import org.eclipse.collections.api.collection.*;
import org.eclipse.collections.impl.factory.*;
import org.eclipse.collections.impl.list.mutable.*;
import org.racetrack.karoapi.*;

public class AnalyzePlayer extends Player {

  public AnalyzePlayer(Player player, MutableCollection<Move> possibles) {
    super(player);

    dran = true;
    this.possibles = new FastList<>(possibles);
    missingCps = Lists.mutable.empty();
  }

  public AnalyzePlayer(Player player, MutableCollection<Move> possibles, MutableCollection<MapTile> missingCps) {
    super(player);

    dran = true;
    this.possibles = new FastList<>(possibles);
    this.missingCps = missingCps;
  }

}
