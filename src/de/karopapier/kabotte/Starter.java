package de.karopapier.kabotte;

import de.karopapier.api.KaroMap;
import de.karopapier.kabotte.config.Settings;
import de.karopapier.kabotte.test.track.ScoutTester;
import de.karopapier.kabotte.worker.KaBotte;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Starter {

    private Starter() {
    }

    public static void main(String[] args) {
        for (String param : args)
            if (param.startsWith("--snapshotTest")) {
                List<KaroMap> analyzeMaps = new ArrayList<>();
                String[] maps = param.split(":");
                if (maps.length >= 2) {
                    for (String mapId : maps[1].split(";"))
                        try {
                            analyzeMaps.add(KaroMap.get(Integer.parseInt(mapId)));
                        } catch (NumberFormatException nfe) {
                            File file = new File("./" + mapId);
                            if (file.exists())
                                analyzeMaps.add(KaroMap.get(file));
                            else {
                                System.out.println("SnapshotTest: File not found: " + file + ".");
                                System.exit(1);
                            }
                        }
                }

                if (analyzeMaps.isEmpty())
                    analyzeMaps.addAll(KaroMap.getAll().toList());

                for (KaroMap map : analyzeMaps)
                    new ScoutTester(map).run();
                System.out.print("\nFinished");

            } else if (param.startsWith("--bot")) {
                String[] filename = param.split(":");
                Settings settings = filename.length >= 2 ? Settings.getInstance(new File("./" + filename[1])) : Settings.getInstance();
                new Thread(new KaBotte(settings)).start();

            } else {
                String help = "No start command set. Please provide parameters:" + "\n" +
                        "  --bot:file.prop" + "\n" +
                        "                Provide a property file with settings for the bot" + "\n" +
                        "  --snapshotTest[:mapId|map.json[;mapId|map.json]]" + "\n" +
                        "                Map-Analyzer" + "\n" +
                        "                For json files, provide relative path to map-file from jar)" + "\n" +
                        "Possible properties to set are:\n" +
                        "    " + Settings.Property.activateChatbot.name() + "=(true|false)" + "\n" +
                        "                Start a chatbot (only in bot mode)" + "\n" +
                        "    " + Settings.Property.maxParallelTourThreads.name() + "\n" +
                        "                Max number of threads to actually calculate the distance of the shortest tour." + "\n" +
                        "                n multi-processor platforms set this to #cores (or #cores-1 to keep the system responsive)" + "\n" +
                        "    " + Settings.Property.user.name() + "\n" + "                Login for bot or gui" + "\n" +
                        "    " + Settings.Property.password.name() + "\n" + "                Password for login" + "\n";
                System.out.print(help);
            }
    }

}
