#!/usr/bin/env sh
set -eu
mkdir -p build
javac -encoding UTF-8 -d build src/*.java
java -cp build Main "$@"
