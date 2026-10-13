#!/usr/bin/env sh
set -eu
export JAVA_HOME="${JAVA_HOME:-/opt/java/jdk21}"
export PATH="$JAVA_HOME/bin:$PATH"
MAVEN_VERSION=3.9.11
MAVEN_CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/liushao-maven"
MAVEN_DIR="$MAVEN_CACHE/apache-maven-$MAVEN_VERSION"
if [ ! -x "$MAVEN_DIR/bin/mvn" ]; then
  mkdir -p "$MAVEN_CACHE"
  ARCHIVE="$MAVEN_CACHE/apache-maven-$MAVEN_VERSION-bin.tar.gz"
  URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz"
  curl -fsSL "$URL" -o "$ARCHIVE"
  curl -fsSL "$URL.sha512" -o "$ARCHIVE.sha512"
  EXPECTED=$(cat "$ARCHIVE.sha512")
  printf '%s  %s\n' "$EXPECTED" "$ARCHIVE" | sha512sum -c -
  tar -xzf "$ARCHIVE" -C "$MAVEN_CACHE"
fi
exec "$MAVEN_DIR/bin/mvn" "$@"
