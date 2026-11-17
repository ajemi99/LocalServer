package com.localserver.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.localserver.constants.Constants;

/**
 * JSON Parser.
 * Returns:
 * object -> Map<String,Object>
 * array -> List<Object>
 * string -> String
 * number -> Long (bla point) wla Double
 * true/false -> Boolean
 * null -> null
 */
public class JsonParser {

    private final String s;
    private int pos = 0;
    private int depth = 0;

    private JsonParser(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Objects.requireNonNull(text, "JSON input cannot be null");
        JsonParser jsonParser = new JsonParser(text);
        jsonParser.skipWhitespace();
        Object value = jsonParser.readValue();
        jsonParser.skipWhitespace();
        if (jsonParser.pos != jsonParser.s.length()) {
            throw jsonParser.error("Unexpected content after JSON value");
        }
        return value;
    }

    // ---------- valeurs ----------

    private Object readValue() {
        if (pos >= s.length()) {
            throw error("Unexpected end of JSON");
        }

        char c = s.charAt(pos);

        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                expectWord("true");
                return Boolean.TRUE;
            case 'f':
                expectWord("false");
                return Boolean.FALSE;
            case 'n':
                expectWord("null");
                return null;
            default:
                return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        enterContainer();
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // '{'
            skipWhitespace();

            if (peek() == '}') {
                pos++;
                return map;
            }

            while (true) {
                skipWhitespace();

                if (peek() != '"') {
                    throw error("Expected string key");
                }

                String key = readString();

                skipWhitespace();
                expectChar(':');
                skipWhitespace();

                if (map.containsKey(key)) {
                    throw error("Duplicate object key '" + key + "'");
                }

                map.put(key, readValue());
                skipWhitespace();

                char c = next();

                if (c == ',') {
                    continue;
                }

                if (c == '}') {
                    return map;
                }

                throw error("Expected ',' or '}'");
            }
        } finally {
            exitContainer();
        }
    }

    private List<Object> readArray() {
        enterContainer();

        try {
            List<Object> list = new ArrayList<>();
            pos++; // '['
            skipWhitespace();

            if (peek() == ']') {
                pos++;
                return list;
            }

            while (true) {
                skipWhitespace();
                list.add(readValue());
                skipWhitespace();

                char c = next();

                if (c == ',') {
                    continue;
                }

                if (c == ']') {
                    return list;
                }

                throw error("Expected ',' or ']'");
            }
        } finally {
            exitContainer();
        }
    }

    private String readString() {
        StringBuilder sb = new StringBuilder();
        pos++; // guillemet ouvrant

        while (true) {
            if (pos >= s.length()) {
                throw error("Unterminated string");
            }
            char c = s.charAt(pos++);

            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= s.length())
                    throw error("Bad escape");
                char e = s.charAt(pos++);
                switch (e) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'u':
                        char unicode = readUnicodeEscape();

                        if (Character.isHighSurrogate(unicode)) {
                            // A high surrogate must be followed by another \\uXXXX escape.
                            if (pos + 2 > s.length()
                                    || s.charAt(pos) != '\\'
                                    || s.charAt(pos + 1) != 'u') {
                                throw error("High surrogate must be followed by a low surrogate");
                            }

                            pos += 2;
                            char lowSurrogate = readUnicodeEscape();

                            if (!Character.isLowSurrogate(lowSurrogate)) {
                                throw error("Expected low surrogate after high surrogate");
                            }

                            sb.append(unicode);
                            sb.append(lowSurrogate);

                        } else if (Character.isLowSurrogate(unicode)) {
                            throw error("Unexpected low surrogate");

                        } else {
                            sb.append(unicode);
                        }

                        break;
                    default:
                        throw error("Unknown escape \\" + e);
                }
            } else {
                if (c <= 0x1F) {
                    throw error("Unescaped control character in string");
                }
                sb.append(c);
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        // start with minus sign
        if (peek('-')) {
            pos++;
        }
        // integer part
        if (peek('0')) {
            pos++;

            // Json doesnt allow leading Zeros
            if (pos < s.length() && isDigit(s.charAt(pos))) {
                throw error("Leading zeros are not allowed");
            }

        } else if (pos < s.length() && isDigitOneToNine(s.charAt(pos))) {
            while (pos < s.length() && isDigit(s.charAt(pos))) {
                pos++;
            }
        } else {
            throw error("Invalid integer part");
        }

        if (peek('.')) {
            if (pos >= s.length() || !isDigit(s.charAt(pos))) {
                throw error("Expected digit after decimal point found something else");
            }
            while (pos < s.length() && isDigit(s.charAt(pos))) {
                pos++;
            }
        }

        // Optional exponent.
        if (peek('e') || peek('E')) {
            pos++;

            if (peek('+') || peek('-')) {
                pos++;
            }

            if (pos >= s.length() || !isDigit(s.charAt(pos))) {
                throw error("Expected exponent digits");
            }

            while (pos < s.length() && isDigit(s.charAt(pos))) {
                pos++;
            }
        }

        String num = s.substring(start, pos);

        try {
            if (num.contains(".") || num.contains("e") || num.contains("E")) {
                double value = Double.parseDouble(num);

                if (!Double.isFinite(value)) {
                    throw error("Number out of supported range: '" + num + "'");
                }

                return value;
            }
            return Long.parseLong(num);
        } catch (NumberFormatException e) {
            throw error("Number out of supported range: '" + num + "'");
        }
    }

    // ---------- helpers ----------

    private void enterContainer() {
        if (depth >= Constants.Config.MAX_NESSTING_DEPTH) {
            throw error("Maximum nesting depth exceeded");
        }

        depth++;
    }

    private void exitContainer() {
        depth--;
    }

    private void skipWhitespace() {
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private char peek() {
        if (pos >= s.length())
            throw error("Unexpected end of JSON");
        return s.charAt(pos);
    }

    private boolean peek(char expected) {
        return pos < s.length() && s.charAt(pos) == expected;
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expectChar(char expected) {
        if (next() != expected) {
            pos--;
            throw error("Expected '" + expected + "'");
        }
    }

    private void expectWord(String word) {
        if (!s.startsWith(word, pos)) {
            throw error("Expected '" + word + "'");
        }
        pos += word.length();
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isDigitOneToNine(char c) {
        return c >= '1' && c <= '9';
    }

    private char readUnicodeEscape() {
        if (pos + 4 > s.length()) {
            throw error("Incomplete Unicode escape");
        }

        String hex = s.substring(pos, pos + 4);

        try {
            char value = (char) Integer.parseInt(hex, 16);
            pos += 4;
            return value;
        } catch (NumberFormatException e) {
            throw error("Invalid Unicode escape: " + hex);
        }
    }

    // Message with excat place when does the error occur in the server.json
    private IllegalArgumentException error(String msg) {
        int line = 1, col = 1;
        for (int i = 0; i < pos && i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new IllegalArgumentException(
                "JSON error at line " + line + ", column " + col + ": " + msg);
    }
}