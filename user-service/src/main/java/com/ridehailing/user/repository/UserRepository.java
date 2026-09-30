package com.ridehailing.user.repository;

import com.ridehailing.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByPhoneNumber(String phoneNumber);

  boolean existsByPhoneNumber(String phoneNumber);
}
