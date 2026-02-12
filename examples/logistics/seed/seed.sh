#!/bin/bash
set -e

API_BASE="${API_BASE:-http://localhost:3044}"

echo "=== SwiftShip Logistics - Seed Data ==="
echo "API Base: $API_BASE"

# Wait for the API to be ready
echo "Waiting for API to be ready..."
for i in $(seq 1 30); do
    if curl -sf "$API_BASE/ready" > /dev/null 2>&1; then
        echo "API is ready!"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "ERROR: API not ready after 30 attempts"
        exit 1
    fi
    echo "  Attempt $i/30..."
    sleep 2
done

# Helper: POST to REST API (no ID returned — REST returns payload only)
post() {
    curl -sf -X POST "$API_BASE$1" \
        -H "Content-Type: application/json" \
        -d "$2" > /dev/null
}

# Helper: GraphQL query, extract a field from the first result
gql_id() {
    local endpoint="$1"
    local query="$2"
    local response
    response=$(curl -sf -X POST "$API_BASE$endpoint" \
        -H "Content-Type: application/json" \
        -d "{\"query\": \"$query\"}")
    echo "$response" | jq -r '.data | to_entries[0].value | if type == "array" then .[0].id else .id end'
}

echo ""
echo "--- Creating Warehouses ---"

post "/warehouse/api" '{"name":"SwiftShip Denver Hub","address":"4500 Logistics Pkwy","city":"Denver","state":"CO","zip":"80239","capacity":50000}'
W1=$(gql_id "/warehouse/graph" '{ getByCity(city: \"Denver\") { id name } }')
echo "Warehouse 1 (Denver): $W1"

post "/warehouse/api" '{"name":"SwiftShip Chicago Distribution Center","address":"2200 Industrial Blvd","city":"Chicago","state":"IL","zip":"60632","capacity":75000}'
W2=$(gql_id "/warehouse/graph" '{ getByCity(city: \"Chicago\") { id name } }')
echo "Warehouse 2 (Chicago): $W2"

post "/warehouse/api" '{"name":"SwiftShip Atlanta Gateway","address":"800 Peachtree Logistics Way","city":"Atlanta","state":"GA","zip":"30354","capacity":60000}'
W3=$(gql_id "/warehouse/graph" '{ getByCity(city: \"Atlanta\") { id name } }')
echo "Warehouse 3 (Atlanta): $W3"

echo ""
echo "--- Creating Shipments ---"

# Denver shipments
post "/shipment/api" "{\"destination\":\"Portland, OR\",\"carrier\":\"FedEx\",\"status\":\"delivered\",\"estimated_delivery\":\"2026-02-08\",\"warehouse_id\":\"$W1\"}"
S1=$(gql_id "/shipment/graph" "{ getByWarehouse(id: \\\"$W1\\\") { id destination } }" | head -1)
# Need to find the right one by destination
S1=$(curl -sf -X POST "$API_BASE/shipment/graph" \
    -H "Content-Type: application/json" \
    -d "{\"query\": \"{ getByWarehouse(id: \\\"$W1\\\") { id destination } }\"}" \
    | jq -r '.data.getByWarehouse[] | select(.destination == "Portland, OR") | .id')
echo "Shipment 1 (Denver→Portland, delivered): $S1"

post "/shipment/api" "{\"destination\":\"Seattle, WA\",\"carrier\":\"UPS\",\"status\":\"in_transit\",\"estimated_delivery\":\"2026-02-14\",\"warehouse_id\":\"$W1\"}"
S2=$(curl -sf -X POST "$API_BASE/shipment/graph" \
    -H "Content-Type: application/json" \
    -d "{\"query\": \"{ getByWarehouse(id: \\\"$W1\\\") { id destination } }\"}" \
    | jq -r '.data.getByWarehouse[] | select(.destination == "Seattle, WA") | .id')
echo "Shipment 2 (Denver→Seattle, in_transit): $S2"

# Chicago shipments
post "/shipment/api" "{\"destination\":\"New York, NY\",\"carrier\":\"USPS\",\"status\":\"out_for_delivery\",\"estimated_delivery\":\"2026-02-11\",\"warehouse_id\":\"$W2\"}"
S3=$(curl -sf -X POST "$API_BASE/shipment/graph" \
    -H "Content-Type: application/json" \
    -d "{\"query\": \"{ getByWarehouse(id: \\\"$W2\\\") { id destination } }\"}" \
    | jq -r '.data.getByWarehouse[] | select(.destination == "New York, NY") | .id')
