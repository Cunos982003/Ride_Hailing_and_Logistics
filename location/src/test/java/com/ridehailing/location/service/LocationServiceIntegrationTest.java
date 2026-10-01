package com.ridehailing.location.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ridehailing.location.dto.Candidate;
import com.ridehailing.location.dto.LocationUpdate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.DisplayName;
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
@EnabledIfSystemProperty(named = "runRedisIntegrationTests", matches = "true")
@Testcontainers
class LocationServiceIntegrationTest {

  @Container
  static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
      .withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", redis::getFirstMappedPort);
  }

  @TestConfiguration
  static class TestClockConfig {
    private Instant fixedTime = Instant.now();

    @Bean
    @Primary
    public Clock testClock() {
      return Clock.fixed(fixedTime, ZoneId.of("UTC"));
    }

    public void setTime(Instant time) {
      fixedTime = time;
    }
  }

  @Autowired private LocationService locationService;
  @Autowired private StringRedisTemplate redisTemplate;

  @BeforeEach
  void cleanRedis() {
    redisTemplate.delete("drivers:geo");
    redisTemplate.delete("drivers:lastseen");
  }

  @Test
  @DisplayName("Should update 3 drivers and find nearby in correct distance order")
  void findNearby_ReturnsDriversOrderedByDistance() {
    UUID driver1 = UUID.randomUUID();
    UUID driver2 = UUID.randomUUID();
    UUID driver3 = UUID.randomUUID();

    // Hanoi center: 21.0285, 105.8542
    // Driver 1: very close (100m north)
    // Driver 2: medium (1km east)
    // Driver 3: far (5km south)
    List<LocationUpdate> updates = List.of(
        new LocationUpdate(driver1, 21.0295, 105.8542),  // ~1.1 km north
        new LocationUpdate(driver2, 21.0285, 105.8642),  // ~1 km east
        new LocationUpdate(driver3, 21.0285, 105.7542)   // ~10 km west
    );

    locationService.update(updates);

    // Search from Hanoi center within 5km
    List<Candidate> nearby = locationService.findNearby(21.0285, 105.8542, 5.0, 10);

    assertThat(nearby).hasSize(2);
    // Driver 2 should be closest, then driver 1
    assertThat(nearby.get(0).driverId()).isIn(driver1, driver2);
    assertThat(nearby.get(1).driverId()).isIn(driver1, driver2);
    assertThat(nearby.get(0).distanceMeters()).isLessThan(nearby.get(1).distanceMeters());

    // Driver 3 should not be in results (too far)
    assertThat(nearby).noneMatch(c -> c.driverId().equals(driver3));
  }

  @Test
  @DisplayName("Should ignore invalid coordinates")
  void update_InvalidCoordinates_Ignored() {
    UUID driver = UUID.randomUUID();

    List<LocationUpdate> updates = List.of(
        new LocationUpdate(driver, 91.0, 105.8542),  // invalid lat
        new LocationUpdate(driver, 21.0285, 181.0)   // invalid lng
    );

    locationService.update(updates);

    List<Candidate> nearby = locationService.findNearby(21.0285, 105.8542, 5.0, 10);
    assertThat(nearby).isEmpty();
  }

  @Test
  @DisplayName("Should remove driver from geo index")
  void removeDriver_Success() {
    UUID driver = UUID.randomUUID();

    locationService.update(List.of(
        new LocationUpdate(driver, 21.0285, 105.8542)
    ));

    List<Candidate> before = locationService.findNearby(21.0285, 105.8542, 1.0, 10);
    assertThat(before).hasSize(1);

    locationService.removeDriver(driver);

    List<Candidate> after = locationService.findNearby(21.0285, 105.8542, 1.0, 10);
    assertThat(after).isEmpty();
  }
}
