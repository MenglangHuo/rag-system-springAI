#!/bin/bash

# Builds Docker image using Maven JIB plugin with flexible configuration
# Usage: ./build.sh [OPTIONS]
#   -r, --repo REPO         Image repository (default: bronx)
#   -n, --name NAME         Image name (default: rag-question)
#   -t, --tag TAG           Image tag (default: dev)
#   -a, --arch ARCH         Architecture: amd64, arm64, arm32v7 (default: amd64)
#   --no-progress           Disable download progress (default: true)
#   -v, --verbose           Verbose output (default: false)
#   -h, --help              Show this help message

set -e

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
IMAGE_REPO="${IMAGE_REPO:-bronx}"
IMAGE_NAME="${IMAGE_NAME:-rag-question}"
IMAGE_TAG="${IMAGE_TAG:-dev }"
JIB_ARCH="${JIB_ARCH:-amd64}"
SKIP_TESTS=true
NO_PROGRESS=true
VERBOSE=false

# Function to print help
show_help() {
    cat << 'EOF'
Enhanced JIB Docker Image Build Script

Usage: ./build.sh [OPTIONS]

Options:
  -r, --repo REPO         Image repository (default: bronx)
  -n, --name NAME         Image name (default: rag-question)
  -t, --tag TAG           Image tag (default: dev)
  -a, --arch ARCH         Architecture: amd64, arm64, arm32v7 (default: amd64)
  -s, --skip-tests        Skip tests (default: true)
  --no-progress           Disable download progress (default: true)
  -v, --verbose           Verbose output (default: false)
  -h, --help              Show this help message

Examples:
  # Build with defaults (bronx/rag-question:dev)
  ./build.sh

  # Build with custom tag
  ./build.sh -t v1.0.0

  # Build with custom name and tag
  ./build.sh -n my-app -t latest

  # Build for ARM64 architecture
  ./build.sh -a arm64 -t arm64-latest

  # Build with verbose output and run tests
  ./build.sh -v -t dev --no-skip-tests

  # Build with all custom options
  ./build.sh -r myrepo -n myapp -t 1.2.3 -a arm64

EOF
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -r|--repo)
            IMAGE_REPO="$2"
            shift 2
            ;;
        -n|--name)
            IMAGE_NAME="$2"
            shift 2
            ;;
        -t|--tag)
            IMAGE_TAG="$2"
            shift 2
            ;;
        -a|--arch)
            JIB_ARCH="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            show_help
            exit 1
            ;;
    esac
done

# Validate architecture
case "${JIB_ARCH}" in
    amd64|arm64|arm32v7)
        ;;
    *)
        echo -e "${RED}✗ Invalid architecture: ${JIB_ARCH}${NC}"
        echo "Supported: amd64, arm64, arm32v7"
        exit 1
        ;;
esac

# Full image name
FULL_IMAGE_NAME="${IMAGE_REPO}/${IMAGE_NAME}:${IMAGE_TAG}"

# Print build info
echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}Building Docker Image using JIB${NC}"
echo -e "${BLUE}======================================${NC}"
echo ""
echo "Repository: ${IMAGE_REPO}"
echo "Name:       ${IMAGE_NAME}"
echo "Tag:        ${IMAGE_TAG}"
echo "Full Name:  ${FULL_IMAGE_NAME}"
echo "Architecture: ${JIB_ARCH}"
echo "Skip Tests: ${SKIP_TESTS}"
echo "No Progress: ${NO_PROGRESS}"
echo ""

# Build Maven arguments
MVN_ARGS=("clean" "package" "jib:dockerBuild")

# Add skip tests flag
if [ "${SKIP_TESTS}" = true ]; then
    MVN_ARGS+=("-DskipTests")
fi

# Add no transfer progress
if [ "${NO_PROGRESS}" = true ]; then
    MVN_ARGS+=("--no-transfer-progress")
fi

# Add user properties for image configuration
MVN_ARGS+=(
    "-Dimage.repo=${IMAGE_REPO}"
    "-Dimage.name=${IMAGE_NAME}"
    "-Dimage.tag=${IMAGE_TAG}"
    "-Djib.arch=${JIB_ARCH}"
)

# Add verbose flag if requested
if [ "${VERBOSE}" = true ]; then
    MVN_ARGS+=("-X")
else
    MVN_ARGS+=("-q")
fi

# Execute Maven build with JIB
echo "Building..."
echo ""

if [ "${VERBOSE}" = true ]; then
    # Full output for verbose mode
    ./mvnw "${MVN_ARGS[@]}"
else
    # Filtered output for normal mode (hide download messages)
    ./mvnw "${MVN_ARGS[@]}" 2>&1 | grep -v "^\[INFO\] Download" || true
fi

BUILD_STATUS=$?

echo ""

if [ $BUILD_STATUS -eq 0 ]; then
    echo -e "${GREEN}======================================${NC}"
    echo -e "${GREEN}✓ Build successful!${NC}"
    echo -e "${GREEN}======================================${NC}"
    echo ""
    echo "Image created: ${FULL_IMAGE_NAME}"
    echo ""
else
    echo -e "${RED}======================================${NC}"
    echo -e "${RED}✗ Build failed!${NC}"
    echo -e "${RED}======================================${NC}"
    echo ""
    echo "Run with -v flag for detailed output:"
    echo "  ./build.sh -v"
    exit 1
fi
