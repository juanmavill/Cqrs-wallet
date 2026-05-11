package com.wallet.command.messaging;

import com.wallet.command.event.TransactionPersistedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AfterCommitEventForwarder {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitEventForwarder.class);

    private final EventPublisher eventPublisher;

    public AfterCommitEventForwarder(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(TransactionPersistedEvent event) {
        try {
            eventPublisher.publish(event.payload());
        } catch (RuntimeException ex) {
            log.error("Failed to publish BalanceUpdatedEvent for account {} after commit; read model may be stale",
                    event.payload().getAccountId(), ex);
        }
    }
}
