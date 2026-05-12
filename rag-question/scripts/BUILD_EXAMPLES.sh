#!/bin/bash

# Sample build.sh Usage Commands
# Copy and adapt these commands for your workflow

# ============================================
# BASIC BUILDS
# ============================================

# Build with defaults (bronx/rag-question:dev)
# ./build.sh

# Build with custom tag (production ready)
# ./build.sh -t v1.0.0

# Build with custom tag (latest stable)
# ./build.sh -t latest

# Build with custom tag (production)
# ./build.sh -t prod

# ============================================
# REPOSITORY/NAME CUSTOMIZATION
# ============================================

# Build with custom repository
# ./build.sh -r mycompany

# Build with custom name
# ./build.sh -n my-rag-service

# Build with custom repo, name, and tag
# ./build.sh -r mycompany -n my-rag-service -t v2.0.0

# ============================================
# ARCHITECTURE SPECIFIC BUILDS
# ============================================

# Build for AMD64 (default, Intel/AMD)
# ./build.sh -a amd64

# Build for ARM64 (Apple Silicon, AWS Graviton)
# ./build.sh -a arm64 -t arm64-latest

# Build for ARM 32-bit
# ./build.sh -a arm32v7 -t arm32v7-latest

# ============================================
# TESTING OPTIONS
# ============================================

# Build with tests enabled (skip tests by default)
# ./build.sh --no-skip-tests -t test-build

# Build with tests and verbose output
# ./build.sh -v --no-skip-tests -t dev

# ============================================
# OUTPUT CONTROL
# ============================================

# Build with verbose output (full Maven output)
# ./build.sh -v

# Build with quiet output and no download progress (default)
# ./build.sh -t dev

# ============================================
# ENVIRONMENT VARIABLE APPROACH
# ============================================

# Method 1: Use environment variables (backward compatible)
# export IMAGE_REPO=myrepo
# export IMAGE_NAME=myapp
# export IMAGE_TAG=v1.0.0
# export JIB_ARCH=arm64
# ./build.sh

# Method 2: Mix environment variables and arguments
# export IMAGE_TAG=latest
# ./build.sh -r bronx -n rag-question

# ============================================
# CI/CD PIPELINE EXAMPLES
# ============================================

# Example 1: GitHub Actions style
# ./build.sh -r ${{ env.REGISTRY }} -n ${{ env.IMAGE_NAME }} -t ${{ github.ref }}

# Example 2: GitLab CI style
# ./build.sh -r $CI_REGISTRY -n $CI_PROJECT_NAME -t $CI_COMMIT_REF_SLUG

# Example 3: Jenkins style
# ./build.sh -r ${REGISTRY} -n ${JOB_NAME} -t ${BUILD_NUMBER}

# ============================================
# MULTI-ARCHITECTURE BUILDS FOR DEPLOYMENT
# ============================================

# Build AMD64 image for servers
# ./build.sh -a amd64 -t amd64-prod
# ./save_image.sh && ./transfer.sh

# Build ARM64 image for ARM servers/AWS Graviton
# ./build.sh -a arm64 -t arm64-prod
# ./save_image.sh && ./transfer.sh

# Build all architectures for a release
# for ARCH in amd64 arm64; do
#   ./build.sh -a $ARCH -t release-$ARCH
# done

# ============================================
# COMPLETE BUILD & DEPLOY WORKFLOWS
# ============================================

# Workflow 1: Build development image
# ./build.sh -t dev

# Workflow 2: Build, save, and transfer to ECR
# ./build.sh -r bronx -n rag-question -t latest
# IMAGE_NAME=rag-question IMAGE_TAG=latest ./save_image.sh
# export AWS_ACCOUNT_ID=123456789
# export ECR_IMAGE_URI=123456789.dkr.ecr.us-east-1.amazonaws.com/rag-question:latest
# ./transfer.sh

# Workflow 3: Complete build and deployment
# ./build.sh -t v1.0.0 -a amd64
# export ECR_IMAGE_URI=123456789.dkr.ecr.us-east-1.amazonaws.com/rag-question:v1.0.0
# export DEPLOY_TARGET=ecs
# export ECS_CLUSTER=production
# export ECS_SERVICE=rag-service
# export ECS_TASK_FAMILY=rag-task
# ./deploy.sh

# ============================================
# TROUBLESHOOTING
# ============================================

# Get verbose output to debug build issues
# ./build.sh -v

# Build with tests enabled to check for issues
# ./build.sh --no-skip-tests -t test

# Rebuild from scratch (clear cache)
# ./build.sh -t rebuild --no-skip-tests
