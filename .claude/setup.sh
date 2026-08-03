#!/usr/bin/env bash
#
# Wird beim Start einer Claude-Code-Cloud-Session ausgeführt.
# Installiert JDK + Android SDK, damit Gradle-Builds laufen.
#
# Kein Emulator: die Sandbox hat kein KVM. Bauen und JVM-Tests gehen,
# instrumentierte Tests und Maestro nicht. Das ist beabsichtigt.
#
set -euo pipefail

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m  %s\n' "$*" >&2; }

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SUDO="$(command -v sudo || true)"

# ---- hier anpassen, wenn ihr auf eine neuere API hochzieht -------------------
# Seit SDK 37 tragen die Plattform-Pakete Minor-Versionen ("android-37.1").
ANDROID_API="${ANDROID_API:-37.1}"
BUILD_TOOLS="${BUILD_TOOLS:-36.0.0}"
JDK_PKG="${JDK_PKG:-openjdk-21-jdk-headless}"
# Falls der Download 404t: aktuelle Nummer auf
# https://developer.android.com/studio#command-line-tools-only nachsehen.
CMDLINE_TOOLS_ZIP="${CMDLINE_TOOLS_ZIP:-commandlinetools-linux-11076708_latest.zip}"
# -----------------------------------------------------------------------------

# ---------------------------------------------------------------- JDK --------
if command -v javac >/dev/null 2>&1; then
    log "JDK bereits vorhanden: $(javac -version 2>&1)"
else
    log "Installiere $JDK_PKG"
    $SUDO apt-get update -qq
    $SUDO apt-get install -y -qq "$JDK_PKG" unzip curl
fi

JAVA_BIN="$(command -v java)"
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")}"
log "JAVA_HOME=$JAVA_HOME"

# -------------------------------------------------------- Android SDK --------
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
    log "Installiere Android command-line tools nach $ANDROID_HOME"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    TMP="$(mktemp -d)"
    if ! curl -fsSL -o "$TMP/tools.zip" \
        "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"; then
        warn "Download fehlgeschlagen."
        warn "Entweder ist dl.google.com nicht in der Netzwerk-Allowlist"
        warn "(siehe BOOTSTRAP.md, Abschnitt 4), oder CMDLINE_TOOLS_ZIP ist veraltet."
        exit 1
    fi
    unzip -q "$TMP/tools.zip" -d "$TMP"
    mv "$TMP/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm -rf "$TMP"
else
    log "Android SDK bereits vorhanden"
fi

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

log "Akzeptiere SDK-Lizenzen"
yes | sdkmanager --licenses >/dev/null 2>&1 || true

log "Installiere Plattform android-$ANDROID_API und build-tools $BUILD_TOOLS"
sdkmanager --install \
    "platform-tools" \
    "platforms;android-${ANDROID_API}" \
    "build-tools;${BUILD_TOOLS}" >/dev/null

# ------------------------------------------------ Gradle verdrahten ----------
# Gradle liest den SDK-Pfad aus local.properties. Datei ist gitignored.
echo "sdk.dir=$ANDROID_HOME" > "$REPO_ROOT/local.properties"
log "local.properties geschrieben"

if [ -f "$REPO_ROOT/gradlew" ]; then
    chmod +x "$REPO_ROOT/gradlew"
fi

# ------------------------------------------ Umgebung persistieren ------------
PROFILE="$HOME/.bashrc"
if ! grep -q "ANDROID_HOME=" "$PROFILE" 2>/dev/null; then
    {
        echo ""
        echo "# von .claude/setup.sh"
        echo "export JAVA_HOME=\"$JAVA_HOME\""
        echo "export ANDROID_HOME=\"$ANDROID_HOME\""
        echo "export ANDROID_SDK_ROOT=\"$ANDROID_HOME\""
        echo "export PATH=\"\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH\""
        echo "export GRADLE_OPTS=\"-Dorg.gradle.jvmargs=-Xmx4g -Dorg.gradle.daemon=false\""
    } >> "$PROFILE"
    log "Umgebungsvariablen in $PROFILE ergänzt"
fi

log "Fertig. Bauen und JVM-Tests gehen, Emulator gibt es hier nicht."
