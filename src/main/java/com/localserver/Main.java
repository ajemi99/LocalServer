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

        try {
            // parse the command line args
            CommandParser.Command command = CommandParser.parse(args);

            ServerRuntime runtime = new ServerRuntime(command.configPath());

            // setting up the server
            ShutdownHandler shutdownHandler = new ShutdownHandler(runtime);
            shutdownHandler.register();

            // running the server
            try {
                runtime.start();
            } finally {
                shutdownHandler.unregister();
            }

        } catch (Exception e) {
            System.err.println("LocalServer encountered an error:");
            e.printStackTrace();
        }
    }
}
