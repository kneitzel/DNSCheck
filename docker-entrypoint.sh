#!/bin/sh
set -e

if [ "$1" = "service" ]; then
  shift
  exec java ${JAVA_OPTS} -jar /app/service/quarkus-run.jar "$@"
elif [ "$1" = "cli" ]; then
  shift
  exec java ${JAVA_OPTS} -jar /app/cli/dnscheck-cli.jar "$@"
else
  exec java ${JAVA_OPTS} -jar /app/service/quarkus-run.jar "$@"
fi
