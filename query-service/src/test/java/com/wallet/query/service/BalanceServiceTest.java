package com.wallet.query.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.wallet.query.exception.BalanceNotFoundException;
import com.wallet.query.model.Balance;
import com.wallet.query.repository.BalanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private BalanceRepository balanceRepository;

    @InjectMocks
    private BalanceService balanceService;

    @Test
    void returnsTheProjectedBalance() {
        when(balanceRepository.findById("ACC001")).thenReturn(Optional.of(Balance.builder()
                .accountId("ACC001")
                .balance(new BigDecimal("9750.00"))
                .lastUpdated(Instant.parse("2026-08-16T20:00:00Z"))
                .build()));

        assertThat(balanceService.getBalance("ACC001").getBalance()).isEqualByComparingTo("9750.00");
    }

    /**
     * An account without a projection is not the same as a non-existent account:
     * it may exist on the write side and simply not have received an event yet.
     */
    @Test
    void failsWhenTheAccountHasNoProjection() {
        when(balanceRepository.findById("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.getBalance("NOPE"))
                .isInstanceOf(BalanceNotFoundException.class);
    }
}
