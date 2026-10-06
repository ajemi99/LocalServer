import sys
import os
from email.parser import BytesFeedParser
from email.policy import HTTP

# Forcer l'encodage de sortie UTF-8 sous Windows
sys.stdout.reconfigure(encoding='utf-8')

# En-tête HTTP
print("Content-Type: text/html; charset=UTF-8\n")

print("<html><body>")
print("<h1>Résultat de l'Upload</h1>")

try:
    content_type = os.environ.get('CONTENT_TYPE', '')
    content_length = int(os.environ.get('CONTENT_LENGTH', '0'))

    if not content_type or content_length == 0:
        print("<p style='color: red;'>Requête invalide ou vide.</p>")
    else:
        # Lecture binaire du corps depuis stdin
        raw_body = sys.stdin.buffer.read(content_length)

        # Reconstitution de la structure multipart
        header_data = f"Content-Type: {content_type}\r\n\r\n".encode('latin-1')
        parser = BytesFeedParser(policy=HTTP)
        parser.feed(header_data + raw_body)
        msg = parser.close()

        uploaded = False
        upload_dir = os.path.abspath(os.path.join("www", "uploads"))
        os.makedirs(upload_dir, exist_ok=True)

        for part in msg.walk():
            filename = part.get_filename()
            if filename:
                payload = part.get_payload(decode=True)
                clean_filename = os.path.basename(filename)
                target_path = os.path.join(upload_dir, clean_filename)

                with open(target_path, "wb") as f:
                    f.write(payload)

                print(f"<p style='color: green;'>Fichier <strong>{clean_filename}</strong> uploadé avec succès !</p>")
                print(f"<p>Taille : {len(payload)} octets</p>")
                print(f"<p>Emplacement : <code>{target_path}</code></p>")
                uploaded = True

        if not uploaded:
            print("<p style='color: red;'>Aucun fichier trouvé dans la requête.</p>")

except Exception as e:
    print(f"<p style='color: red;'>Erreur lors du traitement : {e}</p>")

print("<br><a href='/upload_form.html'>Retour au formulaire</a>")
print("</body></html>")