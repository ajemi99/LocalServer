import java.nio.ByteBuffer;
import java.util.Arrays;

public class ClientConnection {

    static final int INITIAL_SIZE = 8192;       // taille de départ de la "boîte"
    static final int SHRINK_ABOVE = 64 * 1024;  // si la boîte est devenue grosse, on la rétrécit après la requête

    // Petit tampon qui sert UNIQUEMENT à lire le socket (un morceau à la fois)
    ByteBuffer requestBuffer = ByteBuffer.allocate(8192);

    // Réponse en cours d'envoi
    ByteBuffer responseBuffer = ByteBuffer.allocate(0);

    int localPort;

    // ---- La "boîte qui grandit" : tout ce que le client a envoyé pour la requête en cours ----
    byte[] data = new byte[INITIAL_SIZE];
    int length = 0;                  // combien d'octets utiles dans data

    // ---- État de la requête en cours (pour ne pas tout recalculer à chaque morceau) ----
    int headerEnd = -1;              // position de la fin des headers (-1 = pas encore arrivée)
    HttpRequest pending = null;      // requête dont on a déjà lu les headers
    int contentLength = 0;           // taille du body annoncée par le client
    ChunkedDecoder decoder = null;   // non null si le client envoie un body "chunked"

    // Si true : on envoie la réponse puis on ferme la connexion (ex: 413, le body restant n'est pas lu)
    boolean closeAfterWrite = false;

    // Ajoute les octets reçus à la boîte (qui grandit si besoin)
    void append(ByteBuffer src) {
        int n = src.remaining();
        if (length + n > data.length) {
            data = Arrays.copyOf(data, Math.max(length + n, data.length * 2));
        }
        src.get(data, length, n);
        length += n;
    }

    // Prépare la connexion pour la requête suivante (keep-alive)
    void reset() {
        if (data.length > SHRINK_ABOVE) {
            data = new byte[INITIAL_SIZE];      // on rend la mémoire d'un gros upload
        }
        length = 0;
        headerEnd = -1;
        pending = null;
        contentLength = 0;
        decoder = null;
    }
}