#!/usr/bin/env bash
set -euo pipefail

# Egg Economy Queue seed data generator
# Same domain model as egg-economy, but projections are computed
# and posted directly (no CDC pipeline).
#
# Usage: ./scripts/seed.sh [BASE_URL]
#   BASE_URL defaults to http://localhost:5088

BASE_URL="${1:-http://localhost:5088}"

echo "=== Egg Economy Queue Seed Data ==="
echo "Base URL: $BASE_URL"
echo ""

# Wait for service
echo "Waiting for service..."
for i in $(seq 1 30); do
    if curl -sf "$BASE_URL/health" > /dev/null 2>&1; then
        echo "Service is ready."
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "ERROR: Service not ready after 30 attempts"
        exit 1
    fi
    sleep 2
done

# --- Helper functions ---

post_entity() {
    local path="$1"
    local data="$2"
    curl -sf -X POST "$BASE_URL/$path" \
        -H "Content-Type: application/json" \
        -d "$data" \
        -o /dev/null -w "%{redirect_url}" 2>/dev/null || true
}

query_graphql() {
    local path="$1"
    local query="$2"
    local body
    body=$(jq -n --arg q "$query" '{"query": $q}')
    curl -sf -X POST "$BASE_URL/$path" \
        -H "Content-Type: application/json" \
        -d "$body" 2>/dev/null
}

extract_ids() {
    local path="$1"
    local query_name="$2"
    query_graphql "$path" "{ $query_name { id } }" | jq -r ".data.$query_name[].id"
}

echo ""
echo "--- Phase 1: Creating Actors ---"

# Create farms (5 farms across 3 zones)
echo "Creating farms..."
for farm_data in \
    '{"name":"Sunrise Megafarm","farm_type":"megafarm","zone":"north","owner":"AgriCorp"}' \
    '{"name":"Prairie Industrial","farm_type":"megafarm","zone":"north","owner":"FarmCo Inc."}' \
    '{"name":"Green Valley Farm","farm_type":"local_farm","zone":"east","owner":"Bob Smith"}' \
    '{"name":"Riverside Ranch","farm_type":"local_farm","zone":"east","owner":"Maria Garcia"}' \
    '{"name":"Hillside Homestead","farm_type":"homestead","zone":"south","owner":"Alice Jones"}'; do
    post_entity "farm/api" "$farm_data"
done
echo "  5 farms created"

# Get farm IDs
sleep 1
FARM_IDS=$(extract_ids "farm/graph" "getAll")
FARM1=$(echo "$FARM_IDS" | sed -n '1p')
FARM2=$(echo "$FARM_IDS" | sed -n '2p')
FARM3=$(echo "$FARM_IDS" | sed -n '3p')
FARM4=$(echo "$FARM_IDS" | sed -n '4p')
FARM5=$(echo "$FARM_IDS" | sed -n '5p')

# Create coops (8 coops)
echo "Creating coops..."
for i in 1 2 3; do
    post_entity "coop/api" "$(jq -n --arg name "Industrial Coop $i" --arg fid "$FARM1" '{"name":$name,"farm_id":$fid,"capacity":5000,"coop_type":"industrial"}')"
done
for i in 1 2; do
    post_entity "coop/api" "$(jq -n --arg name "Prairie Coop $i" --arg fid "$FARM2" '{"name":$name,"farm_id":$fid,"capacity":3000,"coop_type":"industrial"}')"
done
post_entity "coop/api" "$(jq -n --arg name "Valley Barn" --arg fid "$FARM3" '{"name":$name,"farm_id":$fid,"capacity":200,"coop_type":"barn"}')"
post_entity "coop/api" "$(jq -n --arg name "Riverside Barn" --arg fid "$FARM4" '{"name":$name,"farm_id":$fid,"capacity":150,"coop_type":"barn"}')"
post_entity "coop/api" "$(jq -n --arg name "Backyard Run" --arg fid "$FARM5" '{"name":$name,"farm_id":$fid,"capacity":30,"coop_type":"freerange"}')"
echo "  8 coops created"

