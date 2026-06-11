#!/bin/bash
# Compile everything and run all test mains. Fails fast on the first failure.
set -e
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
find src test -name '*.java' > /tmp/viz_sources.txt
javac -g -d out @/tmp/viz_sources.txt
for t in $(cd test && ls Test*.java 2>/dev/null | sed 's/\.java//'); do
  java -cp out "$t"
done
echo "ALL TESTS PASS"
