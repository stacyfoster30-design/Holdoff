#!/usr/bin/env sh
APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
DEFAULT_JVM_OPTS='-Dfile.encoding=UTF-8 "-Xmx64m" "-Xms64m"'
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
java $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
