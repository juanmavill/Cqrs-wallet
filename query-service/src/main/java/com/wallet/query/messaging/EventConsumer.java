package com.wallet.query.messaging;

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

    @RabbitListener(queues = "${wallet.queue.balance-updated}")
    public void onBalanceUpdated(BalanceUpdatedEvent event) {
        Balance balance = Balance.builder()
                .accountId(event.getAccountId())
                .balance(event.getNewBalance())
                .lastUpdated(event.getTimestamp())
                .build();
        balanceRepository.save(balance);
        log.debug("Upserted balance for {} = {}", event.getAccountId(), event.getNewBalance());
    }
}
