package com.ridehailing.location.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.location.dto.Candidate;
import com.ridehailing.location.dto.LocationUpdate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LocationControllerIntegrationTest {

  private static final String INTERNAL_KEY = "secret";
  private static final String GEO_KEY = "drivers:geo";
  private static final String LASTSEEN_KEY = "drivers:lastseen";

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    registry.add("app.internal-key", () -> INTERNAL_KEY);
    registry.add("internal.key", () -> INTERNAL_KEY);
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

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private AdjustableClock adjustableClock;

  @BeforeEach
  void cleanRedis() {
    redisTemplate.delete(GEO_KEY);
    redisTemplate.delete(LASTSEEN_KEY);
    adjustableClock.setInstant(Instant.now());
  }

  @Test
  @DisplayName("Tiêu chí xong: Gọi API ghi 3 tài xế rồi nearby trả đúng thứ tự gần -> xa")
  void api_recordThreeDrivers_thenFindNearby_returnsInOrderNearToFar() throws Exception {
    // Tọa độ trung tâm: Hồ Hoàn Kiếm, Hà Nội (21.0285, 105.8542)
    double centerLat = 21.0285;
    double centerLng = 105.8542;

    UUID nearDriverId = UUID.randomUUID(); // Gần nhất (~300m)
    UUID midDriverId = UUID.randomUUID(); // Trung bình (~1.1km)
    UUID farDriverId = UUID.randomUUID(); // Xa nhất (~3.5km)

    // 1 độ vĩ tuyến ~ 111.32 km -> 0.003 độ ~ 330m, 0.010 độ ~ 1.11km, 0.030 độ ~ 3.34km
    List<LocationUpdate> updates =
        List.of(
            new LocationUpdate(farDriverId, centerLat + 0.030, centerLng),
            new LocationUpdate(nearDriverId, centerLat + 0.003, centerLng),
            new LocationUpdate(midDriverId, centerLat + 0.010, centerLng));

    // 1) POST /internal/locations ghi mảng vị trí 3 tài xế
    mockMvc
        .perform(
            post("/internal/locations")
                .header("X-Internal-Key", INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates)))
        .andExpect(status().isOk());

    // 2) GET /internal/drivers/nearby tìm trong bán kính 5km
    MvcResult result =
        mockMvc
            .perform(
                get("/internal/drivers/nearby")
                    .header("X-Internal-Key", INTERNAL_KEY)
                    .param("latitude", String.valueOf(centerLat))
                    .param("longitude", String.valueOf(centerLng))
                    .param("radiusKm", "5.0")
                    .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();

    List<Candidate> candidates =
        objectMapper.readValue(
            result.getResponse().getContentAsString(), new TypeReference<List<Candidate>>() {});

    // Kiểm tra kết quả
    assertThat(candidates).hasSize(3);

    // Thứ tự phải là: nearDriverId -> midDriverId -> farDriverId (gần -> xa)
    assertThat(candidates.get(0).driverId()).isEqualTo(nearDriverId);
    assertThat(candidates.get(1).driverId()).isEqualTo(midDriverId);
    assertThat(candidates.get(2).driverId()).isEqualTo(farDriverId);

    // Khoảng cách tăng dần
    assertThat(candidates.get(0).distanceMeters()).isLessThan(candidates.get(1).distanceMeters());
    assertThat(candidates.get(1).distanceMeters()).isLessThan(candidates.get(2).distanceMeters());
  }

  @Test
  @DisplayName("Lọc bỏ tài xế có lastseen quá 15 giây trước")
  void api_findNearby_filtersOutStaleDriversAfter15Seconds() throws Exception {
    double centerLat = 21.0285;
    double centerLng = 105.8542;

    UUID freshDriverId = UUID.randomUUID();
    UUID staleDriverId = UUID.randomUUID();

    // Ghi stale driver trước
    mockMvc
        .perform(
            post("/internal/locations")
                .header("X-Internal-Key", INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        List.of(new LocationUpdate(staleDriverId, centerLat + 0.005, centerLng)))))
        .andExpect(status().isOk());

    // Chỉnh giờ tăng 20 giây (quá 15 giây)
    adjustableClock.setInstant(adjustableClock.instant().plusSeconds(20));

    // Ghi fresh driver ở thời điểm mới
    mockMvc
        .perform(
            post("/internal/locations")
                .header("X-Internal-Key", INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        List.of(new LocationUpdate(freshDriverId, centerLat + 0.003, centerLng)))))
        .andExpect(status().isOk());

    // GET /internal/drivers/nearby -> chỉ trả về freshDriverId
    MvcResult result =
        mockMvc
            .perform(
                get("/internal/drivers/nearby")
                    .header("X-Internal-Key", INTERNAL_KEY)
                    .param("latitude", String.valueOf(centerLat))
                    .param("longitude", String.valueOf(centerLng))
                    .param("radiusKm", "5.0"))
            .andExpect(status().isOk())
            .andReturn();

    List<Candidate> candidates =
        objectMapper.readValue(
            result.getResponse().getContentAsString(), new TypeReference<List<Candidate>>() {});

    assertThat(candidates).hasSize(1);
    assertThat(candidates.get(0).driverId()).isEqualTo(freshDriverId);
  }

  @Test
  @DisplayName("DELETE /internal/drivers/{id} xóa tài xế khỏi drivers:geo khi nhận chuyến")
  void api_deleteDriver_removesFromGeoIndex() throws Exception {
    double centerLat = 21.0285;
    double centerLng = 105.8542;
    UUID driverId = UUID.randomUUID();

    // Ghi vị trí
    mockMvc
        .perform(
            post("/internal/locations")
                .header("X-Internal-Key", INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        List.of(new LocationUpdate(driverId, centerLat + 0.002, centerLng)))))
        .andExpect(status().isOk());

    // Xóa tài xế (khi nhận chuyến)
    mockMvc
        .perform(delete("/internal/drivers/" + driverId).header("X-Internal-Key", INTERNAL_KEY))
        .andExpect(status().isOk());

    // Tìm nearby -> không còn tài xế này
    MvcResult result =
        mockMvc
            .perform(
                get("/internal/drivers/nearby")
                    .header("X-Internal-Key", INTERNAL_KEY)
                    .param("latitude", String.valueOf(centerLat))
                    .param("longitude", String.valueOf(centerLng))
                    .param("radiusKm", "5.0"))
            .andExpect(status().isOk())
            .andReturn();

    List<Candidate> candidates =
        objectMapper.readValue(
            result.getResponse().getContentAsString(), new TypeReference<List<Candidate>>() {});

    assertThat(candidates).isEmpty();
  }

  @Test
  @DisplayName("Bảo vệ bằng X-Internal-Key: sai hoặc thiếu key trả 403 Forbidden")
  void api_endpoints_requireValidInternalKey() throws Exception {
    // 1) POST không có header
    mockMvc
        .perform(post("/internal/locations").contentType(MediaType.APPLICATION_JSON).content("[]"))
        .andExpect(status().isForbidden());

    // 2) POST sai key
    mockMvc
        .perform(
            post("/internal/locations")
                .header("X-Internal-Key", "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
        .andExpect(status().isForbidden());

    // 3) GET nearby không có header
    mockMvc
        .perform(
            get("/internal/drivers/nearby")
                .param("latitude", "21.0285")
                .param("longitude", "105.8542")
                .param("radiusKm", "5.0"))
        .andExpect(status().isForbidden());

    // 4) DELETE không có header
    mockMvc
        .perform(delete("/internal/drivers/" + UUID.randomUUID()))
        .andExpect(status().isForbidden());
  }
}
