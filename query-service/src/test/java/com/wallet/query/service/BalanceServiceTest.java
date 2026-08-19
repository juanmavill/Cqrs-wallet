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
     * Una cuenta sin proyeccion no es lo mismo que una cuenta inexistente: puede
     * existir en el lado de escritura y no haber recibido todavia ningun evento.
     */
    @Test
    void failsWhenTheAccountHasNoProjection() {
        when(balanceRepository.findById("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.getBalance("NOPE"))
                .isInstanceOf(BalanceNotFoundException.class);
    }
}
