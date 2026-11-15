package com.localserver.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandParserTest {

    @Test
    void defaultsToStartAndDefaultConfig() {
        CommandParser.Command command = CommandParser.parse(new String[0]);

        assertEquals("start", command.name());
        assertEquals(CommandParser.DEFAULT_CONFIG, command.configPath());
        assertFalse(command.foreground());
    }

    @Test
    void parsesConfigAndForegroundFlags() {
        CommandParser.Command command = CommandParser.parse(new String[]{
                "start", "--config", "/tmp/server.json", "--foreground"
        });

        assertEquals("start", command.name());
        assertEquals("/tmp/server.json", command.configPath());
        assertTrue(command.foreground());
    }

    @Test
    void keepsLegacyConfigPathAsFirstArgument() {
        CommandParser.Command command = CommandParser.parse(new String[]{"config/server.json"});

        assertEquals("start", command.name());
        assertEquals("config/server.json", command.configPath());
    }

    @Test
    void rejectsFutureCommandsInsteadOfPretendingTheyWork() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CommandParser.parse(new String[]{"stop"}));

        assertTrue(error.getMessage().contains("not implemented"));
    }

    @Test
    void rejectsMissingConfigValue() {
        assertThrows(IllegalArgumentException.class,
                () -> CommandParser.parse(new String[]{"--config"}));
    }
}
