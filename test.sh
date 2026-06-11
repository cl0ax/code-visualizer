#!/bin/bash
# Compile everything and run all test mains. Fails fast on the first failure.
set -e
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
SOURCES=$(mktemp /tmp/viz_sources.XXXXXX)
trap 'rm -f "$SOURCES"' EXIT
find src test -name '*.java' > "$SOURCES"
javac -g -d out @"$SOURCES"
for t in $(cd test && ls Test*.java 2>/dev/null | sed 's/\.java//'); do
  java -cp out "$t"
done
echo "ALL TESTS PASS"