# Get coop IDs
sleep 1
COOP_IDS=$(extract_ids "coop/graph" "getAll")
COOP_ARRAY=()
while IFS= read -r line; do COOP_ARRAY+=("$line"); done <<< "$COOP_IDS"

# Create hens (20 hens across coops)
echo "Creating hens..."
HEN_NAMES=("Henrietta" "Clucksworth" "Eggatha" "Pecky" "Featherbottom" "Nugget" "Omelet" "Scrambles" "Sunny" "Benedict" "Goldie" "Pepper" "Rosie" "Dottie" "Hazel" "Maple" "Ginger" "Butterscotch" "Cinnamon" "Daisy")
BREEDS=("Rhode Island Red" "Leghorn" "Plymouth Rock" "Australorp" "Sussex" "Wyandotte" "Orpington" "Ameraucana")

for i in "${!HEN_NAMES[@]}"; do
    if [ "$i" -lt 5 ]; then
        coop="${COOP_ARRAY[0]}"
    elif [ "$i" -lt 9 ]; then
        coop="${COOP_ARRAY[1]}"
    elif [ "$i" -lt 12 ]; then
        coop="${COOP_ARRAY[3]}"
    elif [ "$i" -lt 15 ]; then
        coop="${COOP_ARRAY[5]}"
    elif [ "$i" -lt 18 ]; then
        coop="${COOP_ARRAY[6]}"
    else
        coop="${COOP_ARRAY[7]}"
    fi
    breed_idx=$((i % ${#BREEDS[@]}))
    post_entity "hen/api" "$(jq -n --arg name "${HEN_NAMES[$i]}" --arg cid "$coop" --arg breed "${BREEDS[$breed_idx]}" '{"name":$name,"coop_id":$cid,"breed":$breed,"dob":"2024-03-15","status":"active"}')"
done
echo "  ${#HEN_NAMES[@]} hens created"

# Create containers (6 containers)
echo "Creating containers..."
post_entity "container/api" '{"name":"Central Warehouse","container_type":"warehouse","capacity":100000,"zone":"north"}'
post_entity "container/api" '{"name":"North Cold Storage","container_type":"cold_storage","capacity":50000,"zone":"north"}'
post_entity "container/api" '{"name":"East Distribution","container_type":"warehouse","capacity":50000,"zone":"east"}'
post_entity "container/api" '{"name":"Main Street Market","container_type":"market_shelf","capacity":500,"zone":"south"}'
post_entity "container/api" '{"name":"Farmers Market Stand","container_type":"market_shelf","capacity":200,"zone":"east"}'
post_entity "container/api" '{"name":"Home Fridge","container_type":"fridge","capacity":24,"zone":"south"}'
echo "  6 containers created"

# Create consumers (5 consumers)
echo "Creating consumers..."
post_entity "consumer/api" '{"name":"Downtown Bakery","consumer_type":"bakery","zone":"south","weekly_demand":200}'
post_entity "consumer/api" '{"name":"Smith Family","consumer_type":"household","zone":"south","weekly_demand":12}'
post_entity "consumer/api" '{"name":"Golden Spoon Restaurant","consumer_type":"restaurant","zone":"east","weekly_demand":500}'
post_entity "consumer/api" '{"name":"Campus Dining Hall","consumer_type":"cafeteria","zone":"north","weekly_demand":1000}'
post_entity "consumer/api" '{"name":"Corner Grocery","consumer_type":"retail","zone":"south","weekly_demand":100}'
echo "  5 consumers created"

sleep 1

# Get IDs for events
HEN_IDS=$(extract_ids "hen/graph" "getAll")
CONTAINER_IDS=$(extract_ids "container/graph" "getAll")
CONSUMER_IDS=$(extract_ids "consumer/graph" "getAll")

# Build arrays
HEN_ARRAY=()
while IFS= read -r line; do HEN_ARRAY+=("$line"); done <<< "$HEN_IDS"
CONT_ARRAY=()
while IFS= read -r line; do CONT_ARRAY+=("$line"); done <<< "$CONTAINER_IDS"
CONS_ARRAY=()
while IFS= read -r line; do CONS_ARRAY+=("$line"); done <<< "$CONSUMER_IDS"

echo ""
echo "--- Phase 2: Creating Events ---"

# Lay reports — generate multiple per hen for good data
echo "Creating lay reports..."
QUALITIES=("grade_a" "grade_a" "grade_a" "grade_b" "double_yolk")
LAY_COUNT=0

# Track per-hen and per-farm totals for projections
declare -A HEN_TOTAL_EGGS
declare -A HEN_GRADE_A
declare -A HEN_COOP
declare -A HEN_FARM
declare -A FARM_TOTAL_EGGS

for hen_idx in "${!HEN_ARRAY[@]}"; do
    hen_id="${HEN_ARRAY[$hen_idx]}"
    if [ "$hen_idx" -lt 5 ]; then
        coop="${COOP_ARRAY[0]}"; farm="$FARM1"
    elif [ "$hen_idx" -lt 9 ]; then
        coop="${COOP_ARRAY[1]}"; farm="$FARM1"
    elif [ "$hen_idx" -lt 12 ]; then
        coop="${COOP_ARRAY[3]}"; farm="$FARM2"
    elif [ "$hen_idx" -lt 15 ]; then
        coop="${COOP_ARRAY[5]}"; farm="$FARM3"
    elif [ "$hen_idx" -lt 18 ]; then
        coop="${COOP_ARRAY[6]}"; farm="$FARM4"
    else
        coop="${COOP_ARRAY[7]}"; farm="$FARM5"
    fi
    HEN_COOP[$hen_id]="$coop"
    HEN_FARM[$hen_id]="$farm"
    HEN_TOTAL_EGGS[$hen_id]=0
    HEN_GRADE_A[$hen_id]=0

    for day_offset in 0 1 2; do
        eggs=$((1 + RANDOM % 3))
        quality_idx=$((RANDOM % ${#QUALITIES[@]}))
        quality="${QUALITIES[$quality_idx]}"
        ts=$(date -u -d "-${day_offset} days" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u +%Y-%m-%dT%H:%M:%SZ)
        post_entity "lay_report/api" "$(jq -n --arg hid "$hen_id" --arg cid "$coop" --arg fid "$farm" --arg ts "$ts" --argjson eggs "$eggs" --arg q "$quality" \
            '{"hen_id":$hid,"coop_id":$cid,"farm_id":$fid,"eggs":$eggs,"timestamp":$ts,"quality":$q}')"
        HEN_TOTAL_EGGS[$hen_id]=$(( ${HEN_TOTAL_EGGS[$hen_id]} + eggs ))
        FARM_TOTAL_EGGS[$farm]=$(( ${FARM_TOTAL_EGGS[$farm]:-0} + eggs ))
        if [ "$quality" = "grade_a" ]; then
            HEN_GRADE_A[$hen_id]=$(( ${HEN_GRADE_A[$hen_id]} + eggs ))
        fi
        LAY_COUNT=$((LAY_COUNT + 1))
    done
done
echo "  $LAY_COUNT lay reports created"

# Storage deposits — track per-container totals
echo "Creating storage deposits..."
declare -A CONT_DEPOSITS
for cont_idx in 0 1 2; do
    for deposit in 1 2 3; do
        eggs=$((50 + RANDOM % 200))
        ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
        post_entity "storage_deposit/api" "$(jq -n --arg cid "${CONT_ARRAY[$cont_idx]}" --arg sid "$FARM1" --arg ts "$ts" --argjson eggs "$eggs" \
            '{"container_id":$cid,"source_type":"farm","source_id":$sid,"eggs":$eggs,"timestamp":$ts}')"
        CONT_DEPOSITS[${CONT_ARRAY[$cont_idx]}]=$(( ${CONT_DEPOSITS[${CONT_ARRAY[$cont_idx]}]:-0} + eggs ))
    done
done
echo "  9 storage deposits created"

# Container transfers
echo "Creating container transfers..."
declare -A CONT_TRANSFERS_OUT
declare -A CONT_TRANSFERS_IN
METHODS=("van" "truck" "truck" "refrigerated")
for i in 1 2 3 4; do
    src_idx=$(( (i - 1) % 3 ))
    dst_idx=$(( 3 + (i - 1) % 3 ))
    eggs=$((20 + RANDOM % 100))
    method_idx=$((RANDOM % ${#METHODS[@]}))
    ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    post_entity "container_transfer/api" "$(jq -n --arg src "${CONT_ARRAY[$src_idx]}" --arg dst "${CONT_ARRAY[$dst_idx]}" --arg ts "$ts" --argjson eggs "$eggs" --arg method "${METHODS[$method_idx]}" \
        '{"source_container_id":$src,"dest_container_id":$dst,"eggs":$eggs,"timestamp":$ts,"transport_method":$method}')"
    CONT_TRANSFERS_OUT[${CONT_ARRAY[$src_idx]}]=$(( ${CONT_TRANSFERS_OUT[${CONT_ARRAY[$src_idx]}]:-0} + eggs ))
    CONT_TRANSFERS_IN[${CONT_ARRAY[$dst_idx]}]=$(( ${CONT_TRANSFERS_IN[${CONT_ARRAY[$dst_idx]}]:-0} + eggs ))
done
echo "  4 container transfers created"

# Storage withdrawals
echo "Creating storage withdrawals..."
declare -A CONT_WITHDRAWALS
for i in 1 2; do
    eggs=$((10 + RANDOM % 30))
    ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    post_entity "storage_withdrawal/api" "$(jq -n --arg cid "${CONT_ARRAY[3]}" --arg ts "$ts" --argjson eggs "$eggs" \
        '{"container_id":$cid,"reason":"sold","eggs":$eggs,"timestamp":$ts}')"
    CONT_WITHDRAWALS[${CONT_ARRAY[3]}]=$(( ${CONT_WITHDRAWALS[${CONT_ARRAY[3]}]:-0} + eggs ))
done
echo "  2 storage withdrawals created"

# Consumption reports
echo "Creating consumption reports..."
declare -A CONT_CONSUMED
PURPOSES=("baking" "cooking" "retail" "cafeteria" "direct_sale")
for cons_idx in "${!CONS_ARRAY[@]}"; do
    for r in 1 2; do
        eggs=$((5 + RANDOM % 50))
        purpose_idx=$((cons_idx % ${#PURPOSES[@]}))
        cont_idx=$((3 + cons_idx % 3))
        ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
        post_entity "consumption_report/api" "$(jq -n --arg coid "${CONS_ARRAY[$cons_idx]}" --arg cid "${CONT_ARRAY[$cont_idx]}" --arg ts "$ts" --argjson eggs "$eggs" --arg purpose "${PURPOSES[$purpose_idx]}" \
            '{"consumer_id":$coid,"container_id":$cid,"eggs":$eggs,"timestamp":$ts,"purpose":$purpose}')"
        CONT_CONSUMED[${CONT_ARRAY[$cont_idx]}]=$(( ${CONT_CONSUMED[${CONT_ARRAY[$cont_idx]}]:-0} + eggs ))
    done
done
echo "  10 consumption reports created"

echo ""
echo "--- Phase 3: Computing and Posting Projections ---"

# Hen productivity projections
echo "Creating hen productivity projections..."
for hen_id in "${HEN_ARRAY[@]}"; do
    total=${HEN_TOTAL_EGGS[$hen_id]:-0}
    grade_a=${HEN_GRADE_A[$hen_id]:-0}
    if [ "$total" -gt 0 ]; then
        quality_rate=$(echo "scale=2; $grade_a * 100 / $total" | bc)
    else
        quality_rate="0"
    fi
    post_entity "hen_productivity/api" "$(jq -n \
        --arg hid "$hen_id" \
        --argjson eggs_today "$total" \
        --argjson total_eggs "$total" \
        --arg quality_rate "$quality_rate" \
        '{"hen_id":$hid,"eggs_today":$eggs_today,"total_eggs":$total_eggs,"quality_rate":$quality_rate}')"
done
echo "  ${#HEN_ARRAY[@]} hen productivity projections created"

# Farm output projections
echo "Creating farm output projections..."
for farm_id in "$FARM1" "$FARM2" "$FARM3" "$FARM4" "$FARM5"; do
    total=${FARM_TOTAL_EGGS[$farm_id]:-0}
    post_entity "farm_output/api" "$(jq -n \
        --arg fid "$farm_id" \
        --argjson eggs_today "$total" \
        --argjson eggs_week "$total" \
        '{"farm_id":$fid,"eggs_today":$eggs_today,"eggs_week":$eggs_week}')"
done
echo "  5 farm output projections created"

# Container inventory projections
echo "Creating container inventory projections..."
for cont_idx in "${!CONT_ARRAY[@]}"; do
    cid="${CONT_ARRAY[$cont_idx]}"
    deposits=${CONT_DEPOSITS[$cid]:-0}
    transfers_in=${CONT_TRANSFERS_IN[$cid]:-0}
    transfers_out=${CONT_TRANSFERS_OUT[$cid]:-0}
    withdrawals=${CONT_WITHDRAWALS[$cid]:-0}
    consumed=${CONT_CONSUMED[$cid]:-0}
    current=$(( deposits + transfers_in - transfers_out - withdrawals - consumed ))
    if [ "$current" -lt 0 ]; then current=0; fi

    # Get container capacity for utilization
    capacity=$(query_graphql "container/graph" "{ getById(id: \"$cid\") { capacity } }" | jq -r '.data.getById.capacity // 1')
    if [ "$capacity" -gt 0 ]; then
        utilization=$(echo "scale=1; $current * 100 / $capacity" | bc)
    else
        utilization="0"
    fi

    post_entity "container_inventory/api" "$(jq -n \
        --arg cid "$cid" \
        --argjson current_eggs "$current" \
        --argjson total_deposits "$deposits" \
        --argjson total_consumed "$consumed" \
        --arg utilization_pct "$utilization" \
        '{"container_id":$cid,"current_eggs":$current_eggs,"total_deposits":$total_deposits,"total_consumed":$total_consumed,"utilization_pct":$utilization_pct}')"
done
echo "  ${#CONT_ARRAY[@]} container inventory projections created"

echo ""
echo "--- Phase 4: Verifying ---"

echo ""
echo "Farm hierarchy:"
query_graphql "farm/graph" '{ getAll { id name farm_type zone coops { name hens { name status } } } }' | jq '.data'

echo ""
echo "Hen productivity projections:"
query_graphql "hen_productivity/graph" '{ getAll { id hen_id eggs_today total_eggs quality_rate } }' | jq '.data'

echo ""
echo "Farm output projections:"
query_graphql "farm_output/graph" '{ getAll { id farm_id eggs_today eggs_week } }' | jq '.data'

echo ""
echo "Container inventory projections:"
query_graphql "container_inventory/graph" '{ getAll { id container_id current_eggs total_deposits total_consumed utilization_pct } }' | jq '.data'

echo ""
echo "=== Seed Complete ==="
echo ""
echo "Frontend URLs (via nginx on port 8088):"
echo "  Dashboard:   http://localhost:8088/dashboard/"
echo "  Homesteader: http://localhost:8088/homestead/"
echo "  Corporate:   http://localhost:8088/corporate/"
