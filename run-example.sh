#!/usr/bin/env sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/appointment-agent-observability-classes"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" $(find src/main/java -name '*.java')
java -cp "$BUILD_DIR" dev.infrai.health.AppointmentLoopExample
