#!/bin/bash

# Deploy Docker image to AWS

set -e

echo "======================================"
echo "Deploying image to AWS..."
echo "======================================"

# Set variables
ECR_IMAGE_URI="${ECR_IMAGE_URI}"
DEPLOY_TARGET="${DEPLOY_TARGET:-ecs}"  # Options: ecs, eks, apprunner
AWS_REGION="${AWS_REGION:-us-east-1}"

# Validate required variables
if [ -z "${ECR_IMAGE_URI}" ]; then
    echo "✗ Error: ECR_IMAGE_URI is not set!"
    echo "Set it with: export ECR_IMAGE_URI=<your-ecr-image-uri>"
    exit 1
fi

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo "✗ Error: AWS CLI is not installed!"
    exit 1
fi

echo "Deployment Target: ${DEPLOY_TARGET}"
echo "Image URI: ${ECR_IMAGE_URI}"
echo "Region: ${AWS_REGION}"
echo ""

# Deploy based on target
case "${DEPLOY_TARGET}" in
    ecs)
        deploy_ecs
        ;;
    eks)
        deploy_eks
        ;;
    apprunner)
        deploy_apprunner
        ;;
    *)
        echo "✗ Unknown deployment target: ${DEPLOY_TARGET}"
        echo "Supported targets: ecs, eks, apprunner"
        exit 1
        ;;
esac

# ECS Deployment function
deploy_ecs() {
    echo "Deploying to AWS ECS..."
    
    # Required variables for ECS
    ECS_CLUSTER="${ECS_CLUSTER}"
    ECS_SERVICE="${ECS_SERVICE}"
    ECS_TASK_FAMILY="${ECS_TASK_FAMILY}"
    
    if [ -z "${ECS_CLUSTER}" ] || [ -z "${ECS_SERVICE}" ] || [ -z "${ECS_TASK_FAMILY}" ]; then
        echo "✗ Error: Missing required variables for ECS deployment!"
        echo "Required:"
        echo "  export ECS_CLUSTER=<your-cluster-name>"
        echo "  export ECS_SERVICE=<your-service-name>"
        echo "  export ECS_TASK_FAMILY=<your-task-family-name>"
        exit 1
    fi
    
    # Get the latest task definition
    echo "Fetching latest task definition for ${ECS_TASK_FAMILY}..."
    TASK_DEF=$(aws ecs describe-task-definition \
        --task-definition ${ECS_TASK_FAMILY} \
        --region ${AWS_REGION} \
        --query 'taskDefinition' \
        --output json)
    
    # Register new task definition with updated image
    echo "Registering new task definition..."
    NEW_TASK_DEF=$(echo $TASK_DEF | jq \
        --arg IMAGE "${ECR_IMAGE_URI}" \
        '.containerDefinitions[0].image = $IMAGE | del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .compatibilities, .registeredAt, .registeredBy)')
    
    TASK_REVISION=$(echo $NEW_TASK_DEF | jq '.family + ":" + (.revision|tostring)' -r)
    
    # Register the task definition
    aws ecs register-task-definition \
        --region ${AWS_REGION} \
        --cli-input-json "$(echo $NEW_TASK_DEF | jq -c .)"
    
    # Update service
    echo "Updating ECS service..."
    aws ecs update-service \
        --cluster ${ECS_CLUSTER} \
        --service ${ECS_SERVICE} \
        --task-definition ${TASK_REVISION} \
        --region ${AWS_REGION}
    
    echo ""
    echo "======================================"
    echo "✓ ECS deployment initiated!"
    echo "======================================"
    echo "Cluster: ${ECS_CLUSTER}"
    echo "Service: ${ECS_SERVICE}"
    echo "Image: ${ECR_IMAGE_URI}"
    echo ""
}

