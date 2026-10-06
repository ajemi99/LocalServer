import os

print("Content-Type: text/html; charset=UTF-8\n")
print("<html><body>")
print("<h1>Hello from Python CGI!</h1>")
print(f"<p>Request Method: {os.environ.get('REQUEST_METHOD')}</p>")
print(f"<p>Query String: {os.environ.get('QUERY_STRING')}</p>")
print("</body></html>")