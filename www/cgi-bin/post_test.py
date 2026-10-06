import sys
import os
import urllib.parse

# Forcer la sortie en UTF-8 sous Windows
sys.stdout.reconfigure(encoding='utf-8')

print("Content-Type: text/html; charset=UTF-8\n")

content_length = os.environ.get('CONTENT_LENGTH')
request_method = os.environ.get('REQUEST_METHOD', 'UNKNOWN')

body_data = ""
parsed_data = {}

if request_method == 'POST' and content_length:
    length = int(content_length)
    body_data = sys.stdin.read(length)
    parsed_data = urllib.parse.parse_qs(body_data)

print("<html><body>")
print("<h1>Test CGI POST</h1>")
print(f"<p><strong>Méthode :</strong> {request_method}</p>")
print(f"<p><strong>Données brutes reçues (stdin) :</strong> {body_data}</p>")

if parsed_data:
    print("<h3>Données décodées :</h3><ul>")
    for key, values in parsed_data.items():
        print(f"<li><strong>{key} :</strong> {', '.join(values)}</li>")
    print("</ul>")

print("</body></html>")