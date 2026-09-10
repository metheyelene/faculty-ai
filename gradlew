#!/bin/sh
# Faculty AI Gradle bootstrap wrapper.
#
# Uses a cached Gradle 8.14.3 distribution (checked in at .android-gradle-cache)
# so the project builds without network access on this machine. To restore the
# standard wrapper experience, run: gradle wrapper --gradle-version 8.14.3

# Locate a JDK for the launcher (daemon JVM is pinned in gradle.properties).
if [ -z "$JAVA_HOME" ]; then
    if [ -d "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]; then
        JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
    elif [ -d "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" ]; then
        JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
    fi
    export JAVA_HOME
fi

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)

CLASSPATH="$APP_HOME/.android-gradle-cache/gradle-dist/bin/gradle"

exec "$CLASSPATH" "$@"
