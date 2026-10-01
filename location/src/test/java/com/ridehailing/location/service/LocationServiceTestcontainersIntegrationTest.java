package com.ridehailing.location.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ridehailing.location.dto.Candidate;
import com.ridehailing.location.dto.LocationUpdate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class LocationServiceTestcontainersIntegrationTest {

  private static final String GEO_KEY = "drivers:geo";
  private static final String LASTSEEN_KEY = "drivers:lastseen";

  // Use redis:7 as requested
  @Container
  static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
      .withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", redis::getFirstMappedPort);
  }

  @TestConfiguration
  static class TestClockConfig {
    private final AdjustableClock adjustableClock = new AdjustableClock(Instant.now(), ZoneId.of("UTC"));

    @Bean
    @Primary
    public Clock testClock() {
      return adjustableClock;
    }

    @Bean
    public AdjustableClock adjustableClock() {
      return adjustableClock;
    }
  }

  static class AdjustableClock extends Clock {
    private final AtomicReference<Instant> instantRef;
    private final ZoneId zone;

    AdjustableClock(Instant initial, ZoneId zone) {
      this.instantRef = new AtomicReference<>(initial);
      this.zone = zone;
    }

    void setInstant(Instant i) {
      this.instantRef.set(i);
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return new AdjustableClock(instantRef.get(), zone);
    }

    @Override
    public Instant instant() {
      return instantRef.get();
    }
  }

  @Autowired private LocationService locationService;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private AdjustableClock adjustableClock;

  @BeforeEach
  void cleanRedis() {
    redisTemplate.delete(GEO_KEY);
    redisTemplate.delete(LASTSEEN_KEY);
  }

  // Haversine formula for distance in meters. Used to precompute expected distances.
  private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
    double R = 6371000; // earth radius meters
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  @Test
  @DisplayName("Driver at 1.99km is returned; at 2.01km is not (radius 2km)")
  void distanceBoundary_InclusionExclusion() {
    // Center: Ho Chi Minh City approximate
    double centerLat = 10.8231;
    double centerLon = 106.6297;

    // Precomputed deltas: 1 degree lat ~ 111.32 km
    // deltaLat for 1.99 km = 1.99 / 111.32 = ~0.01787 deg
    double d1 = 1.99 / 111.32;
    double d2 = 2.01 / 111.32;

    UUID d99 = UUID.randomUUID();
    UUID d201 = UUID.randomUUID();

    LocationUpdate near = new LocationUpdate(d99, centerLat + d1, centerLon);
    LocationUpdate far = new LocationUpdate(d201, centerLat + d2, centerLon);

    // Verify distances by haversine
    double distNear = haversineMeters(centerLat, centerLon, near.latitude(), near.longitude()) / 1000.0;
    double distFar = haversineMeters(centerLat, centerLon, far.latitude(), far.longitude()) / 1000.0;

    // distNear should be just under 2.0 km and distFar just over
    assertThat(distNear).isLessThan(2.0);
    assertThat(distFar).isGreaterThan(2.0);

    locationService.update(List.of(near, far));

    List<Candidate> outs = locationService.findNearby(centerLat, centerLon, 2.0, 10);

    // Should include only the near driver
    assertThat(outs).anyMatch(c -> c.driverId().equals(d99));
    assertThat(outs).noneMatch(c -> c.driverId().equals(d201));
  }

  @Test
  @DisplayName("Stale driver (20s) not returned and cleaned by job")
  void staleDriver_RemovedByJob() {
    double lat = 10.8231;
    double lon = 106.6297;

    UUID driver = UUID.randomUUID();
    locationService.update(List.of(new LocationUpdate(driver, lat, lon)));

    // Ensure present immediately
    List<Candidate> before = locationService.findNearby(lat, lon, 1.0, 10);
    assertThat(before).anyMatch(c -> c.driverId().equals(driver));

    // Advance clock by 20 seconds to make it stale (threshold is 15s in service)
    adjustableClock.setInstant(adjustableClock.instant().plusSeconds(20));

    // findNearby should filter it out
    List<Candidate> after = locationService.findNearby(lat, lon, 1.0, 10);
    assertThat(after).noneMatch(c -> c.driverId().equals(driver));

    // Run cleaning job which should remove from both keys
    locationService.cleanStaleDrivers();

    // ZSET score should be null and GEO should not return position
    Double score = redisTemplate.opsForZSet().score(LASTSEEN_KEY, driver.toString());
    assertThat(score).isNull();

    // Ensure geo also doesn't return it
    List<Candidate> afterCleanup = locationService.findNearby(lat, lon, 10.0, 10);
    assertThat(afterCleanup).noneMatch(c -> c.driverId().equals(driver));
  }

  @Test
  @DisplayName("Updating same driver overwrites old position")
  void update_OverwritePosition() {
    double centerLat = 10.8231;
    double centerLon = 106.6297;

    UUID driver = UUID.randomUUID();

    // Initial close position
    LocationUpdate p1 = new LocationUpdate(driver, centerLat, centerLon);
    locationService.update(List.of(p1));

    List<Candidate> first = locationService.findNearby(centerLat, centerLon, 0.5, 10);
    assertThat(first).anyMatch(c -> c.driverId().equals(driver));

    // Move driver far away (10 km north)
    double delta = 10.0 / 111.32;
    LocationUpdate p2 = new LocationUpdate(driver, centerLat + delta, centerLon);
    locationService.update(List.of(p2));

    // Now within small radius should not find driver
    List<Candidate> second = locationService.findNearby(centerLat, centerLon, 0.5, 10);
    assertThat(second).noneMatch(c -> c.driverId().equals(driver));

    // But within 11 km should find and distance should roughly match new point
    List<Candidate> third = locationService.findNearby(centerLat, centerLon, 11.0, 10);
    assertThat(third).anyMatch(c -> c.driverId().equals(driver));

    Candidate found = third.stream().filter(c -> c.driverId().equals(driver)).findFirst().orElseThrow();
    double expected = haversineMeters(centerLat, centerLon, p2.latitude(), p2.longitude());
    assertThat(Math.abs(found.distanceMeters() - expected)).isLessThan(200.0); // within 200m tolerance
  }

  @Test
  @DisplayName("Swapping lat/lng fails: misplaced location not in area")
  void swapLatLng_Fails() {
    // Real point in HCMC
    double realLat = 10.8231;
    double realLon = 106.6297;

    UUID driver = UUID.randomUUID();

    // Intentionally swap lat/lon
    LocationUpdate swapped = new LocationUpdate(driver, realLon, realLat);
    locationService.update(List.of(swapped));

    // The swapped coordinates should not be in the expected nearby area
    List<Candidate> outs = locationService.findNearby(realLat, realLon, 50.0, 10);
    assertThat(outs).noneMatch(c -> c.driverId().equals(driver));
  }

  @Test
  @DisplayName("Batch write 1000 points retains all members")
  void batchWrite_ThousandPoints_NoLoss() {
    double centerLat = 10.8231;
    double centerLon = 106.6297;

    int N = 1000;
    List<LocationUpdate> batch = new ArrayList<>(N);
    for (int i = 0; i < N; i++) {
      UUID id = UUID.randomUUID();
      double jitterLat = centerLat + (Math.random() - 0.5) * 0.001; // ~±55m
      double jitterLon = centerLon + (Math.random() - 0.5) * 0.001; // ~±55m
      batch.add(new LocationUpdate(id, jitterLat, jitterLon));
    }

    locationService.update(batch);

    // zCard should equal N
    Long card = redisTemplate.opsForZSet().zCard(LASTSEEN_KEY);
    assertThat(card).isEqualTo((long) N);

    // Query geo for a large radius to get all members
    List<Candidate> nearbyAll = locationService.findNearby(centerLat, centerLon, 5.0, N + 10);

    assertThat(nearbyAll).hasSize(N);
  }

  // Run tests multiple times to ensure stability
  @RepeatedTest(3)
  void stability_runThreeTimes() {
    // This test simply runs a small scenario to exercise the setup and teardown multiple times.
    double lat = 10.8231;
    double lon = 106.6297;
    UUID id = UUID.randomUUID();
    locationService.update(List.of(new LocationUpdate(id, lat, lon)));
    List<Candidate> res = locationService.findNearby(lat, lon, 1.0, 10);
    assertThat(res).anyMatch(c -> c.driverId().equals(id));
    // Clean up
    locationService.removeDriver(id);
  }
}
