#!/usr/bin/env sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"
sh ./mvnw -v
git config --local core.hooksPath .githooks
chmod +x mvnw .githooks/pre-commit scripts/setup-dev.sh
printf 'Enabled repository pre-commit hook. Run ./mvnw verify with JDK 21.\n'
