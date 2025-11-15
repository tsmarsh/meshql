#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get the script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PERF_DIR="$(dirname "$SCRIPT_DIR")"
FARM_DIR="$(dirname "$PERF_DIR")"
RESULTS_DIR="${PERF_DIR}/results"

# Create results directory if it doesn't exist
mkdir -p "${RESULTS_DIR}"

# Configuration
JMETER_HOME="${JMETER_HOME:-}"
TEST_PLAN="${1:-}"

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Farm Performance Test Suite${NC}"
echo -e "${BLUE}================================${NC}"

# Check if JMeter is installed
if [ -z "$JMETER_HOME" ]; then
    if command -v jmeter &> /dev/null; then
        JMETER_CMD="jmeter"
    else
        echo -e "${RED}Error: JMeter not found${NC}"
        echo -e "Please install JMeter and set JMETER_HOME or add jmeter to PATH"
        echo -e "Download from: https://jmeter.apache.org/download_jmeter.cgi"
        exit 1
    fi
else
    JMETER_CMD="${JMETER_HOME}/bin/jmeter"
fi

echo -e "${GREEN}✓ JMeter found${NC}"

# Wait for services to be ready
echo -e "\n${YELLOW}Checking if services are ready...${NC}"
if ! "${SCRIPT_DIR}/wait-for-services.sh"; then
    echo -e "${RED}Services are not ready. Please start docker-compose first:${NC}"
    echo -e "  cd ${FARM_DIR}"
    echo -e "  docker-compose up -d"
    exit 1
fi

# Determine which test plans to run
if [ -n "$TEST_PLAN" ]; then
    if [ ! -f "${PERF_DIR}/test-plans/${TEST_PLAN}" ]; then
        echo -e "${RED}Error: Test plan not found: ${TEST_PLAN}${NC}"
        exit 1
    fi
    TEST_PLANS=("${PERF_DIR}/test-plans/${TEST_PLAN}")
else
    # Run all test plans
    TEST_PLANS=($(find "${PERF_DIR}/test-plans" -name "*.jmx" 2>/dev/null || true))

    if [ ${#TEST_PLANS[@]} -eq 0 ]; then
        echo -e "${YELLOW}Warning: No JMeter test plans found in ${PERF_DIR}/test-plans/${NC}"
        echo -e "Please create .jmx files in the test-plans directory"
        exit 0
    fi
fi

# Run each test plan
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
for test_plan in "${TEST_PLANS[@]}"; do
    test_name=$(basename "$test_plan" .jmx)
    result_file="${RESULTS_DIR}/${test_name}_${TIMESTAMP}.jtl"
    report_dir="${RESULTS_DIR}/${test_name}_${TIMESTAMP}_report"

    echo -e "\n${BLUE}Running test: ${test_name}${NC}"
    echo -e "Results will be saved to: ${result_file}"

    # Run JMeter in non-GUI mode
    $JMETER_CMD -n \
        -t "$test_plan" \
        -l "$result_file" \
        -e -o "$report_dir" \
        -j "${RESULTS_DIR}/${test_name}_${TIMESTAMP}.log"

    echo -e "${GREEN}✓ Test completed: ${test_name}${NC}"
    echo -e "HTML Report: ${report_dir}/index.html"
done

echo -e "\n${GREEN}================================${NC}"
echo -e "${GREEN}All tests completed!${NC}"
echo -e "${GREEN}================================${NC}"
echo -e "Results directory: ${RESULTS_DIR}"
