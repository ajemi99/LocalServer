package com.localserver;

import com.localserver.cli.CommandParser;
import com.localserver.logging.LoggingManger;
import com.localserver.runtime.ServerRuntime;
import java.io.IOException;

/**
 * Application entry point. It only coordinates CLI parsing and runtime
 * initialization; networking lives in {@code com.localserver.server} and HTTP
 * protocol handling in {@code com.localserver.http}.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            // initialize logging
            LoggingManger.initialize();
            LoggingManger.info("LocalServer starting");
        } catch (IOException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
            return;
        }

        try {
            // parse the command line args
            CommandParser.Command command = CommandParser.parse(args);

            ServerRuntime runtime = new ServerRuntime(command.configPath());

            // running the server
            runtime.start();

        } catch (Exception e) {
            LoggingManger.severe("LocalServer encountered an error", e);
        } finally {
            LoggingManger.shutdown();
        }
    }
}