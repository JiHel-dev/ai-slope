#!/bin/sh
# Installs a self-contained Android build toolchain without sudo:
#   - Eclipse Temurin JDK 17
#   - Android command-line tools, platform-tools, platform and build-tools
#   - A Gradle distribution (only used to bootstrap Gradle wrappers)
#
# Usage:
#   ./setup-android-toolchain.sh [--prefix DIR] [--platform 35] [--build-tools 35.0.0]
#                                [--proxy host:port] [--ca-cert FILE] [-h|--help]
#
# Afterwards, load the environment with:
#   . "$PREFIX/env.sh"

set -eu

PREFIX="${ANDROID_TOOLCHAIN_PREFIX:-$HOME/.local/android}"
PLATFORM="35"
BUILD_TOOLS="35.0.0"
PROXY=""
CA_CERT=""
CMDLINE_TOOLS_VERSION="11076708"
GRADLE_VERSION="8.11.1"

print_help() {
  echo "Install JDK 17, Android SDK and Gradle into a user directory"
  echo
  echo "Optional:"
  echo "  --prefix DIR           Install directory (default: $PREFIX)"
  echo "  --platform N           Android platform API level (default: $PLATFORM)"
  echo "  --build-tools VER      Build-tools version (default: $BUILD_TOOLS)"
  echo "  --proxy HOST:PORT      HTTP(S) proxy used for downloads, sdkmanager and Gradle"
  echo "  --ca-cert FILE         Extra CA certificate (PEM) to trust, e.g. a corporate TLS inspection CA."
  echo "                         It is only added to the toolchain's JDK and CA bundle, never system-wide."
  echo "  -h, --help             Show this help"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --prefix) PREFIX="$2"; shift 2 ;;
    --platform) PLATFORM="$2"; shift 2 ;;
    --build-tools) BUILD_TOOLS="$2"; shift 2 ;;
    --proxy) PROXY="$2"; shift 2 ;;
    --ca-cert) CA_CERT="$2"; shift 2 ;;
    -h|--help) print_help; exit 0 ;;
    *) echo "Unknown option: $1" >&2; print_help >&2; exit 1 ;;
  esac
done

for cmd in curl unzip tar; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "Missing required command: $cmd" >&2; exit 1; }
done

mkdir -p "$PREFIX"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

JDK_DIR="$PREFIX/jdk-17"
SDK_DIR="$PREFIX/sdk"
GRADLE_DIR="$PREFIX/gradle-$GRADLE_VERSION"
CA_BUNDLE="$PREFIX/certs/ca-bundle.pem"

if [ -n "$CA_CERT" ]; then
  [ -f "$CA_CERT" ] || { echo "CA certificate not found: $CA_CERT" >&2; exit 1; }
  mkdir -p "$PREFIX/certs"
  SYSTEM_BUNDLE=""
  for f in /etc/pki/tls/certs/ca-bundle.crt /etc/ssl/certs/ca-certificates.crt /etc/ssl/cert.pem; do
    if [ -f "$f" ]; then SYSTEM_BUNDLE="$f"; break; fi
  done
  { [ -n "$SYSTEM_BUNDLE" ] && cat "$SYSTEM_BUNDLE"; cat "$CA_CERT"; } > "$CA_BUNDLE"
  export CURL_CA_BUNDLE="$CA_BUNDLE"
fi

if [ -n "$PROXY" ]; then
  export https_proxy="http://$PROXY"
  export http_proxy="http://$PROXY"
fi

download() {
  curl -fsSL --retry 2 --connect-timeout 20 -o "$1" "$2"
}

if [ ! -x "$JDK_DIR/bin/java" ]; then
  echo "==> Installing Temurin JDK 17"
  download "$TMP_DIR/jdk.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p "$JDK_DIR"
  tar -xzf "$TMP_DIR/jdk.tar.gz" -C "$JDK_DIR" --strip-components=1
else
  echo "==> JDK 17 already installed"
fi

if [ -n "$CA_CERT" ]; then
  if ! "$JDK_DIR/bin/keytool" -list -cacerts -storepass changeit -alias toolchain-extra-ca >/dev/null 2>&1; then
    echo "==> Adding extra CA to the JDK trust store"
    "$JDK_DIR/bin/keytool" -importcert -noprompt -cacerts -storepass changeit \
      -alias toolchain-extra-ca -file "$CA_CERT" >/dev/null
  fi
fi

if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "==> Installing Android command-line tools"
  download "$TMP_DIR/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  mkdir -p "$SDK_DIR/cmdline-tools"
  unzip -q "$TMP_DIR/cmdline-tools.zip" -d "$TMP_DIR"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$TMP_DIR/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
else
  echo "==> Android command-line tools already installed"
fi

if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  echo "==> Installing Gradle $GRADLE_VERSION"
  download "$TMP_DIR/gradle.zip" \
    "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  unzip -q "$TMP_DIR/gradle.zip" -d "$PREFIX"
else
  echo "==> Gradle already installed"
fi

{
  echo "export JAVA_HOME=\"$JDK_DIR\""
  echo "export ANDROID_HOME=\"$SDK_DIR\""
  echo "export ANDROID_SDK_ROOT=\"$SDK_DIR\""
  echo "export PATH=\"$JDK_DIR/bin:$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$GRADLE_DIR/bin:\$PATH\""
  if [ -n "$CA_CERT" ]; then
    echo "export CURL_CA_BUNDLE=\"$CA_BUNDLE\""
    echo "export REQUESTS_CA_BUNDLE=\"$CA_BUNDLE\""
  fi
  if [ -n "$PROXY" ]; then
    PROXY_HOST="${PROXY%:*}"
    PROXY_PORT="${PROXY##*:}"
    echo "export https_proxy=\"http://$PROXY\""
    echo "export http_proxy=\"http://$PROXY\""
    echo "export SDKMANAGER_OPTS=\"-Dhttps.proxyHost=$PROXY_HOST -Dhttps.proxyPort=$PROXY_PORT -Dhttp.proxyHost=$PROXY_HOST -Dhttp.proxyPort=$PROXY_PORT\""
    echo "export GRADLE_OPTS=\"\$SDKMANAGER_OPTS\""
    echo "export JAVA_TOOL_OPTIONS=\"\$SDKMANAGER_OPTS\""
  fi
} > "$PREFIX/env.sh"

. "$PREFIX/env.sh"

if [ -n "$PROXY" ]; then
  set -- --proxy=http "--proxy_host=${PROXY%:*}" "--proxy_port=${PROXY##*:}"
else
  set --
fi

echo "==> Accepting SDK licenses"
yes | sdkmanager "$@" --licenses >/dev/null 2>&1 || true

echo "==> Installing SDK packages"
sdkmanager "$@" --install \
  "platform-tools" \
  "platforms;android-$PLATFORM" \
  "build-tools;$BUILD_TOOLS" | grep -v '^\[' || true

echo
echo "Toolchain ready in $PREFIX"
echo "Load it with:  . \"$PREFIX/env.sh\""
