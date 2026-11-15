package com.localserver.cli;

import java.io.PrintStream;
import java.util.Set;

/**
 * Parses command-line arguments. Only {@code start} can currently work; the
 * future {@code stop}/{@code status} commands are recognised but rejected until
 * a real process/service integration exists.
 */
public final class CommandParser {

    public static final String DEFAULT_CONFIG = "config/server.json";

    private static final Set<String> COMMANDS = Set.of("start", "stop", "status");

    private CommandParser() {
    }

    public record Command(String name, String configPath, boolean foreground) {
    }

    public static Command parse(String[] args) {
        String command = "start";
        String configPath = DEFAULT_CONFIG;
        boolean configSet = false;
        boolean foreground = false;
        int index = 0;

        if (args.length > 0 && !args[0].startsWith("-") && COMMANDS.contains(args[0])) {
            command = args[0];
            index = 1;
        }

        for (; index < args.length; index++) {
            String arg = args[index];

            switch (arg) {
                case "--config" -> {
                    if (index + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value after --config");
                    }
                    configPath = args[++index];
                    configSet = true;
                }
                case "--foreground" -> foreground = true;
                default -> {
                    if (arg.startsWith("--config=")) {
                        configPath = arg.substring("--config=".length());
                        if (configPath.isEmpty()) {
                            throw new IllegalArgumentException("Missing value after --config");
                        }
                        configSet = true;
                    } else if (!arg.startsWith("-") && !COMMANDS.contains(arg) && !configSet) {
                        // Backward compatibility: the old CLI took the config path as first argument.
                        configPath = arg;
                        configSet = true;
                    } else {
                        throw new IllegalArgumentException("Unknown argument: " + arg);
                    }
                }
            }
        }

        if (!"start".equals(command)) {
            throw new IllegalArgumentException(
                    "Command '" + command + "' is not implemented yet. Only 'start' is available.");
        }

        return new Command(command, configPath, foreground);
    }

    public static void printUsage(PrintStream out) {
        out.println("Usage: localserver [start] [--config <path>] [--foreground]");
        out.println();
        out.println("  start                 Start the server (default command)");
        out.println("  --config <path>       Configuration file (default: " + DEFAULT_CONFIG + ")");
        out.println("  --foreground          Run in the foreground (current and only mode)");
    }
}
