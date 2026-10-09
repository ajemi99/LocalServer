import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lit un body "multipart/form-data" (ce qu'envoie un formulaire HTML avec un champ fichier).
 *
 * Exemple de body (boundary = "B") :
 *
 *     --B\r\n
 *     Content-Disposition: form-data; name="f"; filename="photo.png"\r\n
 *     Content-Type: image/png\r\n
 *     \r\n
 *     (octets du fichier)\r\n
 *     --B\r\n
 *     Content-Disposition: form-data; name="titre"\r\n
 *     \r\n
 *     Mes vacances\r\n
 *     --B--\r\n
 *
 * Tout se fait sur des OCTETS : le contenu d'un fichier n'est jamais converti en texte.
 */
public class MultipartParser {

    public static class Part {
        public String name;          // nom du champ du formulaire
        public String filename;      // nom du fichier envoyé (null si ce n'est pas un fichier)
        public String contentType;   // type du fichier (peut être null)
        public byte[] data;          // contenu, octet pour octet
    }

    private static final int MAX_PARTS = 100;
    private static final int MAX_PART_HEADERS = 8 * 1024;
    private static final byte[] CRLF = { '\r', '\n' };
    private static final byte[] CRLFCRLF = { '\r', '\n', '\r', '\n' };

    // "multipart/form-data; boundary=----abc" -> "----abc"   (null si absent ou invalide)
    public static String boundaryOf(String contentType) {
        if (contentType == null) return null;
        String lower = contentType.toLowerCase(Locale.ROOT);
        int i = lower.indexOf("boundary=");
        if (i == -1) return null;

        String v = contentType.substring(i + "boundary=".length()).trim();
        if (v.startsWith("\"")) {
            int end = v.indexOf('"', 1);
            if (end == -1) return null;
            v = v.substring(1, end);
        } else {
            int semi = v.indexOf(';');
            if (semi != -1) v = v.substring(0, semi);
            v = v.trim();
        }
        if (v.isEmpty() || v.length() > 70 || v.indexOf('\r') != -1 || v.indexOf('\n') != -1) return null;
        return v;
    }

    public static List<Part> parse(byte[] body, String boundary) {

        byte[] delim = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        byte[] nextDelim = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        List<Part> parts = new ArrayList<>();

        // Début : première ligne "--boundary" (un éventuel texte avant elle est ignoré)
        int pos = indexOf(body, delim, 0);
        if (pos == -1) throw new BadRequestException("Multipart boundary not found");

        while (true) {
            pos += delim.length;                       // on saute "--boundary"

            // "--" juste après = délimiteur de FIN
            if (pos + 2 <= body.length && body[pos] == '-' && body[pos + 1] == '-') {
                break;
            }

            // espaces éventuels, puis la fin de ligne
            while (pos < body.length && (body[pos] == ' ' || body[pos] == '\t')) pos++;
            if (!startsWith(body, CRLF, pos)) throw new BadRequestException("Malformed multipart");
            pos += 2;

            // Les headers de cette partie : jusqu'à la ligne vide
            String headerText;
            int dataStart;
            if (startsWith(body, CRLF, pos)) {         // partie sans aucun header
                headerText = "";
                dataStart = pos + 2;
            } else {
                int hEnd = indexOf(body, CRLFCRLF, pos);
                if (hEnd == -1 || hEnd - pos > MAX_PART_HEADERS) {
                    throw new BadRequestException("Malformed multipart headers");
                }
                headerText = new String(body, pos, hEnd - pos, StandardCharsets.UTF_8);
                dataStart = hEnd + 4;
            }

            // Les données : jusqu'au prochain "\r\n--boundary"
            int dataEnd = indexOf(body, nextDelim, dataStart);
            if (dataEnd == -1) throw new BadRequestException("Multipart closing boundary missing");

            Part part = new Part();
            readHeaders(headerText, part);
            part.data = java.util.Arrays.copyOfRange(body, dataStart, dataEnd);
            parts.add(part);
            if (parts.size() > MAX_PARTS) throw new BadRequestException("Too many multipart parts");

            pos = dataEnd + 2;                         // on se place sur le "--boundary" suivant
        }

        return parts;
    }

    // ---------- headers d'une partie ----------

    private static void readHeaders(String headerText, Part part) {
        for (String line : headerText.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();

            if (name.equals("content-disposition")) {
                Map<String, String> params = parseParams(value);
                part.name = params.get("name");
                part.filename = params.get("filename");
            } else if (name.equals("content-type")) {
                part.contentType = value;
            }
        }
    }

    // form-data; name="f"; filename="a b;c.txt"  ->  { name=f, filename=a b;c.txt }   (gère les guillemets)
    private static Map<String, String> parseParams(String value) {
        Map<String, String> map = new HashMap<>();
        int i = 0, n = value.length();

        while (i < n) {
            // saute les ';' et les espaces
            while (i < n && (value.charAt(i) == ';' || Character.isWhitespace(value.charAt(i)))) i++;
            int eq = i;
            while (eq < n && value.charAt(eq) != '=' && value.charAt(eq) != ';') eq++;
            if (eq >= n || value.charAt(eq) != '=') {      // un mot sans "=" (ex: "form-data") : on le saute
                i = eq;
                continue;
            }
            String key = value.substring(i, eq).trim().toLowerCase(Locale.ROOT);
            i = eq + 1;

            StringBuilder val = new StringBuilder();
            if (i < n && value.charAt(i) == '"') {
                i++;
                while (i < n && value.charAt(i) != '"') {
                    // On n'échappe que \" et \\ . Tout autre antislash reste tel quel : les vieux navigateurs
                    // envoient des chemins Windows bruts ("C:\Users\pc\photo.png").
                    if (value.charAt(i) == '\\' && i + 1 < n
                            && (value.charAt(i + 1) == '"' || value.charAt(i + 1) == '\\')) {
                        i++;
                    }
                    val.append(value.charAt(i));
                    i++;
                }
                i++;                                                  // guillemet fermant
            } else {
                while (i < n && value.charAt(i) != ';') { val.append(value.charAt(i)); i++; }
            }
            map.put(key, val.toString().trim());
        }
        return map;
    }

    // ---------- recherche d'octets ----------

    private static boolean startsWith(byte[] data, byte[] prefix, int pos) {
        if (pos < 0 || pos + prefix.length > data.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[pos + i] != prefix[i]) return false;
        }
        return true;
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = Math.max(from, 0); i + pattern.length <= data.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}