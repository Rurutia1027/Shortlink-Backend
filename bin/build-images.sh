#!/bin/bash
set -e

# Usage: ./build-images.sh <tag>
TAG=$1
LATEST_TAG="latest"

if [ -z "$TAG" ]; then
  echo "Usage: $0 <tag>"
  exit 1
fi

#modules=(admin shortlink)
modules=(shortlink flyway)

# Build and push multi-arch images
for module in "${modules[@]}"; do
  echo "Building Docker image for $module ..."
  docker buildx build \
    --platform linux/amd64,linux/arm64 \
    -t nanachi1027/shortlink-$module:$TAG \
    -t nanachi1027/shortlink-$module:$LATEST_TAG \
    --push \
    ./$module
done

# Inspect pushed images (optional)
for module in "${modules[@]}"; do
  docker buildx imagetools inspect nanachi1027/shortlink-$module:$TAG
done
