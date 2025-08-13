package de.karopapier.kabotte.rules;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.karopapier.api.KaroMap;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.logging.Logger;

public class KaroMapSetting {

    private static final Logger LOGGER = Logger.getLogger(KaroMapSetting.class.toString());
    private final boolean set;
    private int id;
    private File file;
    private Boolean circuit;
    private int findCpOvershot = 0;
    private int tourLengthSafetyMargin = -1; // because tour length is an approximation, allow more tours to check
    private QuitModes quitmodes;

    public KaroMapSetting() {
        set = true;
    }

    private KaroMapSetting(int id, File file) {
        this.id = id;
        this.file = file;
        set = false;
    }

    public static KaroMapSetting readSettings(KaroMap map) {
        final File file = getFile(map.id());
        if (file.exists()) {
            LOGGER.fine("Loading settings " + file.getAbsolutePath());
            try {
                return new ObjectMapper().readValue(file, KaroMapSetting.class);
            } catch (final IOException ioe) {
                LOGGER.severe(ioe.getMessage());
            }
        } else
            return new KaroMapSetting(map.id(), file);

        return new KaroMapSetting(map.id(), null);
    }

    private static File getFile(int id) {
        File settingsDir = new File("mapsettings/");
        if (!settingsDir.exists()) {
            final CodeSource codeSource = KaroMapSetting.class.getProtectionDomain().getCodeSource();
            final File jarFile;
            try {
                jarFile = new File(codeSource.getLocation().toURI().getPath());
            } catch (URISyntaxException e) {
                throw new RuntimeException("MapSetting not found: " + e.getMessage());
            }
            File parentDir = new File(jarFile.getParentFile().getParentFile(), "mapsettings/");
            if (parentDir.exists())
                settingsDir = parentDir;
            else
                throw new RuntimeException("Directory for map settings not found in " + settingsDir + " or " + parentDir + "."); //TODO
        }
        return new File(settingsDir, id + ".json");
    }

    @JsonProperty("circuit")
    private void setCircuit(String circuit) {
        this.circuit = circuit.isEmpty() ? null : Boolean.valueOf(circuit);
    }

    public int getId() {
        return id;
    }

    public Boolean isCircuit() {
        return circuit;
    }

    public void setCircuit(boolean circuit) {
        this.circuit = circuit;
    }

    public int getFindCpOvershot() {
        return findCpOvershot;
    }

    public void setFindCpOvershot(int findCpOvershot) {
        this.findCpOvershot = findCpOvershot;
    }

    public int getTourLengthSafetyMargin() {
        return tourLengthSafetyMargin;
    }

    public void setTourLengthSafetyMargin(int tourLengthSafetyMargin) {
        this.tourLengthSafetyMargin = tourLengthSafetyMargin;
    }

    public boolean isSet() {
        return set;
    }

    @JsonProperty("quitmodes")
    public QuitModes getQuitModes() {
        if (quitmodes == null)
            quitmodes = new QuitModes(false, false, false, false);
        return quitmodes;
    }

    @JsonProperty("quitmodes")
    public void setQuitModes(QuitModes quitmodes) {
        this.quitmodes = quitmodes;
    }

    public void save() {
        if (file != null) {
            if (file.exists())
                file.delete();
        } else
            file = getFile(id);
        try {
            new ObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false).writeValue(file, this);
        } catch (final IOException ioe) {
            LOGGER.severe(ioe.getMessage());
        }
    }

    @Override
    public String toString() {
        return "id:" + id + "/circuit:" + circuit;
    }

    public static class QuitModes {

        private boolean noCps;
        private boolean cps;
        private boolean formula1;
        private boolean free;

        public QuitModes() {
        }

        public QuitModes(boolean noCps, boolean cps, boolean formula1, boolean free) {
            this.noCps = noCps;
            this.cps = cps;
            this.formula1 = formula1;
            this.free = free;
        }

        public boolean noCps() {
            return noCps;
        }

        public boolean cps() {
            return cps;
        }

        public boolean formula1() {
            return formula1;
        }

        public boolean free() {
            return free;
        }

        public void setNoCps(boolean noCps) {
            this.noCps = noCps;
        }

        public void setCps(boolean cps) {
            this.cps = cps;
        }

        public void setFormula1(boolean formula1) {
            this.formula1 = formula1;
        }

        public void setFree(boolean free) {
            this.free = free;
        }
    }

}
