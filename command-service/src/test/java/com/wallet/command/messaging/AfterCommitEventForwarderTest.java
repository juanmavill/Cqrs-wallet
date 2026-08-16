package com.wallet.command.messaging;

import java.math.BigDecimal;
import java.time.Instant;

import com.wallet.command.event.BalanceUpdatedEvent;
import com.wallet.command.event.TransactionPersistedEvent;
import com.wallet.command.exception.BrokerUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * El reenvio ocurre en AFTER_COMMIT: la transaccion de escritura ya termino, asi
 * que no hay nada que revertir si el broker falla.
 */
@ExtendWith(MockitoExtension.class)
class AfterCommitEventForwarderTest {

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private AfterCommitEventForwarder forwarder;

    @Test
    void forwardsThePayloadToTheBroker() {
        TransactionPersistedEvent event = event();

        forwarder.onCommit(event);

        verify(eventPublisher).publish(event.payload());
    }

    /**
     * Limitacion conocida y deliberada: si el broker esta caido despues del commit,
     * el evento se pierde y el read model queda desincronizado de forma permanente.
     * No se propaga la excepcion porque el dinero ya se movio y reintentar aqui
     * duplicaria el evento. Cerrar este hueco exige un outbox transaccional, que
     * este proyecto no implementa.
     *
     * <p>Este test fija ese contrato: si alguien cambia el comportamiento para que
     * la excepcion escape, el fallo apareceria en el hilo de commit sin recuperar
     * el evento perdido.
     */
    @Test
    void doesNotPropagateABrokerFailureBecauseTheWriteIsAlreadyCommitted() {
        TransactionPersistedEvent event = event();
        doThrow(new BrokerUnavailableException("broker caido", new RuntimeException()))
                .when(eventPublisher).publish(event.payload());

        assertThatCode(() -> forwarder.onCommit(event)).doesNotThrowAnyException();
    }

    private TransactionPersistedEvent event() {
        return new TransactionPersistedEvent(BalanceUpdatedEvent.builder()
                .accountId("ACC-001")
                .newBalance(new BigDecimal("75.00"))
                .timestamp(Instant.now())
                .build());
    }
}
