#!/bin/sh
# Dev container entrypoint: start the app with `mvn spring-boot:run`, then recompile whenever a file under src/
# changes so Spring DevTools restarts it (a polling loop: file-change events do not cross Windows / macOS mounts).
set -e

cd /app
PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"
DEBUG_PORT="${DEBUG_PORT:-5005}"
INTERVAL="${WATCH_INTERVAL:-2}"

echo "[dev] compiling (profile: ${PROFILE})…"
mvn -B -q compile

echo "[dev] starting the app on port ${PORT:-5000} (remote debug on ${DEBUG_PORT})…"
mvn -B spring-boot:run \
  -Dspring-boot.run.profiles="${PROFILE}" \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT} ${JAVA_OPTS:-}" &
APP_PID=$!

# stop the app when the container stops
trap 'kill "$APP_PID" 2>/dev/null; wait "$APP_PID" 2>/dev/null; exit 0' INT TERM

MARK=/tmp/.last-compile
touch "$MARK"
while kill -0 "$APP_PID" 2>/dev/null; do
  sleep "$INTERVAL"
  CHANGED=$(find src/main -type f -newer "$MARK" 2>/dev/null | head -n 1)
  if [ -n "$CHANGED" ]; then
    touch "$MARK"
    echo "[dev] change detected (${CHANGED}) — recompiling…"
    if mvn -B -q -o compile; then
      echo "[dev] compiled; DevTools restarts the app."
    else
      echo "[dev] compile failed — fix the error and save again."
    fi
  fi
done

wait "$APP_PID"
