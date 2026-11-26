#!/bin/bash
set -e

# The absolute path of the extension directory
EXT_DIR=$(pwd)

echo "Starting Dockerized Test & Build for Extension..."

docker run --rm \
  -v "${EXT_DIR}:/app" \
  -w /app \
  -u node \
  node:18-alpine \
  sh -c "npm install --legacy-peer-deps && npm run test && npm run build"

echo "==================================="
echo "✅ Build complete!"
echo "The extension has been packaged and can be found in the web-ext-artifacts/ folder."
