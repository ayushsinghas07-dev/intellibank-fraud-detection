#!/usr/bin/env sh
# ----------------------------------------------------------------------------
# Maven Wrapper Executable for Unix
# ----------------------------------------------------------------------------

if [ -f "$(dirname "$0")/../maven/apache-maven-3.9.6/bin/mvn" ]; then
    exec "$(dirname "$0")/../maven/apache-maven-3.9.6/bin/mvn" "$@"
else
    exec mvn "$@"
fi
