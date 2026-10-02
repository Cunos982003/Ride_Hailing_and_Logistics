#!/bin/bash
# Manual test script for LocationService
# Prerequisites: Redis running on localhost:6379, location service running on port 8082

INTERNAL_KEY="test123"
BASE_URL="http://localhost:8082/internal"

echo "=== LocationService Manual Test ==="
echo ""

# Test 1: Update 3 driver locations
echo "Test 1: Updating 3 driver locations..."
DRIVER1=$(uuidgen)
DRIVER2=$(uuidgen)
DRIVER3=$(uuidgen)

curl -X POST "$BASE_URL/locations" \
  -H "X-Internal-Key: $INTERNAL_KEY" \
  -H "Content-Type: application/json" \
  -d "[
    {\"driverId\": \"$DRIVER1\", \"latitude\": 21.0295, \"longitude\": 105.8542},
    {\"driverId\": \"$DRIVER2\", \"latitude\": 21.0285, \"longitude\": 105.8642},
    {\"driverId\": \"$DRIVER3\", \"latitude\": 21.0285, \"longitude\": 105.7542}
  ]"
echo ""
echo "Driver IDs: $DRIVER1, $DRIVER2, $DRIVER3"
echo ""

# Test 2: Find nearby drivers from Hanoi center (21.0285, 105.8542) within 5km
echo "Test 2: Finding nearby drivers within 5km..."
curl -X GET "$BASE_URL/drivers/nearby?latitude=21.0285&longitude=105.8542&radiusKm=5.0&limit=10" \
  -H "X-Internal-Key: $INTERNAL_KEY"
echo ""
echo ""

# Test 3: Remove one driver
echo "Test 3: Removing driver $DRIVER1..."
curl -X DELETE "$BASE_URL/drivers/$DRIVER1" \
  -H "X-Internal-Key: $INTERNAL_KEY"
echo ""
echo ""

# Test 4: Find nearby again (should only return 1 driver now, driver3 is too far)
echo "Test 4: Finding nearby drivers after removal..."
curl -X GET "$BASE_URL/drivers/nearby?latitude=21.0285&longitude=105.8542&radiusKm=5.0&limit=10" \
  -H "X-Internal-Key: $INTERNAL_KEY"
echo ""
echo ""

echo "=== Test Complete ==="
echo "Expected results:"
echo "- Test 2 should return 2 drivers (driver1 and driver2, driver3 is >5km away)"
echo "- Test 4 should return 1 driver (only driver2, driver1 was removed, driver3 is too far)"
