package com.localserver.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderValidationTest {

    @TempDir
    Path tempDir;

    private int counter = 0;

    private List<ServerConfig> load(String serversJson) throws IOException {
        String json = "{\"servers\":[" + serversJson + "]}";
        Path file = tempDir.resolve("config-" + counter++ + ".json");
        Files.writeString(file, json);
        return ConfigLoader.load(file);
    }

    private static String server(String body) {
        return "{" + body + "}";
    }

    private static String route(String body) {
        return "{" + body + "}";
    }

    private IllegalArgumentException loadFails(String serversJson) throws IOException {
        return assertThrows(IllegalArgumentException.class, () -> load(serversJson));
    }

    // ------------------------------------------------------------------
    // Listening endpoints
    // ------------------------------------------------------------------

    @Test
    void acceptsDifferentHostnamesOnSameAddressAndPort() throws IOException {
        String json = server("\"bindAddress\":\"127.0.0.1\","
                + "\"ports\":[8080],"
                + "\"serverNames\":[\"one.test\",\"two.test\"]");

        List<ServerConfig> servers = load(json);

        assertEquals(List.of("one.test", "two.test"), servers.get(0).serverNames);
    }

    @Test
    void acceptsMultipleVirtualServersSharingEndpointWithOneDefault() throws IOException {
        String json = server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],"
                        + "\"serverNames\":[\"one.test\"],\"default\":false")
                + "," + server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],"
                        + "\"serverNames\":[\"two.test\"],\"default\":true");

        List<ServerConfig> servers = load(json);

        assertEquals(2, servers.size());
    }

    @Test
    void rejectsDuplicateHostnameWithinServer() throws IOException {
        String json = server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],"
                + "\"serverNames\":[\"one.test\",\"ONE.TEST\"]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("duplicate name"), ex.getMessage());
    }

    @Test
    void rejectsDuplicateHostnameAcrossServersSharingEndpoint() throws IOException {
        String json = server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],"
                        + "\"serverNames\":[\"shared.test\"],\"default\":false")
                + "," + server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],"
                        + "\"serverNames\":[\"shared.test\"],\"default\":true");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("duplicate server name"), ex.getMessage());
    }

    @Test
    void rejectsTwoDefaultServersOnSameEndpoint() throws IOException {
        String json = server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],"
                        + "\"serverNames\":[\"one.test\"],\"default\":true")
                + "," + server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],"
                        + "\"serverNames\":[\"two.test\"],\"default\":true");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("more than one default server"), ex.getMessage());
    }

    @Test
    void acceptsDifferentAddressesWithSamePortAndDifferentDefaults() throws IOException {
        String json = server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],\"default\":true")
                + "," + server("\"bindAddress\":\"0.0.0.0\",\"ports\":[8080],\"default\":true");

        List<ServerConfig> servers = load(json);

        assertEquals("127.0.0.1", servers.get(0).bindAddress);
        assertEquals("0.0.0.0", servers.get(1).bindAddress);
    }

    @Test
    void rejectsDuplicatePortWithinServer() throws IOException {
        String json = server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080,8080]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("duplicate port"), ex.getMessage());
    }

    @Test
    void rejectsMultipleVirtualServersSharingEndpointWithoutDefault() throws IOException {
        String json = server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],"
                        + "\"serverNames\":[\"one.test\"],\"default\":false")
                + "," + server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],"
                        + "\"serverNames\":[\"two.test\"],\"default\":false");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("requires exactly one default server"), ex.getMessage());
    }

    // ------------------------------------------------------------------
    // Server names
    // ------------------------------------------------------------------

    @Test
    void rejectsNonStringServerName() throws IOException {
        String json = server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],"
                + "\"serverNames\":[\"one.test\",42]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("serverNames[1] must be a string"), ex.getMessage());
    }

    @Test
    void rejectsEmptyServerName() throws IOException {
        String json = server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],"
                + "\"serverNames\":[\"\"]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("must not be empty"), ex.getMessage());
    }

    @Test
    void rejectsServerNamesAndLegacyServerNameTogether() throws IOException {
        String json = server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],"
                + "\"serverNames\":[\"one.test\"],\"serverName\":\"two.test\"");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("must not define both"), ex.getMessage());
    }

    @Test
    void rejectsBindAddressAndLegacyHostTogether() throws IOException {
        String json = server("\"bindAddress\":\"127.0.0.1\",\"host\":\"127.0.0.1\","
                + "\"ports\":[8080]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("must not define both"), ex.getMessage());
    }

    @Test
    void supportsLegacyServerNameField() throws IOException {
        String json = server("\"host\":\"127.0.0.1\",\"ports\":[8080],\"serverName\":\"Legacy.Test\"");

        List<ServerConfig> servers = load(json);

        assertEquals("127.0.0.1", servers.get(0).bindAddress);
        assertEquals(List.of("legacy.test"), servers.get(0).serverNames);
    }

    // ------------------------------------------------------------------
    // Routes: methods
    // ------------------------------------------------------------------

    @Test
    void rejectsEmptyMethodsArray() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/\",\"root\":\"www\",\"methods\":[]") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("servers[0].routes[0].methods must not be empty"), ex.getMessage());
    }

    @Test
    void rejectsDuplicateMethods() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/\",\"root\":\"www\",\"methods\":[\"GET\",\"GET\"]") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("duplicate method"), ex.getMessage());
    }

    @Test
    void rejectsNonStringMethod() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/\",\"root\":\"www\",\"methods\":[\"GET\",1]") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("servers[0].routes[0].methods[1] must be a string"), ex.getMessage());
    }

    @Test
    void rejectsUnsupportedMethod() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/\",\"root\":\"www\",\"methods\":[\"PUT\"]") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("must be one of GET, POST, DELETE"), ex.getMessage());
    }

    @Test
    void defaultsMethodsToGetWhenOmitted() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/\",\"root\":\"www\"") + "]");

        List<ServerConfig> servers = load(json);

        assertEquals(List.of("GET"), servers.get(0).routes.get(0).methods);
    }

    // ------------------------------------------------------------------
    // Routes: paths and files
    // ------------------------------------------------------------------

    @Test
    void rejectsEmptyRoutePath() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"\",\"root\":\"www\"") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("servers[0].routes[0].path"), ex.getMessage());
    }

    @Test
    void rejectsRoutePathWithoutLeadingSlash() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"api\",\"root\":\"www\"") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("must start with '/'"), ex.getMessage());
    }

    @Test
    void rejectsAmbiguousRoutePath() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/a/../b\",\"root\":\"www\"") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("must not contain '.' or '..'"), ex.getMessage());
    }

    @Test
    void rejectsEmptyRoot() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/\",\"root\":\"\"") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains(".root must not be empty"), ex.getMessage());
    }

    @Test
    void rejectsEmptyIndex() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/\",\"root\":\"www\",\"index\":\"\"") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains(".index must not be empty"), ex.getMessage());
    }

    @Test
    void rejectsNonBooleanDirectoryListing() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/\",\"root\":\"www\",\"directoryListing\":\"yes\"") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains(".directoryListing must be true or false"), ex.getMessage());
    }

    @Test
    void acceptsRedirectWithoutRoot() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/old\",\"redirect\":{\"url\":\"/\",\"status\":301}") + "]");

        List<ServerConfig> servers = load(json);
        RouteConfig r = servers.get(0).routes.get(0);

        assertEquals("/", r.redirect.url);
        assertEquals(301, r.redirect.status);
        assertEquals(null, r.root);
    }

    @Test
    void acceptsRootWithoutRedirect() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/\",\"root\":\"www\"") + "]");

        List<ServerConfig> servers = load(json);
        RouteConfig r = servers.get(0).routes.get(0);

        assertEquals("www", r.root);
        assertEquals(null, r.redirect);
    }

    @Test
    void rejectsRootAndRedirectTogether() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/\",\"root\":\"www\",\"redirect\":{\"url\":\"/new\"}") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("not both"), ex.getMessage());
    }

    @Test
    void rejectsRouteWithoutRootOrRedirect() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/\"") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("either 'root' or 'redirect'"), ex.getMessage());
    }

    @Test
    void rejectsDuplicateRoutePaths() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/a\",\"root\":\"www\"")
                + "," + route("\"path\":\"/a/\",\"root\":\"www\"") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("duplicates another route path"), ex.getMessage());
    }

    // ------------------------------------------------------------------
    // Redirects
    // ------------------------------------------------------------------

    @Test
    void acceptsValidRedirectStatus() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/old\",\"redirect\":{\"url\":\"/\",\"status\":308}") + "]");

        List<ServerConfig> servers = load(json);

        assertEquals(308, servers.get(0).routes.get(0).redirect.status);
    }

    @Test
    void rejectsInvalidRedirectStatus() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/old\",\"redirect\":{\"url\":\"/\",\"status\":200}") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("status must be one of"), ex.getMessage());
    }

    @Test
    void rejectsExtremelyLargeRedirectStatusSafely() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/old\",\"redirect\":{\"url\":\"/\",\"status\":9223372036854775807}") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("status must be one of"), ex.getMessage());
    }

    @Test
    void rejectsEmptyRedirectUrl() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/old\",\"redirect\":{\"url\":\"\"}") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("url is required"), ex.getMessage());
    }

    // ------------------------------------------------------------------
    // CGI
    // ------------------------------------------------------------------

    @Test
    void acceptsValidCgiMapping() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/cgi-bin\",\"root\":\"www/cgi-bin\","
                        + "\"cgi\":{\".py\":\"python3\",\".pl\":\"perl\"}") + "]");

        List<ServerConfig> servers = load(json);
        RouteConfig r = servers.get(0).routes.get(0);

        assertEquals("python3", r.cgi.get(".py"));
        assertEquals("perl", r.cgi.get(".pl"));
    }

    @Test
    void rejectsCgiExtensionWithoutDot() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/cgi-bin\",\"root\":\"www/cgi-bin\","
                        + "\"cgi\":{\"py\":\"python3\"}") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("must start with '.'"), ex.getMessage());
    }

    @Test
    void rejectsEmptyCgiExtension() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/cgi-bin\",\"root\":\"www/cgi-bin\","
                        + "\"cgi\":{\"\":\"python3\"}") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("must not be empty"), ex.getMessage());
    }

    @Test
    void rejectsEmptyCgiInterpreter() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/cgi-bin\",\"root\":\"www/cgi-bin\","
                        + "\"cgi\":{\".py\":\"\"}") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains(".cgi['.py'] must not be empty"), ex.getMessage());
    }

    @Test
    void rejectsCgiRouteWithoutRoot() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"routes\":[" + route("\"path\":\"/cgi-bin\","
                        + "\"redirect\":{\"url\":\"/\"},\"cgi\":{\".py\":\"python3\"}") + "]");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains(".cgi requires 'root'"), ex.getMessage());
    }

    // ------------------------------------------------------------------
    // Error pages
    // ------------------------------------------------------------------

    @Test
    void acceptsValidErrorPage() throws IOException {
        String json = server("\"ports\":[8080],"
                + "\"errorPages\":{\"404\":\"error_pages/404.html\"}");

        List<ServerConfig> servers = load(json);

        assertEquals("error_pages/404.html", servers.get(0).errorPages.get(404));
    }

    @Test
    void rejectsNonNumericErrorPageKey() throws IOException {
        String json = server("\"ports\":[8080],\"errorPages\":{\"not-a-code\":\"x.html\"}");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("must be an integer error status code"), ex.getMessage());
    }

    @Test
    void rejectsUnsupportedErrorPageStatus() throws IOException {
        String json = server("\"ports\":[8080],\"errorPages\":{\"418\":\"teapot.html\"}");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("unsupported error status code"), ex.getMessage());
    }

    @Test
    void rejectsNonStringErrorPagePath() throws IOException {
        String json = server("\"ports\":[8080],\"errorPages\":{\"404\":123}");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("servers[0].errorPages.404 must be a string"), ex.getMessage());
    }

    @Test
    void rejectsEmptyErrorPagePath() throws IOException {
        String json = server("\"ports\":[8080],\"errorPages\":{\"404\":\"\"}");

        IllegalArgumentException ex = loadFails(json);
        assertTrue(ex.getMessage().contains("servers[0].errorPages.404 must not be empty"), ex.getMessage());
    }

    // ------------------------------------------------------------------
    // Base directory
    // ------------------------------------------------------------------

    private static final String MINIMAL_SERVERS =
            server("\"bindAddress\":\"127.0.0.1\",\"ports\":[8080],"
                    + "\"routes\":[" + route("\"path\":\"/\",\"root\":\"www\"") + "]");

    private Path loadWithBaseDir(String baseDirJson) throws IOException {
        String json = "{" + (baseDirJson == null ? "" : "\"baseDir\":" + baseDirJson + ",")
                + "\"servers\":[" + MINIMAL_SERVERS + "]}";
        Path file = tempDir.resolve("config-" + counter++ + ".json");
        Files.writeString(file, json);
        return ConfigLoader.load(file).get(0).baseDir;
    }

    @Test
    void usesConfigDirectoryWhenBaseDirOmitted() throws IOException {
        Path baseDir = loadWithBaseDir(null);

        assertEquals(tempDir.toAbsolutePath().normalize(), baseDir);
    }

    @Test
    void resolvesRelativeBaseDirAgainstConfigDirectory() throws IOException {
        Path configured = Files.createDirectories(tempDir.resolve("data"));

        Path baseDir = loadWithBaseDir("\"data\"");

        assertEquals(configured.toAbsolutePath().normalize(), baseDir);
    }

    @Test
    void preservesAbsoluteBaseDir() throws IOException {
        Path configured = Files.createDirectories(tempDir.resolve("absolute-root")).toAbsolutePath().normalize();

        Path baseDir = loadWithBaseDir("\"" + configured.toString().replace("\\", "\\\\") + "\"");

        assertEquals(configured, baseDir);
    }

    @Test
    void rejectsNonexistentBaseDir() throws IOException {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> loadWithBaseDir("\"missing\""));
        assertTrue(ex.getMessage().contains("'baseDir' is not a directory"), ex.getMessage());
    }

    @Test
    void rejectsBaseDirThatIsAFile() throws IOException {
        Path file = Files.writeString(tempDir.resolve("a-file"), "content");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> loadWithBaseDir("\"" + file.getFileName() + "\""));
        assertTrue(ex.getMessage().contains("'baseDir' is not a directory"), ex.getMessage());
    }

    @Test
    void resolvesRelativeResourcePathsAgainstBaseDir() throws IOException {
        Path baseDir = loadWithBaseDir(null);
        ServerConfig server = new ServerConfig();
        server.baseDir = baseDir;

        Path resolved = server.resolvePath("www/index.html");

        assertEquals(baseDir.resolve("www/index.html").normalize(), resolved);
    }
}