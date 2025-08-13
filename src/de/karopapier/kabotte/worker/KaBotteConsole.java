package de.karopapier.kabotte.worker;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class KaBotteConsole {

    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyMMdd/HHmm");

    private static String getFormattedTime() {
        return dtf.format(LocalDateTime.now());
    }

    private final OutPutLevel level;

    public KaBotteConsole(OutPutLevel level) {
        this.level = level;
    }

    private void write(int gameId, String text, OutPutLevel level) {
        if (level.ordinal() >= this.level.ordinal())
            System.out.println(getFormattedTime() + " [" + gameId + "] " + text);
    }

    public void verbose(int gameId, String text) {
        write(gameId, text, OutPutLevel.VERBOSE);
    }

    public void info(int gameId, String text) {
        write(gameId, text, OutPutLevel.INFO);
    }

    public void warning(int gameId, String text) {
        write(gameId, text, OutPutLevel.WARNING);
    }

    public void error(int gameId, String text) {
        write(gameId, text, OutPutLevel.ERROR);
    }

    public enum OutPutLevel {
        VERBOSE, INFO, WARNING, ERROR,
    }

}
