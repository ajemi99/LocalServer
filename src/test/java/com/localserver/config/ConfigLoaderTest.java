package com.localserver.config;

import com.localserver.constants.Constants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonParserTest {

    // ---------- Empty objects and arrays ----------

    @Test
    void parsesEmptyObject() {
        Object result = JsonParser.parse("{}");

        assertInstanceOf(Map.class, result);
        assertTrue(((Map<?, ?>) result).isEmpty());
    }

    @Test
    void parsesEmptyArray() {
        Object result = JsonParser.parse("[]");

        assertInstanceOf(List.class, result);
        assertTrue(((List<?>) result).isEmpty());
    }

    // ---------- Nested objects and arrays ----------

    @Test
    void parsesNestedObjectsAndArrays() {
        String json = """
                {
                    "servers": [
                        {
                            "host": "127.0.0.1",
                            "ports": [8080, 8081],
                            "routes": [
                                {
                                    "path": "/api",
                                    "methods": ["GET", "POST"]
                                }
                            ]
                        }
                    ]
                }
                """;

        Map<?, ?> root = (Map<?, ?>) JsonParser.parse(json);

        List<?> servers = (List<?>) root.get("servers");
        Map<?, ?> server = (Map<?, ?>) servers.get(0);

        assertEquals("127.0.0.1", server.get("host"));
        assertEquals(List.of(8080L, 8081L), server.get("ports"));

        List<?> routes = (List<?>) server.get("routes");
        Map<?, ?> route = (Map<?, ?>) routes.get(0);

        assertEquals("/api", route.get("path"));
        assertEquals(List.of("GET", "POST"), route.get("methods"));
    }

    // ---------- Strings and escape sequences ----------

    @Test
    void parsesEscapedQuotesAndBackslashes() {
        String json = "\"Hello \\\"World\\\" \\\\ LocalServer\"";

        Object result = JsonParser.parse(json);

        assertEquals("Hello \"World\" \\ LocalServer", result);
    }

    @Test
    void parsesAllValidEscapeSequences() {
        String json = "\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"";

        String expected = "\"\\/\b\f\n\r\t";

        assertEquals(expected, JsonParser.parse(json));
    }

    @Test
    void rejectsInvalidEscapeSequences() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse("\"\\x\""));
    }

    @Test
    void rejectsUnterminatedStrings() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse("\"Hello"));
    }

    @Test
    void rejectsUnescapedControlCharacters() {
        String json = "\"Hello\nWorld\"";

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse(json));
    }

    // ---------- Numbers ----------

    @Test
    void parsesValidNumbers() {
        assertEquals(123L, JsonParser.parse("123"));
        assertEquals(-123L, JsonParser.parse("-123"));
        assertEquals(0L, JsonParser.parse("0"));
        assertEquals(0L, JsonParser.parse("-0"));

        assertEquals(0.5, JsonParser.parse("0.5"));
        assertEquals(-1.25, JsonParser.parse("-1.25"));
        assertEquals(1.5E-3, JsonParser.parse("1.5e-3"));
        assertEquals(1.0E10, JsonParser.parse("1E10"));
    }

    @Test
    void rejectsInvalidNumbers() {
        String[] invalidNumbers = {
                "+123",
                "01",
                "-01",
                ".5",
                "1.",
                "1.e2",
                "1e",
                "1e+",
                "1e-",
                "--1",
                "-"
        };

        for (String number : invalidNumbers) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonParser.parse(number),
                    "Expected invalid number to be rejected: " + number);
        }
    }

    @Test
    void rejectsNumbersOutsideSupportedRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse("999999999999999999999999999999"));
    }

    // ---------- Whitespace ----------

    @Test
    void acceptsValidJsonWhitespace() {
        String json = " \t\n\r { \"port\" : 8080 } \n";

        Map<?, ?> result = (Map<?, ?>) JsonParser.parse(json);

        assertEquals(8080L, result.get("port"));
    }

    @Test
    void rejectsInvalidJsonWhitespace() {
        String json = "\u00A0{}";

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse(json));
    }

    // ---------- Duplicate object keys ----------

    @Test
    void rejectsDuplicateObjectKeys() {
        String json = """
                {
                    "port": 8080,
                    "port": 9090
                }
                """;

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse(json));
    }

    @Test
    void rejectsDuplicateKeysInNestedObjects() {
        String json = """
                {
                    "server": {
                        "port": 8080,
                        "port": 9090
                    }
                }
                """;

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse(json));
    }

    // ---------- Unicode ----------

    @Test
    void parsesUnicodeEscapes() {
        String json = "\"\\u0041\\u0042\\u0043\"";

        assertEquals("ABC", JsonParser.parse(json));
    }

    @Test
    void parsesSupplementaryUnicodeCharacters() {
        String json = "\"\\uD83D\\uDE00\"";

        String expected = new String(Character.toChars(0x1F600));

        assertEquals(expected, JsonParser.parse(json));
    }

    @Test
    void rejectsUnpairedHighSurrogate() {
        String json = "\"\\uD83D\"";

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse(json));
    }

    @Test
    void rejectsUnpairedLowSurrogate() {
        String json = "\"\\uDE00\"";

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse(json));
    }

    @Test
    void rejectsInvalidUnicodeEscapes() {
        String json = "\"\\uZZZZ\"";

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse(json));
    }

    // ---------- Maximum nesting depth ----------

    @Test
    void acceptsMaximumNestingDepth() {
        int depth = Constants.Config.MAX_NESSTING_DEPTH;

        String json = "[".repeat(depth)
                + "0"
                + "]".repeat(depth);

        assertDoesNotThrow(() -> JsonParser.parse(json));
    }

    @Test
    void rejectsExcessiveNestingDepth() {
        int depth = Constants.Config.MAX_NESSTING_DEPTH + 1;

        String json = "[".repeat(depth)
                + "0"
                + "]".repeat(depth);

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse(json));
    }

    // ---------- Trailing content ----------

    @Test
    void rejectsTrailingContentAfterValidJson() {
        String json = "{\"port\":8080} garbage";

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse(json));
    }

    @Test
    void rejectsMultipleRootValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse("{} {}"));
    }

    // ---------- Trailing commas ----------

    @Test
    void rejectsTrailingCommaInObject() {
        String json = """
                {
                    "port": 8080,
                }
                """;

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse(json));
    }

    @Test
    void rejectsTrailingCommaInArray() {
        String json = "[8080, 8081,]";

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse(json));
    }

    // ---------- Unexpected end of input ----------

    @Test
    void rejectsIncompleteObjects() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse("{\"port\":"));
    }

    @Test
    void rejectsIncompleteArrays() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse("[1, 2"));
    }

    @Test
    void rejectsEmptyInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonParser.parse(""));
    }
}