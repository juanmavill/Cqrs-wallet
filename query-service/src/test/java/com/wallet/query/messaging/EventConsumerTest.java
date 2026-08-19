package com.wallet.query.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.wallet.query.event.BalanceUpdatedEvent;
import com.wallet.query.model.Balance;
import com.wallet.query.repository.BalanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El consumidor es el unico punto donde el modelo de lectura se actualiza. Si
 * proyecta un evento fuera de orden, el saldo consultado queda equivocado sin que
 * nada falle de forma visible.
 */
@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    private static final String ACCOUNT_ID = "ACC001";
    private static final Instant T1 = Instant.parse("2026-08-16T20:00:00Z");
    private static final Instant T2 = Instant.parse("2026-08-16T20:00:05Z");

    @Mock
    private BalanceRepository balanceRepository;

    @InjectMocks
    private EventConsumer eventConsumer;

    @Test
    void projectsTheBalanceWhenTheAccountHasNoProjectionYet() {
        when(balanceRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        eventConsumer.onBalanceUpdated(event(new BigDecimal("500.00"), T1));

        Balance saved = captureSaved();
        assertThat(saved.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.getBalance()).isEqualByComparingTo("500.00");
        assertThat(saved.getLastUpdated()).isEqualTo(T1);
    }

    @Test
    void appliesAnEventNewerThanTheCurrentProjection() {
        when(balanceRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(projection("500.00", T1)));

        eventConsumer.onBalanceUpdated(event(new BigDecimal("450.00"), T2));

        assertThat(captureSaved().getBalance()).isEqualByComparingTo("450.00");
    }

    /**
     * La cola reintenta y puede reentregar. Sin esta guarda, una reentrega tardia
     * de un evento anterior sobrescribiria el saldo correcto con uno obsoleto.
     */
    @Test
    void discardsAnEventOlderThanTheCurrentProjection() {
        when(balanceRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(projection("450.00", T2)));

        eventConsumer.onBalanceUpdated(event(new BigDecimal("500.00"), T1));

        verify(balanceRepository, never()).save(any());
    }

    /**
     * El evento transporta el saldo resultante, no un incremento, de modo que
     * volver a aplicar el mismo evento deja el mismo estado.
     */
    @Test
    void reapplyingTheSameEventIsHarmless() {
        when(balanceRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(projection("450.00", T2)));

        eventConsumer.onBalanceUpdated(event(new BigDecimal("450.00"), T2));

        assertThat(captureSaved().getBalance()).isEqualByComparingTo("450.00");
    }

    private BalanceUpdatedEvent event(BigDecimal newBalance, Instant timestamp) {
        BalanceUpdatedEvent event = new BalanceUpdatedEvent();
        event.setAccountId(ACCOUNT_ID);
        event.setNewBalance(newBalance);
        event.setTimestamp(timestamp);
        return event;
    }

    private Balance projection(String balance, Instant lastUpdated) {
        return Balance.builder()
                .accountId(ACCOUNT_ID)
                .balance(new BigDecimal(balance))
                .lastUpdated(lastUpdated)
                .build();
    }

    private Balance captureSaved() {
        ArgumentCaptor<Balance> captor = ArgumentCaptor.forClass(Balance.class);
        verify(balanceRepository).save(captor.capture());
        return captor.getValue();
    }
}