echo "Shipment 3 (Chicago→NYC, out_for_delivery): $S3"

post "/shipment/api" "{\"destination\":\"Detroit, MI\",\"carrier\":\"FedEx\",\"status\":\"preparing\",\"estimated_delivery\":\"2026-02-18\",\"warehouse_id\":\"$W2\"}"
S4=$(curl -sf -X POST "$API_BASE/shipment/graph" \
    -H "Content-Type: application/json" \
    -d "{\"query\": \"{ getByWarehouse(id: \\\"$W2\\\") { id destination } }\"}" \
    | jq -r '.data.getByWarehouse[] | select(.destination == "Detroit, MI") | .id')
echo "Shipment 4 (Chicago→Detroit, preparing): $S4"

# Atlanta shipments
post "/shipment/api" "{\"destination\":\"Miami, FL\",\"carrier\":\"DHL\",\"status\":\"delivered\",\"estimated_delivery\":\"2026-02-07\",\"warehouse_id\":\"$W3\"}"
S5=$(curl -sf -X POST "$API_BASE/shipment/graph" \
    -H "Content-Type: application/json" \
    -d "{\"query\": \"{ getByWarehouse(id: \\\"$W3\\\") { id destination } }\"}" \
    | jq -r '.data.getByWarehouse[] | select(.destination == "Miami, FL") | .id')
echo "Shipment 5 (Atlanta→Miami, delivered): $S5"

post "/shipment/api" "{\"destination\":\"Nashville, TN\",\"carrier\":\"Amazon\",\"status\":\"delayed\",\"estimated_delivery\":\"2026-02-10\",\"warehouse_id\":\"$W3\"}"
S6=$(curl -sf -X POST "$API_BASE/shipment/graph" \
    -H "Content-Type: application/json" \
    -d "{\"query\": \"{ getByWarehouse(id: \\\"$W3\\\") { id destination } }\"}" \
    | jq -r '.data.getByWarehouse[] | select(.destination == "Nashville, TN") | .id')
echo "Shipment 6 (Atlanta→Nashville, delayed): $S6"

echo ""
echo "--- Creating Packages ---"

# Helper: create package and look up its ID by tracking number
create_package() {
    local tn="$1" desc="$2" weight="$3" recip="$4" addr="$5" wid="$6" sid="$7"
    post "/package/api" "{\"tracking_number\":\"$tn\",\"description\":\"$desc\",\"weight\":$weight,\"recipient\":\"$recip\",\"recipient_address\":\"$addr\",\"warehouse_id\":\"$wid\",\"shipment_id\":\"$sid\"}"
    curl -sf -X POST "$API_BASE/package/graph" \
        -H "Content-Type: application/json" \
        -d "{\"query\": \"{ getByTrackingNumber(tracking_number: \\\"$tn\\\") { id } }\"}" \
        | jq -r '.data.getByTrackingNumber.id'
}

P1=$(create_package "PKG-DEN1A001" "Ergonomic Office Chair" 35.2 "Alice Johnson" "742 Evergreen Terrace, Portland, OR 97201" "$W1" "$S1")
echo "Package 1 (PKG-DEN1A001): $P1"

P2=$(create_package "PKG-DEN1B002" "Standing Desk Converter" 42.8 "Bob Williams" "1234 Oak Ave, Portland, OR 97205" "$W1" "$S1")
echo "Package 2 (PKG-DEN1B002): $P2"

P3=$(create_package "PKG-DEN2C003" "4K Monitor 27-inch" 18.5 "Carol Martinez" "5678 Pine St, Seattle, WA 98101" "$W1" "$S2")
echo "Package 3 (PKG-DEN2C003): $P3"

P4=$(create_package "PKG-DEN2D004" "Mechanical Keyboard Bundle" 3.2 "David Chen" "910 Maple Dr, Seattle, WA 98103" "$W1" "$S2")
echo "Package 4 (PKG-DEN2D004): $P4"

P5=$(create_package "PKG-CHI3E005" "Wireless Noise-Canceling Headphones" 0.8 "Emma Davis" "350 5th Ave, New York, NY 10118" "$W2" "$S3")
echo "Package 5 (PKG-CHI3E005): $P5"

P6=$(create_package "PKG-CHI3F006" "Running Shoes Size 10" 1.5 "Frank Miller" "200 Broadway, New York, NY 10007" "$W2" "$S3")
echo "Package 6 (PKG-CHI3F006): $P6"

