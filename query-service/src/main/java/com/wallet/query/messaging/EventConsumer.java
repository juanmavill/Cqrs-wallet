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
     * Proyecta el saldo publicado por el lado de escritura.
     *
     * <p>La cola reintenta y termina en una DLQ, asi que un mismo evento puede
     * reentregarse. Como el evento trae el saldo ya calculado y no un delta,
     * reaplicarlo es inocuo, pero aplicar uno <em>anterior</em> al ya proyectado
     * dejaria el modelo de lectura con un saldo viejo de forma permanente. Por eso
     * se descarta todo evento mas antiguo que la proyeccion actual.
     *
     * <p>La comprobacion lee y luego escribe, de modo que asume un unico consumidor
     * (la configuracion por defecto). Para consumir en paralelo haria falta una
     * escritura condicional en la propia base de datos.
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
