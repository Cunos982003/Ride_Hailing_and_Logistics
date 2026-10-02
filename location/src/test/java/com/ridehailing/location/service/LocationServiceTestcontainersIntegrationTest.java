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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LocationServiceTestcontainersIntegrationTest {

  private static final String GEO_KEY = "drivers:geo";
  private static final String LASTSEEN_KEY = "drivers:lastseen";

  // Sử dụng GenericContainer redis:7 theo yêu cầu (KHÔNG mock Redis)
  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    registry.add("spring.data.redis.timeout", () -> "1000ms");
  }

  @TestConfiguration
  static class TestClockConfig {
    private final AdjustableClock adjustableClock =
        new AdjustableClock(Instant.now(), ZoneId.of("UTC"));

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

  // Clock giả lập có thể chủ động điều chỉnh thời gian phục vụ kiểm thử
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
    adjustableClock.setInstant(Instant.now());
  }

  /**
   * Công thức Haversine tính khoảng cách theo đường trắc địa trên mặt cầu (mét): R = 6,371,000 m
   * (bán kính Trái Đất trung bình chuẩn WGS-84/Redis). d = 2 * R * asin(sqrt(sin^2(Δlat/2) +
   * cos(lat1)*cos(lat2)*sin^2(Δlon/2)))
   */
  private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
    double R = 6371000.0; // mét
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  // =========================================================================
  // CA 1: Tài xế ở 1,99 km được trả về, ở 2,01 km thì không (bán kính 2 km)
  // =========================================================================
  /**
   * CÁCH TÍNH KHOẢNG CÁCH TỌA ĐỘ CỐ ĐỊNH TÍNH TRƯỚC: - Tâm tìm kiếm: Trung tâm Hà Nội - Hồ Hoàn
   * Kiếm (lat = 21.0285, lon = 105.8542). - Giữ nguyên kinh độ lon, di chuyển dọc theo kinh tuyến
   * (meridian): Khoảng cách d = R * Δlat (rad) = R * (Δlat_deg * π / 180). => Δlat_deg = (d / R) *
   * (180 / π). Với R = 6,371,000 mét: + d1 = 1,990 mét (1.99 km): Δlat1 = (1990 / 6371000) * (180 /
   * π) = 0.017897103 độ. => lat_near = 21.0285 + 0.017897103 = 21.0463971. Khoảng cách Haversine
   * tính lại: chính xác 1990.0 mét (< 2000m). + d2 = 2,010 mét (2.01 km): Δlat2 = (2010 / 6371000)
   * * (180 / π) = 0.018076985 độ. => lat_far = 21.0285 + 0.018076985 = 21.0465770. Khoảng cách
   * Haversine tính lại: chính xác 2010.0 mét (> 2000m).
   */
  @Test
  @DisplayName("Ca 1: Tài xế ở 1.99 km được trả về, ở 2.01 km thì không (bán kính 2 km)")
  void distanceBoundary_InclusionExclusion() {
    double centerLat = 21.0285;
    double centerLon = 105.8542;

    // Tọa độ cố định tính trước theo công thức cung tròn kinh tuyến tại Hà Nội
    double deltaLat199 = (1990.0 / 6371000.0) * (180.0 / Math.PI); // ~0.0178971
    double deltaLat201 = (2010.0 / 6371000.0) * (180.0 / Math.PI); // ~0.0180770

    double latNear = centerLat + deltaLat199;
    double latFar = centerLat + deltaLat201;

    // Kiểm tra độ chính xác của khoảng cách đã tính trước bằng công thức Haversine
    double actualDistNear = haversineMeters(centerLat, centerLon, latNear, centerLon);
    double actualDistFar = haversineMeters(centerLat, centerLon, latFar, centerLon);

    assertThat(actualDistNear).isLessThan(2000.0);
    assertThat(actualDistFar).isGreaterThan(2000.0);

    UUID driverNear = UUID.randomUUID();
    UUID driverFar = UUID.randomUUID();

    LocationUpdate nearUpdate = new LocationUpdate(driverNear, latNear, centerLon);
    LocationUpdate farUpdate = new LocationUpdate(driverFar, latFar, centerLon);

    // Ghi vị trí 2 tài xế vào Redis
    locationService.update(List.of(nearUpdate, farUpdate));

    // Tìm kiếm trong bán kính 2.0 km (2000 mét) từ tâm Hà Nội
    List<Candidate> candidates = locationService.findNearby(centerLat, centerLon, 2.0, 10);

    // Tài xế ở 1.99 km ĐƯỢC trả về
    assertThat(candidates).anyMatch(c -> c.driverId().equals(driverNear));

    // Tài xế ở 2.01 km KHÔNG được trả về
    assertThat(candidates).noneMatch(c -> c.driverId().equals(driverFar));
  }

  // =========================================================================
  // CA 2: Tài xế ngừng gửi 20s (Clock giả) không xuất hiện và bị xóa khỏi 2 key
  // =========================================================================
  @Test
  @DisplayName(
      "Ca 2: Tài xế ngừng gửi 20 giây (dùng Clock giả) thì không xuất hiện trong findNearby và bị job xóa khỏi cả hai key")
  void staleDriver_filteredAndRemovedFromBothKeys() {
    double lat = 21.0285;
    double lon = 105.8542;
    UUID driverId = UUID.randomUUID();

    // 1) Ghi vị trí ban đầu tại thời điểm t0 tại Hà Nội
    locationService.update(List.of(new LocationUpdate(driverId, lat, lon)));

    // Xác nhận tài xế xuất hiện ngay lập tức
    List<Candidate> initial = locationService.findNearby(lat, lon, 1.0, 10);
    assertThat(initial).anyMatch(c -> c.driverId().equals(driverId));

    // 2) Tua đồng hồ giả thêm 20 giây (vượt ngưỡng 15 giây quy định)
    adjustableClock.setInstant(adjustableClock.instant().plusSeconds(20));

    // findNearby phải LỌC BỎ tài xế quá hạn này
    List<Candidate> afterStale = locationService.findNearby(lat, lon, 1.0, 10);
    assertThat(afterStale).noneMatch(c -> c.driverId().equals(driverId));

    // 3) Thực thi job dọn dẹp @Scheduled(fixedDelay = 5000)
    locationService.cleanStaleDrivers();

    // Kiểm tra trực tiếp Redis: đã bị xóa khỏi drivers:lastseen
    Double lastSeenScore = redisTemplate.opsForZSet().score(LASTSEEN_KEY, driverId.toString());
    assertThat(lastSeenScore).isNull();

    // Kiểm tra trực tiếp Redis: đã bị xóa khỏi drivers:geo
    List<Point> geoPosition = redisTemplate.opsForGeo().position(GEO_KEY, driverId.toString());
    assertThat(geoPosition == null || geoPosition.isEmpty() || geoPosition.get(0) == null).isTrue();

    // Sau khi dọn dẹp, truy vấn với bán kính lớn vẫn không còn
    List<Candidate> afterCleanup = locationService.findNearby(lat, lon, 10.0, 10);
    assertThat(afterCleanup).noneMatch(c -> c.driverId().equals(driverId));
  }

  // =========================================================================
  // CA 3: Gửi vị trí mới của cùng tài xế thì vị trí cũ bị ghi đè
  // =========================================================================
  @Test
  @DisplayName("Ca 3: Gửi vị trí mới của cùng tài xế thì vị trí cũ bị ghi đè")
  void update_overwritesOldPosition() {
    double centerLat = 21.0285;
    double centerLon = 105.8542;
    UUID driverId = UUID.randomUUID();

    // Vị trí cũ: Tại trung tâm Hà Nội (Hồ Hoàn Kiếm)
    LocationUpdate posOld = new LocationUpdate(driverId, centerLat, centerLon);
    locationService.update(List.of(posOld));

    List<Candidate> foundOld = locationService.findNearby(centerLat, centerLon, 0.5, 10);
    assertThat(foundOld).anyMatch(c -> c.driverId().equals(driverId));

    // Vị trí mới: Cách 10 km về phía Bắc Hà Nội (khu vực Hồ Tây / Đông Anh)
    double deltaNorth = (10000.0 / 6371000.0) * (180.0 / Math.PI); // ~0.0899 độ
    LocationUpdate posNew = new LocationUpdate(driverId, centerLat + deltaNorth, centerLon);
    locationService.update(List.of(posNew));

    // 1) Tìm ở vị trí cũ (bán kính nhỏ 500m) -> KHÔNG còn thấy tài xế (vị trí cũ đã bị ghi đè)
    List<Candidate> atOldPos = locationService.findNearby(centerLat, centerLon, 0.5, 10);
    assertThat(atOldPos).noneMatch(c -> c.driverId().equals(driverId));

    // 2) Tìm ở vị trí mới (bán kính 1km) -> Thấy tài xế ở vị trí mới
    List<Candidate> atNewPos =
        locationService.findNearby(posNew.latitude(), posNew.longitude(), 1.0, 10);
    assertThat(atNewPos).anyMatch(c -> c.driverId().equals(driverId));

    // 3) drivers:lastseen chỉ có duy nhất 1 phần tử cho tài xế này (không bị nhân đôi)
    Long card = redisTemplate.opsForZSet().zCard(LASTSEEN_KEY);
    assertThat(card).isEqualTo(1L);
  }

  // =========================================================================
  // CA 4: Đảo nhầm lat/lng sẽ thất bại test (vị trí thực Hà Nội)
  // =========================================================================
  @Test
  @DisplayName(
      "Ca 4: Đảo nhầm lat/lng sẽ thất bại test (đặt vị trí thực ở Hà Nội, khẳng định kết quả nằm đúng khu vực)")
  void swapLatLng_failsAndAssertsCorrectRegion() {
    // Vị trí thực tế tại trung tâm Hà Nội (Hồ Hoàn Kiếm / Nhà Hát Lớn Hà Nội): Vĩ độ ~21.0285° N,
    // Kinh độ ~105.8542° E
    double hanoiLat = 21.0285;
    double hanoiLng = 105.8542;

    UUID correctDriver = UUID.randomUUID();
    UUID swappedDriver = UUID.randomUUID();

    // 1) Tài xế đặt đúng vị trí thực ở Hà Nội
    LocationUpdate correctUpdate = new LocationUpdate(correctDriver, hanoiLat, hanoiLng);
    locationService.update(List.of(correctUpdate));

    // Khẳng định kết quả nằm đúng khu vực Hà Nội: tìm thấy trong bán kính 1km với sai số < 50m
    List<Candidate> foundInHanoi = locationService.findNearby(hanoiLat, hanoiLng, 1.0, 10);
    assertThat(foundInHanoi).isNotEmpty();
    Candidate matched =
        foundInHanoi.stream()
            .filter(c -> c.driverId().equals(correctDriver))
            .findFirst()
            .orElseThrow();
    assertThat(matched.distanceMeters()).isLessThan(50.0);

    // 2) Đảo nhầm lat/lng: lat = 105.8542, lng = 21.0285
    // Vĩ độ 105.8542 > 90° là bất hợp lệ theo chuẩn địa lý -> validator phải từ chối
    LocationUpdate swappedUpdate = new LocationUpdate(swappedDriver, hanoiLng, hanoiLat);
    locationService.update(List.of(swappedUpdate));

    // Khẳng định tài xế bị đảo nhầm lat/lng KHÔNG THỂ xuất hiện trong khu vực Hà Nội
    List<Candidate> checkHanoi = locationService.findNearby(hanoiLat, hanoiLng, 50.0, 10);
    assertThat(checkHanoi).noneMatch(c -> c.driverId().equals(swappedDriver));
  }

  // =========================================================================
  // CA 5: Ghi lô 1000 điểm không mất điểm nào
  // =========================================================================
  @Test
  @DisplayName("Ca 5: Ghi lô 1000 điểm không mất điểm nào")
  void batchWrite_thousandPoints_noLoss() {
    double centerLat = 21.0285;
    double centerLon = 105.8542;

    int totalPoints = 1000;
    List<LocationUpdate> batch = new ArrayList<>(totalPoints);
    List<UUID> driverIds = new ArrayList<>(totalPoints);

    // Tạo 1000 điểm ngẫu nhiên xung quanh khu vực Hà Nội trong bán kính ~1km
    for (int i = 0; i < totalPoints; i++) {
      UUID id = UUID.randomUUID();
      driverIds.add(id);

      // Dao động nhỏ ±0.005 độ (~±550 mét)
      double jitterLat = centerLat + (Math.random() - 0.5) * 0.01;
      double jitterLon = centerLon + (Math.random() - 0.5) * 0.01;

      batch.add(new LocationUpdate(id, jitterLat, jitterLon));
    }

    // Ghi lô 1000 điểm dùng executePipelined
    locationService.update(batch);

    // 1) Kiểm tra số lượng trong ZSET (drivers:lastseen) phải đủ đúng 1000
    Long lastseenCount = redisTemplate.opsForZSet().zCard(LASTSEEN_KEY);
    assertThat(lastseenCount).isEqualTo((long) totalPoints);

    // 2) Truy vấn GEOSEARCH trong bán kính 10km lấy tối đa 1500 điểm từ trung tâm Hà Nội
    List<Candidate> nearby =
        locationService.findNearby(centerLat, centerLon, 10.0, totalPoints + 100);

    // Đủ toàn bộ 1000 tài xế, không mất điểm nào
    assertThat(nearby).hasSize(totalPoints);

    // Mọi driverId đã sinh đều có trong danh sách kết quả
    Set<UUID> returnedIds =
        nearby.stream().map(Candidate::driverId).collect(java.util.stream.Collectors.toSet());
    assertThat(returnedIds).containsAll(driverIds);
  }
}
