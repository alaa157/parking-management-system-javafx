#!/bin/sh
# Render all D2 diagram sources to SVG. Requires: d2 (https://d2lang.com)
# Install: curl -fsSL https://d2lang.com/install.sh | sh
set -eu
cd "$(dirname "$0")/../docs/diagrams"
for src in *.d2; do
  out="${src%.d2}.svg"
  echo "rendering $src -> $out"
  d2 --pad 16 "$src" "$out"
done
