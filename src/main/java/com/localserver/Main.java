package com.localserver;

import com.localserver.cli.CommandParser;
import com.localserver.runtime.ServerRuntime;
import com.localserver.runtime.ShutdownHandler;
import java.util.Arrays;

/**
 * Application entry point. It only coordinates CLI parsing and runtime
 * initialization; networking lives in {@code com.localserver.server} and HTTP
 * protocol handling in {@code com.localserver.http}.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // Debug(start)
        System.out.println("entry point:" + Arrays.toString(args));
        // Debug(end)
        
        CommandParser.Command command;
        try {
            command = CommandParser.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            CommandParser.printUsage(System.err);
            return;
        }

        ServerRuntime runtime;
        try {
            runtime = new ServerRuntime(command.configPath());
        } catch (Exception e) {
            System.err.println("Config error (" + command.configPath() + "): " + e.getMessage());
            return;
        }

        ShutdownHandler shutdownHandler = new ShutdownHandler(runtime);
        shutdownHandler.register();

        try {
            runtime.start();
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            shutdownHandler.unregister();
        }
    }
}
