package com.localserver.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @Test
    void loadsBundledConfigurationAndResolvesPathsFromBaseDir() throws Exception {
        List<ServerConfig> servers = ConfigLoader.load("config/server.json");

        assertEquals(2, servers.size());

        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        assertEquals(projectRoot, servers.get(0).baseDir);
        assertEquals(projectRoot, servers.get(1).baseDir);

        assertTrue(Files.isDirectory(servers.get(0).resolvePath("www")));
        assertTrue(Files.isRegularFile(servers.get(0).resolvePath("error_pages/404.html")));
    }

    @Test
    void resolvesPathsAgainstExplicitBaseDir() throws Exception {
        Path temp = Files.createTempDirectory("localserver-config");
        Path app = Files.createDirectories(temp.resolve("app"));
        Path conf = Files.createDirectories(temp.resolve("conf"));
        Path config = conf.resolve("server.json");
        Files.writeString(config, """
                {
                  "baseDir": "../app",
                  "servers": [
                    { "host": "127.0.0.1", "ports": [8080],
                      "routes": [{ "path": "/", "root": "www" }] }
                  ]
                }
                """);

        List<ServerConfig> servers = ConfigLoader.load(config);

        assertEquals(app, servers.get(0).baseDir);
        assertEquals(app.resolve("www"), servers.get(0).resolvePath("www"));
    }
}
