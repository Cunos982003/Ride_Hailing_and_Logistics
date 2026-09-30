package com.ridehailing.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ridehailing.common.error.ApiException;
import com.ridehailing.common.error.ErrorCode;
import com.ridehailing.user.domain.Driver;
import com.ridehailing.user.domain.DriverStatus;
import com.ridehailing.user.domain.Role;
import com.ridehailing.user.domain.User;
import com.ridehailing.user.dto.DriverResponse;
import com.ridehailing.user.dto.RegisterDriverRequest;
import com.ridehailing.user.dto.UpdateDriverStatusRequest;
import com.ridehailing.user.repository.DriverRepository;
import com.ridehailing.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

  @Mock private DriverRepository driverRepository;
  @Mock private UserRepository userRepository;
  @InjectMocks private DriverService driverService;

  private User driverUser;
  private Driver driver;

  @BeforeEach
  void setUp() {
    driverUser = new User();
    driverUser.setId(UUID.randomUUID());
    driverUser.setPhoneNumber("+84907654321");
    driverUser.setRole(Role.DRIVER);
    driverUser.setFullName("Driver User");
    driverUser.setCreatedAt(Instant.now());
    driverUser.setUpdatedAt(Instant.now());

    driver = new Driver();
    driver.setId(UUID.randomUUID());
    driver.setUser(driverUser);
    driver.setLicenseNumber("DL123456789");
    driver.setLicenseExpiryDate(LocalDate.of(2027, 12, 31));
    driver.setDriverStatus(DriverStatus.OFFLINE);
    driver.setRating(BigDecimal.ZERO);
    driver.setTotalTrips(0);
    driver.setCreatedAt(Instant.now());
    driver.setUpdatedAt(Instant.now());
  }

  @Test
  void shouldRegisterDriver() {
    RegisterDriverRequest request = new RegisterDriverRequest("DL123456789", LocalDate.of(2027, 12, 31));

    when(userRepository.findById(driverUser.getId())).thenReturn(Optional.of(driverUser));
    when(driverRepository.findByUserId(driverUser.getId())).thenReturn(Optional.empty());
    when(driverRepository.existsByLicenseNumber(request.licenseNumber())).thenReturn(false);
    when(driverRepository.save(any(Driver.class))).thenReturn(driver);

    DriverResponse response = driverService.registerDriver(driverUser.getId(), request);

    assertThat(response.licenseNumber()).isEqualTo(request.licenseNumber());
    assertThat(response.driverStatus()).isEqualTo(DriverStatus.OFFLINE);
    verify(driverRepository).save(any(Driver.class));
  }

  @Test
  void shouldThrowForbiddenWhenUserIsNotDriver() {
    User customerUser = new User();
    customerUser.setId(UUID.randomUUID());
    customerUser.setRole(Role.CUSTOMER);

    RegisterDriverRequest request = new RegisterDriverRequest("DL123456789", LocalDate.of(2027, 12, 31));

    when(userRepository.findById(customerUser.getId())).thenReturn(Optional.of(customerUser));

    assertThatThrownBy(() -> driverService.registerDriver(customerUser.getId(), request))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
  }

  @Test
  void shouldUpdateDriverStatus() {
    UpdateDriverStatusRequest request = new UpdateDriverStatusRequest(DriverStatus.ONLINE);

    when(userRepository.findById(driverUser.getId())).thenReturn(Optional.of(driverUser));
    when(driverRepository.findByUserId(driverUser.getId())).thenReturn(Optional.of(driver));
    when(driverRepository.save(any(Driver.class))).thenReturn(driver);

    driverService.updateStatus(driverUser.getId(), request);

    verify(driverRepository).save(any(Driver.class));
  }

  @Test
  void shouldGetDriverProfile() {
    when(userRepository.findById(driverUser.getId())).thenReturn(Optional.of(driverUser));
    when(driverRepository.findByUserId(driverUser.getId())).thenReturn(Optional.of(driver));

    DriverResponse response = driverService.getDriverProfile(driverUser.getId());

    assertThat(response.userId()).isEqualTo(driverUser.getId());
    assertThat(response.licenseNumber()).isEqualTo(driver.getLicenseNumber());
  }
}
