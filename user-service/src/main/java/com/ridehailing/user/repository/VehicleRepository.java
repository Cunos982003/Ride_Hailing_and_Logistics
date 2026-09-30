package com.ridehailing.user.repository;

import com.ridehailing.user.domain.Vehicle;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
  List<Vehicle> findByDriverId(UUID driverId);

  boolean existsByLicensePlate(String licensePlate);
}