P7=$(create_package "PKG-CHI4G007" "Espresso Machine" 22.0 "Grace Thompson" "1500 Woodward Ave, Detroit, MI 48226" "$W2" "$S4")
echo "Package 7 (PKG-CHI4G007): $P7"

P8=$(create_package "PKG-CHI4H008" "Yoga Mat and Block Set" 4.5 "Henry Park" "2700 Michigan Ave, Detroit, MI 48216" "$W2" "$S4")
echo "Package 8 (PKG-CHI4H008): $P8"

P9=$(create_package "PKG-ATL5I009" "Beach Umbrella and Chair Set" 12.3 "Isabella Rodriguez" "100 Ocean Dr, Miami, FL 33139" "$W3" "$S5")
echo "Package 9 (PKG-ATL5I009): $P9"

P10=$(create_package "PKG-ATL5J010" "Portable Bluetooth Speaker" 2.1 "James Wilson" "450 Brickell Ave, Miami, FL 33131" "$W3" "$S5")
echo "Package 10 (PKG-ATL5J010): $P10"

P11=$(create_package "PKG-ATL6K011" "Acoustic Guitar with Case" 8.7 "Karen Lee" "300 Broadway, Nashville, TN 37201" "$W3" "$S6")
echo "Package 11 (PKG-ATL6K011): $P11"

P12=$(create_package "PKG-ATL6L012" "Vinyl Record Collection (Box Set)" 6.4 "Leo Brown" "500 Music Row, Nashville, TN 37203" "$W3" "$S6")
echo "Package 12 (PKG-ATL6L012): $P12"

echo ""
echo "--- Creating Tracking Updates ---"

# Helper for tracking updates
track() {
    local pkg_id="$1" status="$2" location="$3" timestamp="$4" notes="$5"
    post "/tracking_update/api" "{\"package_id\":\"$pkg_id\",\"status\":\"$status\",\"location\":\"$location\",\"timestamp\":\"$timestamp\",\"notes\":\"$notes\"}"
    echo "  [$status] $location - $notes"
}

# Package 1 - Full delivered lifecycle
echo "Package PKG-DEN1A001 (delivered):"
track "$P1" "label_created"       "Denver, CO"            "2026-02-03T08:00:00Z" "Shipping label created"
track "$P1" "picked_up"           "Denver, CO"            "2026-02-03T14:30:00Z" "Package picked up by FedEx"
track "$P1" "arrived_at_facility" "Denver Hub, CO"        "2026-02-04T06:00:00Z" "Arrived at Denver sorting facility"
track "$P1" "departed_facility"   "Denver Hub, CO"        "2026-02-04T22:00:00Z" "Departed Denver facility"
track "$P1" "in_transit"          "Salt Lake City, UT"    "2026-02-05T10:00:00Z" "In transit through Salt Lake City"
track "$P1" "arrived_at_facility" "Portland Hub, OR"      "2026-02-06T16:00:00Z" "Arrived at Portland facility"
track "$P1" "out_for_delivery"    "Portland, OR"          "2026-02-07T07:30:00Z" "Out for delivery"
track "$P1" "delivered"           "Portland, OR"          "2026-02-07T14:15:00Z" "Delivered - signed by A. Johnson"

# Package 2 - Full delivered lifecycle
echo "Package PKG-DEN1B002 (delivered):"
track "$P2" "label_created"       "Denver, CO"            "2026-02-03T08:15:00Z" "Shipping label created"
track "$P2" "picked_up"           "Denver, CO"            "2026-02-03T14:30:00Z" "Package picked up by FedEx"
track "$P2" "arrived_at_facility" "Denver Hub, CO"        "2026-02-04T06:00:00Z" "Arrived at Denver sorting facility"
track "$P2" "departed_facility"   "Denver Hub, CO"        "2026-02-04T22:00:00Z" "Departed Denver facility"
track "$P2" "in_transit"          "Salt Lake City, UT"    "2026-02-05T10:30:00Z" "In transit through Salt Lake City"
track "$P2" "arrived_at_facility" "Portland Hub, OR"      "2026-02-06T16:30:00Z" "Arrived at Portland facility"
track "$P2" "out_for_delivery"    "Portland, OR"          "2026-02-07T08:00:00Z" "Out for delivery"
track "$P2" "delivered"           "Portland, OR"          "2026-02-07T15:45:00Z" "Delivered - left at front door"

