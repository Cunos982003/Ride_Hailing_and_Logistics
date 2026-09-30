package com.ridehailing.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.ridehailing.user.domain.DriverStatus;
import com.ridehailing.user.domain.Role;
import com.ridehailing.user.dto.AuthResponse;
import com.ridehailing.user.dto.DriverResponse;
import com.ridehailing.user.dto.LoginRequest;
import com.ridehailing.user.dto.RegisterDriverRequest;
import com.ridehailing.user.dto.RegisterRequest;
import com.ridehailing.user.dto.UpdateDriverStatusRequest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class UserServiceIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void shouldRegisterAndLoginCustomer() {
    RegisterRequest registerRequest =
        new RegisterRequest(
            "+84901234567", "password123", "Nguyen Van A", "nguyenvana@example.com", Role.CUSTOMER);

    ResponseEntity<AuthResponse> registerResponse =
        restTemplate.postForEntity("/api/v1/auth/register", registerRequest, AuthResponse.class);

    assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(registerResponse.getBody()).isNotNull();
    assertThat(registerResponse.getBody().accessToken()).isNotEmpty();
    assertThat(registerResponse.getBody().refreshToken()).isNotEmpty();
    assertThat(registerResponse.getBody().user().role()).isEqualTo(Role.CUSTOMER);

    LoginRequest loginRequest = new LoginRequest("+84901234567", "password123");
    ResponseEntity<AuthResponse> loginResponse =
        restTemplate.postForEntity("/api/v1/auth/login", loginRequest, AuthResponse.class);

    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(loginResponse.getBody()).isNotNull();
    assertThat(loginResponse.getBody().accessToken()).isNotEmpty();
  }

  @Test
  void shouldRegisterDriverAndUpdateStatus() {
    RegisterRequest registerRequest =
        new RegisterRequest(
            "+84907654321",
            "driverpass123",
            "Tran Van B",
            "tranvanb@example.com",
            Role.DRIVER);

    ResponseEntity<AuthResponse> registerResponse =
        restTemplate.postForEntity("/api/v1/auth/register", registerRequest, AuthResponse.class);

    assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String userId = registerResponse.getBody().user().id().toString();

    RegisterDriverRequest driverRequest =
        new RegisterDriverRequest("DL123456789", LocalDate.of(2027, 12, 31));

    ResponseEntity<DriverResponse> driverResponse =
        restTemplate.postForEntity(
            "/api/v1/drivers/users/" + userId, driverRequest, DriverResponse.class);

    assertThat(driverResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(driverResponse.getBody()).isNotNull();
    assertThat(driverResponse.getBody().driverStatus()).isEqualTo(DriverStatus.OFFLINE);

    UpdateDriverStatusRequest statusUpdate = new UpdateDriverStatusRequest(DriverStatus.ONLINE);
    restTemplate.patchForObject(
        "/api/v1/drivers/users/" + userId + "/status", statusUpdate, DriverResponse.class);

    ResponseEntity<DriverResponse> updatedDriver =
        restTemplate.getForEntity("/api/v1/drivers/users/" + userId, DriverResponse.class);

    assertThat(updatedDriver.getBody().driverStatus()).isEqualTo(DriverStatus.ONLINE);
  }

  @Test
  void shouldRejectDuplicatePhoneNumber() {
    RegisterRequest request =
        new RegisterRequest(
            "+84909999999", "password123", "Duplicate User", null, Role.CUSTOMER);

    restTemplate.postForEntity("/api/v1/auth/register", request, AuthResponse.class);

    ResponseEntity<AuthResponse> duplicate =
        restTemplate.postForEntity("/api/v1/auth/register", request, AuthResponse.class);

    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void shouldRejectInvalidCredentials() {
    RegisterRequest registerRequest =
        new RegisterRequest(
            "+84908888888", "correctpass", "Test User", null, Role.CUSTOMER);

    restTemplate.postForEntity("/api/v1/auth/register", registerRequest, AuthResponse.class);

    LoginRequest wrongPassword = new LoginRequest("+84908888888", "wrongpass");
    ResponseEntity<AuthResponse> response =
        restTemplate.postForEntity("/api/v1/auth/login", wrongPassword, AuthResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
