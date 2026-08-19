package com.wallet.query.messaging;

import java.util.Optional;

import com.wallet.query.event.BalanceUpdatedEvent;
import com.wallet.query.model.Balance;
import com.wallet.query.repository.BalanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final BalanceRepository balanceRepository;

    public EventConsumer(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }

    /**
     * Projects the balance published by the write side.
     *
     * <p>The queue retries and ends in a DLQ, so the same event can be
     * redelivered. Because the event carries the computed balance rather than a
     * delta, reapplying it is harmless, but applying one <em>older</em> than what
     * is already projected would leave the read model permanently stale. Any
     * event older than the current projection is therefore discarded.
     *
     * <p>The check reads and then writes, so it assumes a single consumer (the
     * default configuration). Consuming in parallel would require a conditional
     * write in the database itself.
     */
    @RabbitListener(queues = "${wallet.queue.balance-updated}")
    public void onBalanceUpdated(BalanceUpdatedEvent event) {
        if (isStale(event)) {
            log.warn("Discarding stale BalanceUpdatedEvent for {}: event={} projection is newer",
                    event.getAccountId(), event.getTimestamp());
            return;
        }

        balanceRepository.save(Balance.builder()
                .accountId(event.getAccountId())
                .balance(event.getNewBalance())
                .lastUpdated(event.getTimestamp())
                .build());
        log.debug("Upserted balance for {} = {}", event.getAccountId(), event.getNewBalance());
    }

    private boolean isStale(BalanceUpdatedEvent event) {
        Optional<Balance> projected = balanceRepository.findById(event.getAccountId());
        return projected.isPresent()
                && projected.get().getLastUpdated() != null
                && event.getTimestamp() != null
                && event.getTimestamp().isBefore(projected.get().getLastUpdated());
    }
}
