#!/bin/bash

# Save Docker image to tar file

set -e

echo "======================================"
echo "Saving Docker image to tar file..."
echo "======================================"

# Set variables
IMAGE_NAME="${IMAGE_NAME:-rag-question-app}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
OUTPUT_DIR="${OUTPUT_DIR:-.}"
FILENAME="${OUTPUT_DIR}/${IMAGE_NAME}-${IMAGE_TAG}.tar"

# Check if image exists
if ! docker image inspect "${IMAGE_NAME}:${IMAGE_TAG}" > /dev/null 2>&1; then
    echo "✗ Error: Docker image '${IMAGE_NAME}:${IMAGE_TAG}' not found!"
    echo "Please run build.sh first."
    exit 1
fi

# Save the image
echo "Saving image to: ${FILENAME}"
docker save "${IMAGE_NAME}:${IMAGE_TAG}" -o "${FILENAME}"

if [ $? -eq 0 ]; then
    IMAGE_SIZE=$(du -h "${FILENAME}" | cut -f1)
    echo ""
    echo "======================================"
    echo "✓ Image saved successfully!"
    echo "======================================"
    echo "Image: ${IMAGE_NAME}:${IMAGE_TAG}"
    echo "File: ${FILENAME}"
    echo "Size: ${IMAGE_SIZE}"
    echo ""
else
    echo ""
    echo "======================================"
    echo "✗ Failed to save image!"
    echo "======================================"
    exit 1
fi