# EKS Deployment function
deploy_eks() {
    echo "Deploying to AWS EKS..."
    
    # Required variables for EKS
    EKS_CLUSTER="${EKS_CLUSTER}"
    K8S_NAMESPACE="${K8S_NAMESPACE:-default}"
    K8S_DEPLOYMENT="${K8S_DEPLOYMENT:-rag-question-app}"
    
    if [ -z "${EKS_CLUSTER}" ]; then
        echo "✗ Error: Missing required variables for EKS deployment!"
        echo "Required:"
        echo "  export EKS_CLUSTER=<your-cluster-name>"
        echo "Optional:"
        echo "  export K8S_NAMESPACE=<namespace> (default: default)"
        echo "  export K8S_DEPLOYMENT=<deployment-name> (default: rag-question-app)"
        exit 1
    fi
    
    # Check if kubectl is installed
    if ! command -v kubectl &> /dev/null; then
        echo "✗ Error: kubectl is not installed!"
        exit 1
    fi
    
    # Update kubeconfig
    echo "Configuring kubectl for cluster: ${EKS_CLUSTER}"
    aws eks update-kubeconfig \
        --region ${AWS_REGION} \
        --name ${EKS_CLUSTER}
    
    # Update image in deployment
    echo "Updating Kubernetes deployment..."
    kubectl set image deployment/${K8S_DEPLOYMENT} \
        ${K8S_DEPLOYMENT}=${ECR_IMAGE_URI} \
        -n ${K8S_NAMESPACE}
    
    # Wait for rollout
    echo "Waiting for rollout to complete..."
    kubectl rollout status deployment/${K8S_DEPLOYMENT} -n ${K8S_NAMESPACE}
    
    echo ""
    echo "======================================"
    echo "✓ EKS deployment successful!"
    echo "======================================"
    echo "Cluster: ${EKS_CLUSTER}"
    echo "Namespace: ${K8S_NAMESPACE}"
    echo "Deployment: ${K8S_DEPLOYMENT}"
    echo "Image: ${ECR_IMAGE_URI}"
    echo ""
}

# App Runner Deployment function
deploy_apprunner() {
    echo "Deploying to AWS App Runner..."
    
    # Required variables for App Runner
    APPRUNNER_SERVICE="${APPRUNNER_SERVICE}"
    
    if [ -z "${APPRUNNER_SERVICE}" ]; then
        echo "✗ Error: Missing required variables for App Runner deployment!"
        echo "Required:"
        echo "  export APPRUNNER_SERVICE=<your-service-name>"
        exit 1
    fi
    
    echo "Updating App Runner service with new image..."
    aws apprunner update-service \
        --service-arn "arn:aws:apprunner:${AWS_REGION}:*:service/${APPRUNNER_SERVICE}" \
        --source-configuration ImageRepository={ImageIdentifier=${ECR_IMAGE_URI},ImageRepositoryType=ECR} \
        --region ${AWS_REGION}
    
    echo ""
    echo "======================================"
    echo "✓ App Runner deployment initiated!"
    echo "======================================"
    echo "Service: ${APPRUNNER_SERVICE}"
    echo "Image: ${ECR_IMAGE_URI}"
    echo "Note: Check App Runner console for deployment status"
    echo ""
}

# Help function
show_help() {
    cat << EOF
Usage: ./deploy.sh [OPTIONS]

Required Environment Variables:
  ECR_IMAGE_URI          ECR image URI from transfer.sh
  DEPLOY_TARGET          Deployment target (ecs, eks, apprunner)
  AWS_REGION             AWS region (default: us-east-1)

For ECS deployment, also set:
  ECS_CLUSTER            ECS cluster name
  ECS_SERVICE            ECS service name
  ECS_TASK_FAMILY        ECS task family name

For EKS deployment, also set:
  EKS_CLUSTER            EKS cluster name
  K8S_NAMESPACE          Kubernetes namespace (default: default)
  K8S_DEPLOYMENT         Kubernetes deployment name (default: rag-question-app)

For App Runner deployment, also set:
  APPRUNNER_SERVICE      App Runner service name

Example (ECS):
  export ECR_IMAGE_URI=123456789.dkr.ecr.us-east-1.amazonaws.com/rag-question-app:latest
  export DEPLOY_TARGET=ecs
  export ECS_CLUSTER=my-cluster
  export ECS_SERVICE=rag-service
  export ECS_TASK_FAMILY=rag-task
  ./deploy.sh

EOF
}

if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    show_help
fi
