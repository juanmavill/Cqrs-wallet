package com.wallet.query.repository.jdbc;

import com.wallet.query.model.Balance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class AccountSeedRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountSeedRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Balance> loadAllAccountBalances() {
        return jdbcTemplate.query(
                "SELECT id, balance FROM accounts",
                (rs, rowNum) -> Balance.builder()
                        .accountId(rs.getString("id"))
                        .balance(rs.getBigDecimal("balance"))
                        .lastUpdated(Instant.now())
                        .build()
        );
    }
}
