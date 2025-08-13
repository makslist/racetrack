package de.karopapier.kabotte.test.track;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.karopapier.api.Game;
import de.karopapier.api.Game.Crash;
import de.karopapier.api.Game.Dir;
import de.karopapier.api.KaroMap;
import de.karopapier.api.Rule;
import de.karopapier.kabotte.rules.KaroMapSetting;
import de.karopapier.kabotte.rules.RuleFactory;
import de.karopapier.kabotte.test.track.TrackSnapshot.GameMode;
import de.karopapier.kabotte.track.PathFinder;
import de.karopapier.kabotte.track.Paths;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ScoutTester {

    private final static List<Integer> hardMaps = List.of(125, 145, 155, 167, 207, 215, 10007, 10015, 10033, 10041, 10042, 10059); //10519
    private final KaroMap map;

    public ScoutTester(KaroMap map) {
        this.map = map;
    }

    public static void main(String[] args) {
        long startTimeMillis = System.currentTimeMillis();

//        KaroMap.getAll().filter(m -> m.id() == 10023).forEach(m -> new SnapshotTester(m).run());
        KaroMap.getAll().filter(m -> !hardMaps.contains(m.id())).forEach(m -> new ScoutTester(m).run());
//        KaroMap.getAll().filter(m -> hardMaps.contains(m.id())).forEach(m -> new SnapshotTester(m).run());

        System.out.println(" \n" + "Snapshot-test took :" + (System.currentTimeMillis() - startTimeMillis) / 1000 + "s");
        System.exit(0);
    }

    private static GameMode analyzeGame(Game game) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        PathFinder scout = new PathFinder(game, game.next());
        Future<Paths> futurePaths = executor.submit(scout);
        try {
            Paths path = futurePaths.get();
            int startMoves = path.movesInRound(1).size();
            int minLength = path.length() - 1; //TODO all reference plus 1
            return new GameMode(game.direction(), game.withCps(), startMoves, minLength);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("Error scout testing on map " + game.map().id());
            return null;
        } finally {
            executor.shutdownNow();
        }
    }

    public void run() {
        try {
            System.out.print("Map " + map.id() + " (" + map.name() + ") ");

            if (map.isNight())
                return;

            map.cps().stream().filter(c -> map.getStartMoves(c).isEmpty()).forEach(c -> System.err.println("CPs don't match: " + c));

            KaroMapSetting mapSetting = KaroMapSetting.readSettings(map);

            File path = new File("snapshot" + File.separator + "scout");
            if (path.exists() || path.mkdir()) {
                File checkFile = new File(path, map.id() + ".json");
                ObjectMapper mapper = new ObjectMapper();
                TrackSnapshot check = checkFile.exists() ? mapper.readValue(checkFile, TrackSnapshot.class) : new TrackSnapshot(map.id());

                long startTime = System.currentTimeMillis();
                System.out.print(".");
                boolean updateFile = false;
                Game testGame = Game.createTestGame(map, false, Dir.classic, Crash.forbidden, 2);
                Rule mapRule = RuleFactory.getInstance(testGame);
                if (!mapSetting.getQuitModes().noCps()) {
                    updateFile = check.addOrCheck(analyzeGame(testGame));
                }
                if (map.hasCps() && !mapSetting.getQuitModes().cps()) {
                    System.out.print(".");
                    updateFile = check.addOrCheck(analyzeGame(Game.createTestGame(map, true, Dir.classic, Crash.forbidden, 2))) || updateFile;
                }
                if (map.hasCps() && mapRule.isMapCircuit() && !mapSetting.getQuitModes().formula1()) {
                    System.out.print(".");
                    updateFile = check.addOrCheck(analyzeGame(Game.createTestGame(map, true, Dir.formula1, Crash.forbidden, 2))) || updateFile;
                }
                if (map.hasCps() && mapRule.isMapCircuit() && !mapSetting.getQuitModes().free()) {
                    System.out.print(".");
                    updateFile = check.addOrCheck(analyzeGame(Game.createTestGame(map, true, Dir.free, Crash.forbidden, 2))) || updateFile;
                }

                long duration = (System.currentTimeMillis() - startTime) / 1000;
                if (duration > 20)
                    System.out.println(" took " + duration + "s.");
                else
                    System.out.print("\r");

                if (!checkFile.exists() || updateFile)
                    mapper.writeValue(checkFile, check);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
