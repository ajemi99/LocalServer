import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parseur JSON minimal.
 * Kay-rje3:
 *   object  -> Map<String,Object>
 *   array   -> List<Object>
 *   string  -> String
 *   number  -> Long (bla point) wla Double
 *   true/false -> Boolean
 *   null    -> null
 */
public class JsonParser {

    private final String s;
    private int pos = 0;

    private JsonParser(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        JsonParser p = new JsonParser(text);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (p.pos != p.s.length()) {
            throw p.error("Unexpected content after JSON value");
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
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't': expectWord("true");  return Boolean.TRUE;
            case 'f': expectWord("false"); return Boolean.FALSE;
            case 'n': expectWord("null");  return null;
            default:  return readNumber();
        }
    }

    private Map<String, Object> readObject() {
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
            map.put(key, readValue());
            skipWhitespace();

            char c = next();
            if (c == ',') continue;
            if (c == '}') return map;
            throw error("Expected ',' or '}'");
        }
    }

    private List<Object> readArray() {
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
            if (c == ',') continue;
            if (c == ']') return list;
            throw error("Expected ',' or ']'");
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
                if (pos >= s.length()) throw error("Bad escape");
                char e = s.charAt(pos++);
                switch (e) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'n':  sb.append('\n'); break;
                    case 't':  sb.append('\t'); break;
                    case 'r':  sb.append('\r'); break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'u':
                        if (pos + 4 > s.length()) throw error("Bad unicode escape");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("Bad unicode escape");
                        }
                        pos += 4;
                        break;
                    default:
                        throw error("Unknown escape \\" + e);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        while (pos < s.length() && "-+0123456789.eE".indexOf(s.charAt(pos)) != -1) {
            pos++;
        }
        String num = s.substring(start, pos);
        if (num.isEmpty()) {
            throw error("Unexpected character '" + s.charAt(pos) + "'");
        }
        try {
            if (num.contains(".") || num.contains("e") || num.contains("E")) {
                return Double.parseDouble(num);
            }
            return Long.parseLong(num);
        } catch (NumberFormatException e) {
            pos = start;
            throw error("Invalid number '" + num + "'");
        }
    }

    // ---------- outils ----------

    private void skipWhitespace() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= s.length()) throw error("Unexpected end of JSON");
        return s.charAt(pos);
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

    // Message b numéro dyal ligne bach ykon sahl t-lqa l'ghalta f config.json
    private IllegalArgumentException error(String msg) {
        int line = 1, col = 1;
        for (int i = 0; i < pos && i < s.length(); i++) {
            if (s.charAt(i) == '\n') { line++; col = 1; } else { col++; }
        }
        return new IllegalArgumentException(
                "JSON error at line " + line + ", column " + col + ": " + msg);
    }
}