package com.localserver.logging;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Configuration for the application's {@link Logger} setup.
 *
 * <p>
 * Configures {@link java.util.logging} for the {@code com.localserver}
 * logger: output goes to a per-run log file under {@code logs/} written by a
 * {@link FileHandler} using a {@link SimpleFormatter}, with no propagation to
 * parent handlers so log records are not printed twice.
 * </p>
 *
 * <p>
 * This is a utility class and cannot be instantiated.
 * </p>
 */
public final class LoggingConfig {

    private LoggingConfig() {
    }

    /**
     * Configures java.util.logging for the {@code com.localserver} logger.
     *
     * <p>
     * Disables parent handlers, sets the logger level to {@link Level#ALL},
     * attaches a {@link FileHandler} bound to this run's log file (as returned
     * by {@link LogFileManager#createRunLogFile()}) with a {@link
     * SimpleFormatter}, and logs an initialization message at {@link
     * Level#CONFIG}.
     * </p>
     *
     * @throws IOException if the log file handler cannot be created or opened
     */
    public static void configure() throws IOException {
        Logger logger = Logger.getLogger("com.localserver");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);

        Path logFile = LogFileManager.createRunLogFile();
        FileHandler fileHandler = new FileHandler(logFile.toString(), false);
        fileHandler.setFormatter(new SimpleFormatter());
        fileHandler.setLevel(Level.ALL);
        logger.addHandler(fileHandler);

        logger.log(Level.CONFIG, "Logging initialized, writing to {0}", logFile);
    }
}