# Package 3 - In transit
echo "Package PKG-DEN2C003 (in_transit):"
track "$P3" "label_created"       "Denver, CO"            "2026-02-09T09:00:00Z" "Shipping label created"
track "$P3" "picked_up"           "Denver, CO"            "2026-02-09T16:00:00Z" "Package picked up by UPS"
track "$P3" "arrived_at_facility" "Denver Hub, CO"        "2026-02-10T05:00:00Z" "Arrived at Denver sorting facility"
track "$P3" "departed_facility"   "Denver Hub, CO"        "2026-02-10T20:00:00Z" "Departed Denver facility"
track "$P3" "in_transit"          "Boise, ID"             "2026-02-11T08:00:00Z" "In transit through Boise"

# Package 4 - In transit
echo "Package PKG-DEN2D004 (in_transit):"
track "$P4" "label_created"       "Denver, CO"            "2026-02-09T09:15:00Z" "Shipping label created"
track "$P4" "picked_up"           "Denver, CO"            "2026-02-09T16:00:00Z" "Package picked up by UPS"
track "$P4" "arrived_at_facility" "Denver Hub, CO"        "2026-02-10T05:00:00Z" "Arrived at Denver sorting facility"
track "$P4" "departed_facility"   "Denver Hub, CO"        "2026-02-10T20:00:00Z" "Departed Denver facility"

# Package 5 - Out for delivery
echo "Package PKG-CHI3E005 (out_for_delivery):"
track "$P5" "label_created"       "Chicago, IL"           "2026-02-08T10:00:00Z" "Shipping label created"
track "$P5" "picked_up"           "Chicago, IL"           "2026-02-08T15:00:00Z" "Package picked up by USPS"
track "$P5" "arrived_at_facility" "Chicago Hub, IL"       "2026-02-09T04:00:00Z" "Arrived at Chicago sorting facility"
track "$P5" "departed_facility"   "Chicago Hub, IL"       "2026-02-09T18:00:00Z" "Departed Chicago facility"
track "$P5" "in_transit"          "Cleveland, OH"         "2026-02-10T06:00:00Z" "In transit through Cleveland"
track "$P5" "arrived_at_facility" "NYC Distribution, NY"  "2026-02-10T22:00:00Z" "Arrived at NYC distribution center"
track "$P5" "out_for_delivery"    "New York, NY"          "2026-02-11T07:00:00Z" "Out for delivery"

# Package 6 - Out for delivery
echo "Package PKG-CHI3F006 (out_for_delivery):"
track "$P6" "label_created"       "Chicago, IL"           "2026-02-08T10:30:00Z" "Shipping label created"
track "$P6" "picked_up"           "Chicago, IL"           "2026-02-08T15:00:00Z" "Package picked up by USPS"
track "$P6" "arrived_at_facility" "Chicago Hub, IL"       "2026-02-09T04:00:00Z" "Arrived at Chicago sorting facility"
track "$P6" "departed_facility"   "Chicago Hub, IL"       "2026-02-09T18:00:00Z" "Departed Chicago facility"
track "$P6" "arrived_at_facility" "NYC Distribution, NY"  "2026-02-10T22:30:00Z" "Arrived at NYC distribution center"
track "$P6" "out_for_delivery"    "New York, NY"          "2026-02-11T07:30:00Z" "Out for delivery"

# Package 7 - Label created (preparing)
echo "Package PKG-CHI4G007 (label_created):"
track "$P7" "label_created"       "Chicago, IL"           "2026-02-11T09:00:00Z" "Shipping label created"

# Package 8 - Label created (preparing)
echo "Package PKG-CHI4H008 (label_created):"
track "$P8" "label_created"       "Chicago, IL"           "2026-02-11T09:15:00Z" "Shipping label created"

# Package 9 - Full delivered lifecycle
echo "Package PKG-ATL5I009 (delivered):"
track "$P9"  "label_created"       "Atlanta, GA"           "2026-02-02T08:00:00Z" "Shipping label created"
track "$P9"  "picked_up"           "Atlanta, GA"           "2026-02-02T13:00:00Z" "Package picked up by DHL"
track "$P9"  "arrived_at_facility" "Atlanta Hub, GA"       "2026-02-03T05:00:00Z" "Arrived at Atlanta sorting facility"
track "$P9"  "departed_facility"   "Atlanta Hub, GA"       "2026-02-03T19:00:00Z" "Departed Atlanta facility"
track "$P9"  "in_transit"          "Jacksonville, FL"      "2026-02-04T12:00:00Z" "In transit through Jacksonville"
track "$P9"  "arrived_at_facility" "Miami Hub, FL"         "2026-02-05T08:00:00Z" "Arrived at Miami facility"
track "$P9"  "out_for_delivery"    "Miami, FL"             "2026-02-06T07:00:00Z" "Out for delivery"
track "$P9"  "delivered"           "Miami, FL"             "2026-02-06T13:20:00Z" "Delivered - left with concierge"

