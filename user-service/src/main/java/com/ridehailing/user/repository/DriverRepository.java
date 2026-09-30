package com.ridehailing.user.repository;

import com.ridehailing.user.domain.Driver;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, UUID> {
  Optional<Driver> findByUserId(UUID userId);

  boolean existsByLicenseNumber(String licenseNumber);
}
