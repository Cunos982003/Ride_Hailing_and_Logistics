package com.ridehailing.core;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.common.jwt.JwtUtil;
import com.ridehailing.core.dto.AuthResponse;
import com.ridehailing.core.dto.LoginRequest;
import com.ridehailing.core.dto.RegisterRequest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
@Testcontainers
class AuthIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("user_db")
          .withUsername("test_user")
          .withPassword("test_pass");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void cleanup() {
    // Xóa dữ liệu theo thứ tự FK: ledger -> trip_events -> trips -> wallets -> users
    jdbcClient.sql("DELETE FROM ledger_entries").update();
    jdbcClient.sql("DELETE FROM trip_events").update();
    jdbcClient.sql("DELETE FROM trips").update();
    jdbcClient.sql("DELETE FROM wallets").update();
    jdbcClient.sql("DELETE FROM users").update();
  }

  // --- Đăng ký + đăng nhập thành công ---

  @Test
  void registerCustomer_success() throws Exception {
    RegisterRequest request =
        new RegisterRequest("customer@test.com", "password123", "John Doe", "CUSTOMER");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").exists());
  }

  @Test
  void registerDriver_success() throws Exception {
    RegisterRequest request =
        new RegisterRequest("driver@test.com", "password123", "Jane Smith", "DRIVER");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").exists());
  }

  @Test
  void login_success() throws Exception {
    // Đăng ký trước
    RegisterRequest registerRequest =
        new RegisterRequest("login@test.com", "password123", "Login Test", "CUSTOMER");
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)));

    // Đăng nhập
    LoginRequest loginRequest = new LoginRequest("login@test.com", "password123");
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").exists());
  }

  // --- Đăng ký trùng email = 409 ---

  @Test
  void registerDuplicateEmail_returns409() throws Exception {
    RegisterRequest request =
        new RegisterRequest("duplicate@test.com", "password123", "Test User", "CUSTOMER");

    // Lần đăng ký đầu
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    // Lần đăng ký trùng email
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
  }

  // --- Token hết hạn = 401 ---

  @Test
  void expiredToken_returns401() throws Exception {
    // Tạo token đã hết hạn (expiration trong quá khứ)
    JwtUtil testJwt = new JwtUtil("test-secret-key-at-least-32-chars-long-for-hs256-algorithm");
    String expiredToken =
        testJwt.generateTokenWithExpiration("999", "CUSTOMER", Instant.now().minusSeconds(60));

    mockMvc
        .perform(get("/api/v1/rides/test").header("Authorization", "Bearer " + expiredToken))
        .andExpect(status().isUnauthorized());
  }

  // --- Token sai chữ ký = 401 ---

  @Test
  void wrongSignatureToken_returns401() throws Exception {
    // Tạo token bằng secret khác → chữ ký không match server
    JwtUtil otherJwt = new JwtUtil("different-secret-key-at-least-32-chars-long-for-hs256-test");
    String wrongSigToken = otherJwt.createToken("999", "CUSTOMER");

    mockMvc
        .perform(get("/api/v1/rides/test").header("Authorization", "Bearer " + wrongSigToken))
        .andExpect(status().isUnauthorized());
  }

  // --- Khách (CUSTOMER) gọi endpoint bình thường ---

  @Test
  void customerAccessRides_authPasses() throws Exception {
    // Đăng ký customer
    RegisterRequest req =
        new RegisterRequest("cust-access@test.com", "password123", "Cust Access", "CUSTOMER");
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andReturn();

    AuthResponse auth =
        objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);

    // Customer gọi /api/v1/rides/** → auth/role đều pass, trả 404 vì chưa có handler
    mockMvc
        .perform(get("/api/v1/rides/test").header("Authorization", "Bearer " + auth.accessToken()))
        .andExpect(status().isNotFound());
  }

  // --- Driver gọi endpoint của CUSTOMER (/api/v1/rides/**) = 403 ---

  @Test
  void driverAccessRidesEndpoint_returns403() throws Exception {
    // Đăng ký driver
    RegisterRequest req =
        new RegisterRequest(
            "driver-forbidden@test.com", "password123", "Driver Forbidden", "DRIVER");
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andReturn();

    AuthResponse auth =
        objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);

    // Driver thử gọi endpoint chỉ dành cho CUSTOMER → 403
    mockMvc
        .perform(get("/api/v1/rides/test").header("Authorization", "Bearer " + auth.accessToken()))
        .andExpect(status().isForbidden());
  }

  // --- Khách (CUSTOMER) gọi endpoint của tài xế (/api/v1/driver/**) = 403 ---

  @Test
  void customerAccessDriverEndpoint_returns403() throws Exception {
    RegisterRequest req =
        new RegisterRequest(
            "customer-forbidden@test.com", "password123", "Customer Forbidden", "CUSTOMER");
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andReturn();

    AuthResponse auth =
        objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);

    // Khách gọi endpoint của tài xế → 403
    mockMvc
        .perform(
            get("/api/v1/driver/status").header("Authorization", "Bearer " + auth.accessToken()))
        .andExpect(status().isForbidden());
  }

  // --- Thêm test bổ sung cho coverage ---

  @Test
  void loginWrongPassword_returns401() throws Exception {
    RegisterRequest registerRequest =
        new RegisterRequest("wrong@test.com", "password123", "Wrong Test", "CUSTOMER");
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)));

    LoginRequest loginRequest = new LoginRequest("wrong@test.com", "wrongpassword");
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void loginNonExistentEmail_returns401() throws Exception {
    LoginRequest loginRequest = new LoginRequest("nonexistent@test.com", "password123");
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void accessProtectedEndpoint_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/rides/test")).andExpect(status().isUnauthorized());
  }

  @Test
  void accessProtectedEndpoint_withMalformedToken_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/rides/test").header("Authorization", "Bearer invalid.token.here"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void accessInternalEndpoint_withValidKey_success() throws Exception {
    mockMvc
        .perform(get("/internal/test").header("X-Internal-Key", "test-internal-key"))
        .andExpect(status().isNotFound()); // auth pass, 404 vì chưa có handler
  }

  @Test
  void accessInternalEndpoint_withoutKey_returns403() throws Exception {
    mockMvc.perform(get("/internal/test")).andExpect(status().isForbidden());
  }

  @Test
  void accessInternalEndpoint_withInvalidKey_returns403() throws Exception {
    mockMvc
        .perform(get("/internal/test").header("X-Internal-Key", "wrong-key"))
        .andExpect(status().isForbidden());
  }
}
