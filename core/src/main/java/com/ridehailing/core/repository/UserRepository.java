package com.ridehailing.core.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Tạo user mới và wallet trong cùng transaction.
     * Khách hàng có số dư ban đầu 500000, tài xế có 0.
     */
    public long createUserWithWallet(String email, String passwordHash, String fullName, String role) {
        // Insert user
        Long userId = jdbcClient.sql("""
            INSERT INTO users (email, password_hash, full_name, role)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """)
            .params(email, passwordHash, fullName, role)
            .query(Long.class)
            .single();

        // Insert wallet với số dư ban đầu
        long initialBalance = "CUSTOMER".equals(role) ? 500000 : 0;
        jdbcClient.sql("INSERT INTO wallets (user_id, balance) VALUES (?, ?)")
            .params(userId, initialBalance)
            .update();

        return userId;
    }

    /**
     * Tìm user theo email.
     */
    public Optional<UserRecord> findByEmail(String email) {
        return jdbcClient.sql("""
            SELECT id, email, password_hash, role, full_name, created_at
            FROM users
            WHERE email = ?
            """)
            .param(email)
            .query((rs, rowNum) -> new UserRecord(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("role"),
                rs.getString("full_name"),
                rs.getTimestamp("created_at").toInstant()
            ))
            .optional();
    }

    /**
     * Kiểm tra email đã tồn tại chưa.
     */
    public boolean existsByEmail(String email) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM users WHERE email = ?")
            .param(email)
            .query(Integer.class)
            .single();
        return count > 0;
    }

    public record UserRecord(
        long id,
        String email,
        String passwordHash,
        String role,
        String fullName,
        java.time.Instant createdAt
    ) {}
}
