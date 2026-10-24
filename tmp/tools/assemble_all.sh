#!/bin/bash
set -e

CP="/tmp/smali-2.5.2.jar:/tmp/util-2.5.2.jar:/tmp/dexlib2-2.5.2.jar:/tmp/jcommander-1.64.jar:/tmp/antlr-runtime-3.5.2.jar:/tmp/stringtemplate-3.2.1.jar:/opt/android/sdk/cmdline-tools/latest/lib/external/com/google/guava/guava/31.1-jre/guava-31.1-jre.jar"

echo "Assembling classes5..."
java -cp "$CP" org.jf.smali.Main a -a 26 -o app/precompiled-dex/classes5.dex /tmp/smali/classes5

echo "Assembling classes6..."
java -cp "$CP" org.jf.smali.Main a -a 26 -o app/precompiled-dex/classes6.dex /tmp/smali/classes6

echo "Assembling classes8..."
java -cp "$CP" org.jf.smali.Main a -a 26 -o app/precompiled-dex/classes8.dex /tmp/smali/classes8

echo "Assembling classes9..."
java -cp "$CP" org.jf.smali.Main a -a 26 -o app/precompiled-dex/classes9.dex /tmp/smali/classes9

echo "Assembling classes10..."
java -cp "$CP" org.jf.smali.Main a -a 26 -o app/precompiled-dex/classes10.dex /tmp/smali/classes10

echo "All dex files assembled successfully."
ls -lh app/precompiled-dex/*.dex
