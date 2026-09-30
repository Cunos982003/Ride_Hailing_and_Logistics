package com.ridehailing.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "drivers")
public class Driver {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(name = "license_number", nullable = false, unique = true, length = 50)
  private String licenseNumber;

  @Column(name = "license_expiry_date", nullable = false)
  private LocalDate licenseExpiryDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "driver_status", nullable = false, length = 20)
  private DriverStatus driverStatus = DriverStatus.OFFLINE;

  @Column(name = "rating", precision = 3, scale = 2)
  private BigDecimal rating = BigDecimal.ZERO;

  @Column(name = "total_trips", nullable = false)
  private Integer totalTrips = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getLicenseNumber() {
    return licenseNumber;
  }

  public void setLicenseNumber(String licenseNumber) {
    this.licenseNumber = licenseNumber;
  }

  public LocalDate getLicenseExpiryDate() {
    return licenseExpiryDate;
  }

  public void setLicenseExpiryDate(LocalDate licenseExpiryDate) {
    this.licenseExpiryDate = licenseExpiryDate;
  }

  public DriverStatus getDriverStatus() {
    return driverStatus;
  }

  public void setDriverStatus(DriverStatus driverStatus) {
    this.driverStatus = driverStatus;
  }

  public BigDecimal getRating() {
    return rating;
  }

  public void setRating(BigDecimal rating) {
    this.rating = rating;
  }

  public Integer getTotalTrips() {
    return totalTrips;
  }

  public void setTotalTrips(Integer totalTrips) {
    this.totalTrips = totalTrips;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
