#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p out
javac -d out src/main/java/br/com/simplelauncher/*.java
java -cp out br.com.simplelauncher.LauncherApp
