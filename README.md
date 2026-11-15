# LocalServer

A small HTTP/1.1 server written with Java Core Libraries only (`java.nio` and
`java.net`). It uses one process, one thread and one selector for all listening
sockets and client connections.

## Requirements

- JDK 21
- Maven 3.9+ (optional: the sources can also be compiled directly with `javac`)

No server framework or runtime dependency is used.

## Build

```sh
mvn package
```

The executable JAR is written to `target/localserver-1.0.0-SNAPSHOT.jar`.

Without Maven:

```sh
mkdir -p target/classes
javac -d target/classes $(find src/main/java -name '*.java')
```

## Run

From the project root:

```sh
java -jar target/localserver-1.0.0-SNAPSHOT.jar --config config/server.json
```

The server always runs in the foreground. This is intentional: a future
`systemd --user` service will supervise the process, so Java does not daemonize
and does not use a PID file.

CLI:

```text
localserver [start] [--config <path>] [--foreground]
```

Only `start` is implemented. `stop` and `status` are recognised by the parser
but rejected until a real service/process integration exists. The old form
`java ... Main config/server.json` is still accepted for compatibility.

## Configuration

The bundled configuration is `config/server.json`. Relative paths inside the
file are resolved against the root-level `baseDir`; `baseDir` itself is resolved
against the directory containing the configuration file. This means the server
does not depend on the JVM working directory.

```json
{
  "baseDir": "..",
  "servers": [
    {
      "host": "127.0.0.1",
      "ports": [8080],
      "routes": [
        { "path": "/", "methods": ["GET"], "root": "www", "index": "index.html" }
      ]
    }
  ]
}
```

- `root`, error-page paths and other filesystem paths are relative to `baseDir`.
- Upload directories, document roots and error pages are configuration values,
  not hardcoded application paths.
- Custom error pages are optional; the built-in pages are used when a configured
  file is absent.
- CGI interpreters are configured per route (for example `".py": "python3"`).

## Repository layout

```text
src/main/java/com/localserver/
├── Main.java                 # entry point
├── cli/                      # command-line parsing
├── runtime/                  # lifecycle and shutdown hook
├── server/                   # sockets, selector, event loop, connection state
├── http/                     # HTTP parsing, responses, framing, error pages
├── routing/                  # route matching and dispatch
├── handlers/                 # static files, uploads, CGI
└── config/                   # JSON/config loading and configuration objects
src/test/java/com/localserver/ # JUnit 5 tests
config/server.json             # default configuration
www/                           # static site and CGI scripts
error_pages/                   # optional custom error pages
packaging/linux/               # rootless packaging templates (systemd not enabled yet)
```

There is no `session` package because the current implementation does not
contain session/cookie management yet. The package will only be added when that
functionality is implemented.

## Rootless deployment preparation

The application:

- never requires root;
- accepts an external configuration path;
- resolves relative paths using `baseDir`;
- keeps binaries, configuration and data separable;
- runs in the foreground;
- closes sockets and the selector on SIGTERM through a JVM shutdown hook.

`packaging/linux/install.sh` is a user-local installation template. It copies
the JAR, static site and error pages under `~/.local/share/localserver`, writes
an installed configuration under `~/.config/localserver`, and does **not**
enable or start a service. `packaging/linux/localserver.service` is a template
for future `systemd --user` integration.

## Tests

```sh
mvn test
```

The tests cover command parsing, HTTP parsing, chunked bodies, multipart bodies,
configuration/path resolution, routing, static files, redirects, traversal
protection and uploads.

## Manual smoke test

```sh
java -jar target/localserver-1.0.0-SNAPSHOT.jar --config config/server.json
curl -i http://127.0.0.1:8080/
curl -i http://127.0.0.1:8080/old
curl -i http://127.0.0.1:8080/nexiste-pas
curl -i http://127.0.0.1:9090/
```
