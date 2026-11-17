#!/bin/sh

# ----------------------------------------------------------------------------
# Gradle Wrapper startup script (generated)
# ----------------------------------------------------------------------------

set -e

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

if [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/bin/java" ]; then
        JAVACMD="$JAVA_HOME/bin/java"
    else
        JAVACMD=java
    fi
else
    JAVACMD=java
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
    exit 1
fi

exec "$JAVACMD" \
    $DEFAULT_JVM_OPTS \
    $JAVA_OPTS \
    $GRADLE_OPTS \
    -Dorg.gradle.appname="gradlew" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
