#!/bin/bash
cd "$(dirname "$0")"
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
