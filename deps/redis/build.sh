#!/bin/sh
#
# Use `./dev.sh` instead. This is more handy as a component of other tools.

VERSION="2.8.17"

DIR="$(dirname "$0")"
SRCDIR="$DIR/redis-$VERSION"

(cd "$SRCDIR" && sh -c "make redis-server") # trying to work around Travis failure