package de.karopapier.kabotte.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.Collections;
import java.util.List;

public class Blacklist {

    private static final String FILENAME = "blacklist.json";

    private static Blacklist userBlacklist = null;
    private List<String> unfair = Collections.emptyList();
    private List<String> quitter = Collections.emptyList();
    private List<String> inactive = Collections.emptyList();

    public Blacklist() {
    }

    public static Blacklist getInstance() {
        if (userBlacklist == null)
            try {
                final CodeSource codeSource = Blacklist.class.getProtectionDomain().getCodeSource();
                final File jarFile = new File(codeSource.getLocation().toURI().getPath());
                File jarDir = jarFile.getParentFile();
                File blacklistFile = new File(jarDir, FILENAME);
                if (!blacklistFile.exists()) {
                    jarDir = jarDir.getParentFile();
                    blacklistFile = new File(jarDir, FILENAME);
                }
                if (blacklistFile.exists()) {
                    System.out.println("Loading blacklist: " + blacklistFile);
                    userBlacklist = new ObjectMapper().readValue(blacklistFile, Blacklist.class);
                }
            } catch (final IOException | URISyntaxException ioe) {
                System.err.println("Reading blacklist failed with: " + ioe.getMessage());
            }
        return userBlacklist;
    }

    /**
     * Checks if the blacklist file contains this user is internally blacklisted.
     *
     * @return true, if contained in blacklist
     */
    public boolean isBlacklisted(String login) {
        return getInstance().contains(login);
    }

    @SuppressWarnings("unused")
    @JsonProperty("unfair")
    private void setUnfair(List<String> unfair) {
        this.unfair = unfair;
    }

    @SuppressWarnings("unused")
    @JsonProperty("quitter")
    private void setQuitter(List<String> quitter) {
        this.quitter = quitter;
    }

    @SuppressWarnings("unused")
    @JsonProperty("inactive")
    private void setInactive(List<String> inactive) {
        this.inactive = inactive;
    }

    public boolean contains(String login) {
        return unfair.contains(login) || quitter.contains(login) || inactive.contains(login);
    }

}
