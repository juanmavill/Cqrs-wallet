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
 * Forwarding happens on AFTER_COMMIT: the write transaction has already finished,
 * so there is nothing to roll back if the broker fails.
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
     * A known and deliberate limitation: if the broker is down after the commit,
     * the event is lost and the read model stays permanently out of sync. The
     * exception is not propagated because the money has already moved, and
     * retrying here would duplicate the event. Closing this gap requires a
     * transactional outbox, which this project does not implement.
     *
     * <p>This test pins that contract: if someone changes the behaviour so the
     * exception escapes, the failure would surface on the commit thread without
     * recovering the lost event.
     */
    @Test
    void doesNotPropagateABrokerFailureBecauseTheWriteIsAlreadyCommitted() {
        TransactionPersistedEvent event = event();
        doThrow(new BrokerUnavailableException("broker down", new RuntimeException()))
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
