#!/usr/bin/env sh
#
# Rootless, user-local installation template for LocalServer.
#
# This script does NOT use sudo, does NOT create a PID file and does NOT
# enable/start a systemd service. It only lays out files under XDG directories
# so a future systemd --user unit can supervise the foreground process.
#
# Usage:
#   ./packaging/linux/install.sh [path-to-localserver.jar]
#
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)

JAR=${1:-"$REPO_ROOT/target/localserver-1.0.0-SNAPSHOT.jar"}
DATA_HOME=${XDG_DATA_HOME:-"$HOME/.local/share"}
CONFIG_HOME=${XDG_CONFIG_HOME:-"$HOME/.config"}

APP_DIR="$DATA_HOME/localserver"
CONF_DIR="$CONFIG_HOME/localserver"

if [ ! -f "$JAR" ]; then
    echo "JAR not found: $JAR" >&2
    echo "Build it first with: mvn package" >&2
    exit 1
fi

mkdir -p "$APP_DIR" "$CONF_DIR"

cp "$JAR" "$APP_DIR/localserver.jar"
cp -R "$REPO_ROOT/www" "$APP_DIR/"
cp -R "$REPO_ROOT/error_pages" "$APP_DIR/"

# The bundled config uses "baseDir": "..", which is correct when the config
# lives inside the project. For a user installation the data directory is
# explicit, so rewrite baseDir to the installed application/data directory.
sed "s#\"baseDir\"[[:space:]]*:[[:space:]]*\"\\.\\.\"#\"baseDir\": \"$APP_DIR\"#" \
    "$REPO_ROOT/config/server.json" > "$CONF_DIR/server.json"

echo "Installed application files in: $APP_DIR"
echo "Installed configuration in:     $CONF_DIR/server.json"
echo
echo "Systemd user-service integration is prepared but not enabled yet."
echo "Template: $REPO_ROOT/packaging/linux/localserver.service"
echo "A future installer will copy it to $CONFIG_HOME/systemd/user/ and enable it."
