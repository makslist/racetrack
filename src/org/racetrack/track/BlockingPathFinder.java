package org.racetrack.track;

import org.eclipse.collections.api.list.*;
import org.racetrack.karoapi.*;
import org.racetrack.worker.*;

public class BlockingPathFinder extends PathFinder {

  public BlockingPathFinder(Game game, Player player) {
    super(game, player);
  }

  @Override
  public Paths call() throws Exception {
    if (game.getMap().getSetting().isQuit())
      return Paths.getQuitPath(game);

    Paths possiblePaths = player.getPossiblesAsPaths(game);
    if (possiblePaths.isEmpty())
      return possiblePaths;

    crashDetector = new CrashDetector(rule, possiblePaths.getEndMoves());

    if (rule.hasNotXdFinishlineOnF1Circuit(player.getLastmove())) {
      possiblePaths = findPathToCp(possiblePaths, MapTile.FINISH);
    }

    MutableList<Tour> tours = game.isWithCheckpoints() ? tsp.solve(player.getMissingCps(), edgeRuler, true)
        : Tour.SINGLE_FINISH_TOUR;
    if (game.getMap().getSetting().getMaxTours() > 0) {
      tours = tours.take(game.getMap().getSetting().getMaxTours());
    }

    progress = CliProgressBar.getPathBar(game, tours.size());
    Paths shortestPaths = getMinPathsForTours(tours, possiblePaths);

    if (shortestPaths.isEmpty())
      return possiblePaths;

    Blocker blocker = new Blocker(game, tsp, rule);
    blocker.calcMoves(shortestPaths);

    return shortestPaths;
  }

}
