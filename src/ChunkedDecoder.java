import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Décode un body "Transfer-Encoding: chunked".
 *
 * Format reçu :
 *     5\r\n            <- taille du morceau (en hexadécimal)
 *     hello\r\n        <- les données
 *     6\r\n
 *     world!\r\n
 *     0\r\n            <- taille 0 = c'est fini
 *     \r\n
 *
 * Le décodeur est INCRÉMENTAL : on l'appelle chaque fois que de nouveaux octets arrivent,
 * et il reprend exactement là où il s'était arrêté (même si le morceau est coupé en plein milieu
 * d'une ligne de taille, d'un \r\n ou des données).
 */
public class ChunkedDecoder {

    public enum Status { INCOMPLETE, COMPLETE, BAD, TOO_LARGE }

    private enum State { SIZE_LINE, DATA, DATA_CRLF, TRAILERS, DONE }

    private static final int MAX_LINE = 1024;          // taille max d'une ligne de taille / de trailer
    private static final int MAX_TRAILERS = 8 * 1024;  // taille max de tous les trailers ensemble

    private State state = State.SIZE_LINE;
    private int pos;                                   // prochain octet à lire dans le tableau
    private long remaining;                            // octets qu'il reste à lire dans le morceau en cours
    private int trailerBytes = 0;
    private final long maxBody;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    // start = index où commence le body dans le tableau (juste après les headers)
    public ChunkedDecoder(int start, long maxBody) {
        this.pos = start;
        this.maxBody = maxBody;
    }

    public byte[] body() {
        return out.toByteArray();
    }

    // data[0..len) = tout ce qui est arrivé jusqu'ici
    public Status feed(byte[] data, int len) {

        while (true) {
            switch (state) {

                case SIZE_LINE: {
                    int eol = findCrlf(data, pos, len);
                    if (eol == -1) {
                        // ligne pas finie : si elle est déjà trop longue, c'est une erreur
                        return (len - pos > MAX_LINE) ? Status.BAD : Status.INCOMPLETE;
                    }
                    if (eol - pos > MAX_LINE) return Status.BAD;

                    String line = new String(data, pos, eol - pos, StandardCharsets.ISO_8859_1);
                    int semi = line.indexOf(';');                 // extension de morceau ("5;name=val") : ignorée
                    if (semi != -1) line = line.substring(0, semi);
                    line = line.trim();

                    long size = parseHex(line);
                    if (size < 0) return Status.BAD;

                    pos = eol + 2;

                    if (size == 0) {
                        state = State.TRAILERS;
                    } else {
                        if (out.size() + size > maxBody) return Status.TOO_LARGE;
                        remaining = size;
                        state = State.DATA;
                    }
                    break;
                }

                case DATA: {
                    int available = len - pos;
                    int take = (int) Math.min(available, remaining);
                    out.write(data, pos, take);
                    pos += take;
                    remaining -= take;
                    if (remaining > 0) return Status.INCOMPLETE;
                    state = State.DATA_CRLF;
                    break;
                }

                case DATA_CRLF: {
                    if (len - pos < 2) return Status.INCOMPLETE;
                    if (data[pos] != '\r' || data[pos + 1] != '\n') return Status.BAD;
                    pos += 2;
                    state = State.SIZE_LINE;
                    break;
                }

                case TRAILERS: {
                    // Après le morceau "0" : des lignes optionnelles, puis une ligne vide
                    int eol = findCrlf(data, pos, len);
                    if (eol == -1) {
                        return (len - pos > MAX_LINE) ? Status.BAD : Status.INCOMPLETE;
                    }
                    if (eol == pos) {              // ligne vide : fin du body
                        pos += 2;
                        state = State.DONE;
                        return Status.COMPLETE;
                    }
                    trailerBytes += (eol + 2 - pos);
                    if (trailerBytes > MAX_TRAILERS) return Status.BAD;
                    pos = eol + 2;                 // on ignore le contenu du trailer
                    break;
                }

                case DONE:
                    return Status.COMPLETE;
            }
        }
    }

    // Cherche "\r\n" entre from (inclus) et len (exclu). Retourne l'index du \r, ou -1.
    private static int findCrlf(byte[] d, int from, int len) {
        for (int i = from; i + 1 < len; i++) {
            if (d[i] == '\r' && d[i + 1] == '\n') return i;
        }
        return -1;
    }

    // "1A" -> 26. Retourne -1 si la ligne n'est pas un nombre hexadécimal valide.
    private static long parseHex(String s) {
        if (s.isEmpty() || s.length() > 15) return -1;    // 15 chiffres max : pas de dépassement possible
        long v = 0;
        for (int i = 0; i < s.length(); i++) {
            int digit = Character.digit(s.charAt(i), 16);
            if (digit < 0) return -1;                      // pas de signe "-" ni de "+", pas de lettre hors a-f
            v = v * 16 + digit;
        }
        return v;
    }
}