# Package 10 - Full delivered lifecycle
echo "Package PKG-ATL5J010 (delivered):"
track "$P10" "label_created"       "Atlanta, GA"           "2026-02-02T08:30:00Z" "Shipping label created"
track "$P10" "picked_up"           "Atlanta, GA"           "2026-02-02T13:00:00Z" "Package picked up by DHL"
track "$P10" "arrived_at_facility" "Atlanta Hub, GA"       "2026-02-03T05:00:00Z" "Arrived at Atlanta sorting facility"
track "$P10" "departed_facility"   "Atlanta Hub, GA"       "2026-02-03T19:00:00Z" "Departed Atlanta facility"
track "$P10" "in_transit"          "Jacksonville, FL"      "2026-02-04T12:30:00Z" "In transit through Jacksonville"
track "$P10" "arrived_at_facility" "Miami Hub, FL"         "2026-02-05T08:30:00Z" "Arrived at Miami facility"
track "$P10" "out_for_delivery"    "Miami, FL"             "2026-02-06T07:30:00Z" "Out for delivery"
track "$P10" "delivered"           "Miami, FL"             "2026-02-06T11:45:00Z" "Delivered - signed by J. Wilson"

# Package 11 - Delayed with exception
echo "Package PKG-ATL6K011 (delayed/exception):"
track "$P11" "label_created"       "Atlanta, GA"           "2026-02-06T10:00:00Z" "Shipping label created"
track "$P11" "picked_up"           "Atlanta, GA"           "2026-02-06T15:00:00Z" "Package picked up by Amazon"
track "$P11" "arrived_at_facility" "Atlanta Hub, GA"       "2026-02-07T04:00:00Z" "Arrived at Atlanta sorting facility"
track "$P11" "departed_facility"   "Atlanta Hub, GA"       "2026-02-07T18:00:00Z" "Departed Atlanta facility"
track "$P11" "in_transit"          "Chattanooga, TN"       "2026-02-08T10:00:00Z" "In transit through Chattanooga"
track "$P11" "exception"           "Chattanooga, TN"       "2026-02-08T14:00:00Z" "Severe weather delay - tornado warning"
track "$P11" "in_transit"          "Chattanooga, TN"       "2026-02-10T08:00:00Z" "Resumed transit after weather delay"

# Package 12 - Delayed with delivery attempt
echo "Package PKG-ATL6L012 (delayed/delivery_attempted):"
track "$P12" "label_created"       "Atlanta, GA"           "2026-02-06T10:30:00Z" "Shipping label created"
track "$P12" "picked_up"           "Atlanta, GA"           "2026-02-06T15:00:00Z" "Package picked up by Amazon"
track "$P12" "arrived_at_facility" "Atlanta Hub, GA"       "2026-02-07T04:00:00Z" "Arrived at Atlanta sorting facility"
track "$P12" "departed_facility"   "Atlanta Hub, GA"       "2026-02-07T18:00:00Z" "Departed Atlanta facility"
track "$P12" "arrived_at_facility" "Nashville Hub, TN"     "2026-02-08T22:00:00Z" "Arrived at Nashville facility"
track "$P12" "out_for_delivery"    "Nashville, TN"         "2026-02-09T07:00:00Z" "Out for delivery"
track "$P12" "delivery_attempted"  "Nashville, TN"         "2026-02-09T14:00:00Z" "Delivery attempted - no one home"
track "$P12" "out_for_delivery"    "Nashville, TN"         "2026-02-10T07:30:00Z" "Out for second delivery attempt"
track "$P12" "delivery_attempted"  "Nashville, TN"         "2026-02-10T15:00:00Z" "Delivery attempted - business closed"

echo ""
echo "=== Seed Data Complete ==="
echo ""
echo "Demo tracking numbers to try:"
echo "  PKG-DEN1A001 - Delivered (full lifecycle)"
echo "  PKG-DEN2C003 - In Transit"
echo "  PKG-CHI3E005 - Out for Delivery"
echo "  PKG-CHI4G007 - Label Created"
echo "  PKG-ATL5I009 - Delivered"
echo "  PKG-ATL6K011 - Delayed (weather exception)"
echo "  PKG-ATL6L012 - Delayed (delivery attempted)"
echo ""
echo "Totals: 3 warehouses, 6 shipments, 12 packages, tracking updates created"
