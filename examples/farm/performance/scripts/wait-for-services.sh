#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Waiting for services to be ready...${NC}"

# Configuration
MAX_ATTEMPTS=30
SLEEP_INTERVAL=2

# Services to check
declare -A SERVICES=(
    ["Farm"]="http://localhost:3033/ready"
)

# Function to check if a service is ready
check_service() {
    local name=$1
    local url=$2
    local attempt=1

    echo -e "${YELLOW}Checking ${name} at ${url}...${NC}"

    while [ $attempt -le $MAX_ATTEMPTS ]; do
        if curl -sf "${url}" > /dev/null 2>&1; then
            echo -e "${GREEN}✓ ${name} is ready${NC}"
            return 0
        fi

        echo -e "  Attempt ${attempt}/${MAX_ATTEMPTS} - ${name} not ready yet..."
        sleep $SLEEP_INTERVAL
        attempt=$((attempt + 1))
    done

    echo -e "${RED}✗ ${name} failed to become ready after ${MAX_ATTEMPTS} attempts${NC}"
    return 1
}

# Check all services
all_ready=true
for service_name in "${!SERVICES[@]}"; do
    if ! check_service "$service_name" "${SERVICES[$service_name]}"; then
        all_ready=false
    fi
done

if [ "$all_ready" = true ]; then
    echo -e "${GREEN}All services are ready!${NC}"
    exit 0
else
    echo -e "${RED}Some services failed to start${NC}"
    exit 1
fi
