#!/bin/sh
# Runs the plain-Java checks for the Garmin USB Link classes copied from the AutoReplacer probe.
# No Android SDK needed: these classes only use java.*.
set -e
cd "$(dirname "$0")"
SRC=../app/src/main/java/com/andrerinas/openheadunit/autoshow
OUT=$(mktemp -d)
javac --release 8 -d "$OUT" $SRC/Wire.java $SRC/SerialWriter.java $SRC/TsProtocol.java $SRC/SessionWatchdog.java $SRC/DiagnosticPolicy.java *Test.java
for t in WireTest SerialWriterTest TsProtocolTest SessionWatchdogTest; do java -cp "$OUT" com.andrerinas.openheadunit.autoshow.$t; done
