#!/bin/bash

# Transfer Docker image to AWS ECR (Elastic Container Registry)

set -e

echo "======================================"
echo "Transferring image to AWS ECR..."
echo "======================================"

# Set variables
LOCAL_IMAGE_NAME="${IMAGE_NAME:-rag-question-app}"
LOCAL_IMAGE_TAG="${IMAGE_TAG:-latest}"
AWS_REGION="${AWS_REGION:-us-east-1}"
AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID}"
ECR_REPO_NAME="${ECR_REPO_NAME:-rag-question-app}"

# Validate required variables
if [ -z "${AWS_ACCOUNT_ID}" ]; then
    echo "✗ Error: AWS_ACCOUNT_ID is not set!"
    echo "Set it with: export AWS_ACCOUNT_ID=<your-account-id>"
    exit 1
fi

# Set up ECR variables
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
ECR_IMAGE="${ECR_REGISTRY}/${ECR_REPO_NAME}:${LOCAL_IMAGE_TAG}"

echo "AWS Account: ${AWS_ACCOUNT_ID}"
echo "AWS Region: ${AWS_REGION}"
echo "ECR Registry: ${ECR_REGISTRY}"
echo ""

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo "✗ Error: AWS CLI is not installed!"
    echo "Please install AWS CLI: https://aws.amazon.com/cli/"
    exit 1
fi

# Login to ECR
echo "Logging in to AWS ECR..."
aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}

if [ $? -ne 0 ]; then
    echo "✗ Error: Failed to login to ECR!"
    exit 1
fi

echo "✓ Login successful!"
echo ""

# Check if ECR repository exists, if not create it
echo "Checking ECR repository..."
if ! aws ecr describe-repositories --repository-names ${ECR_REPO_NAME} --region ${AWS_REGION} > /dev/null 2>&1; then
    echo "Repository not found. Creating: ${ECR_REPO_NAME}"
    aws ecr create-repository \
        --repository-name ${ECR_REPO_NAME} \
        --region ${AWS_REGION}
    echo "✓ Repository created!"
fi

# Tag the local image with ECR repository URL
echo ""
echo "Tagging image for ECR..."
docker tag "${LOCAL_IMAGE_NAME}:${LOCAL_IMAGE_TAG}" "${ECR_IMAGE}"

# Push to ECR
echo "Pushing image to ECR..."
docker push "${ECR_IMAGE}"

if [ $? -eq 0 ]; then
    echo ""
    echo "======================================"
    echo "✓ Image transferred successfully!"
    echo "======================================"
    echo "ECR Image URI: ${ECR_IMAGE}"
    echo ""
    echo "Save this URI for deployment:"
    echo "  export ECR_IMAGE_URI=${ECR_IMAGE}"
    echo ""
else
    echo ""
    echo "======================================"
    echo "✗ Failed to transfer image!"
    echo "======================================"
    exit 1
fi
