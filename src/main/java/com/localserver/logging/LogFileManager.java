package com.localserver.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Manages log file paths and directories for the application.
 * This utility class handles creation of the log directory and generation
 * of timestamped log file names for each run.
 */
public final class LogFileManager {

    private static final Path LOG_DIR = Paths.get("logs").toAbsolutePath().normalize();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private LogFileManager() {
    }

    /**
     * Returns the absolute path to the log directory.
     *
     * @return the log directory path (absolute, normalized)
     */
    public static Path logDirectory() {
        return LOG_DIR;
    }

    /**
     * Creates the log directory if it does not exist and returns a path for a new
     * run log file named with the current timestamp.
     *
     * @return the path to the new log file (directory is guaranteed to exist)
     * @throws IOException if the directory cannot be created
     */
    public static Path createRunLogFile() throws IOException {
        Files.createDirectories(LOG_DIR);
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        return LOG_DIR.resolve(timestamp + ".logs");
    }
}