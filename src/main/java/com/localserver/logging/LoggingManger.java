package com.localserver.logging;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Facade over {@link java.util.logging} for LocalServer.
 */
public final class LoggingManger {

    private LoggingManger() {
    }

    public static final Logger LOGGER = Logger.getLogger("com.localserver");

    public static boolean initialized = false;

    public static void initialize() throws IOException {
        if (initialized) {
            return;
        }
        LoggingConfig.configure();
        initialized = true;
        LOGGER.info("LocalServer logging initialized");
    }

    public static Logger logger() {
        return LOGGER;
    }

    public static void log(Level level, String msg) {
        LOGGER.log(level, msg);
    }

    public static void log(Level level, String msg, Throwable thrown) {
        LOGGER.log(level, msg, thrown);
    }

    public static void info(String msg) {
        LOGGER.info(msg);
    }

    public static void info(String msg, Throwable thrown) {
        LOGGER.log(Level.INFO, msg, thrown);
    }

    public static void warning(String msg) {
        LOGGER.warning(msg);
    }

    public static void warning(String msg, Throwable thrown) {
        LOGGER.log(Level.WARNING, msg, thrown);
    }

    public static void severe(String msg) {
        LOGGER.severe(msg);
    }

    public static void severe(String msg, Throwable thrown) {
        LOGGER.log(Level.SEVERE, msg, thrown);
    }

    public static void config(String msg) {
        LOGGER.config(msg);
    }

    public static void fine(String msg) {
        LOGGER.fine(msg);
    }

    public static void finer(String msg) {
        LOGGER.finer(msg);
    }

    public static void finest(String msg) {
        LOGGER.finest(msg);
    }

    public static void shutdown() {
        for (Handler handler : LOGGER.getHandlers()) {
            handler.flush();
            handler.close();
            LOGGER.removeHandler(handler);
        }
    }
}