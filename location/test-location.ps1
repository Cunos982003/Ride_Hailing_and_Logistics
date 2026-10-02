# LocationService Manual Test (Windows PowerShell)
# Prerequisites: Redis running on localhost:6379, location service running on port 8082

$INTERNAL_KEY = "test123"
$BASE_URL = "http://localhost:8082/internal"

Write-Host "=== LocationService Manual Test ===" -ForegroundColor Green
Write-Host ""

# Generate UUIDs
$DRIVER1 = [guid]::NewGuid().ToString()
$DRIVER2 = [guid]::NewGuid().ToString()
$DRIVER3 = [guid]::NewGuid().ToString()

# Test 1: Update 3 driver locations
Write-Host "Test 1: Updating 3 driver locations..." -ForegroundColor Yellow
$body = @"
[
  {"driverId": "$DRIVER1", "latitude": 21.0295, "longitude": 105.8542},
  {"driverId": "$DRIVER2", "latitude": 21.0285, "longitude": 105.8642},
  {"driverId": "$DRIVER3", "latitude": 21.0285, "longitude": 105.7542}
]
"@

Invoke-RestMethod -Uri "$BASE_URL/locations" `
  -Method POST `
  -Headers @{"X-Internal-Key"=$INTERNAL_KEY} `
  -ContentType "application/json" `
  -Body $body

Write-Host "Driver IDs: $DRIVER1, $DRIVER2, $DRIVER3" -ForegroundColor Cyan
Write-Host ""

# Test 2: Find nearby drivers
Write-Host "Test 2: Finding nearby drivers within 5km from Hanoi center..." -ForegroundColor Yellow
$nearby = Invoke-RestMethod -Uri "$BASE_URL/drivers/nearby?latitude=21.0285&longitude=105.8542&radiusKm=5.0&limit=10" `
  -Method GET `
  -Headers @{"X-Internal-Key"=$INTERNAL_KEY}

Write-Host "Found $($nearby.Count) drivers:" -ForegroundColor Cyan
$nearby | ForEach-Object { Write-Host "  - Driver: $($_.driverId), Distance: $([math]::Round($_.distanceMeters, 2))m" }
Write-Host ""

# Test 3: Remove one driver
Write-Host "Test 3: Removing driver $DRIVER1..." -ForegroundColor Yellow
Invoke-RestMethod -Uri "$BASE_URL/drivers/$DRIVER1" `
  -Method DELETE `
  -Headers @{"X-Internal-Key"=$INTERNAL_KEY}
Write-Host "Removed successfully" -ForegroundColor Green
Write-Host ""

# Test 4: Find nearby again
Write-Host "Test 4: Finding nearby drivers after removal..." -ForegroundColor Yellow
$nearby2 = Invoke-RestMethod -Uri "$BASE_URL/drivers/nearby?latitude=21.0285&longitude=105.8542&radiusKm=5.0&limit=10" `
  -Method GET `
  -Headers @{"X-Internal-Key"=$INTERNAL_KEY}

Write-Host "Found $($nearby2.Count) drivers:" -ForegroundColor Cyan
$nearby2 | ForEach-Object { Write-Host "  - Driver: $($_.driverId), Distance: $([math]::Round($_.distanceMeters, 2))m" }
Write-Host ""

Write-Host "=== Test Complete ===" -ForegroundColor Green
Write-Host "Expected results:" -ForegroundColor Yellow
Write-Host "- Test 2 should return 2 drivers (driver1 ~1100m, driver2 ~1000m, driver3 is >5km away)"
Write-Host "- Test 4 should return 1 driver (only driver2, driver1 was removed)"
