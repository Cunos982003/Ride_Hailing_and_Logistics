package com.ridehailing.core.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class WalletRepository {
    private final JdbcClient jdbcClient;

    public WalletRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void createWallet(long userId, long initialBalance) {
        jdbcClient.sql("""
            INSERT INTO wallets (user_id, balance)
            VALUES (?, ?)
            """)
            .param(userId)
            .param(initialBalance)
            .update();
    }
}
