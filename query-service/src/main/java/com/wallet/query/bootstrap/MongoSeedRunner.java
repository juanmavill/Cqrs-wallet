package com.wallet.query.bootstrap;

import com.wallet.query.model.Balance;
import com.wallet.query.repository.BalanceRepository;
import com.wallet.query.repository.jdbc.AccountSeedRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MongoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoSeedRunner.class);

    private final BalanceRepository balanceRepository;
    private final AccountSeedRepository accountSeedRepository;

    public MongoSeedRunner(BalanceRepository balanceRepository,
                           AccountSeedRepository accountSeedRepository) {
        this.balanceRepository = balanceRepository;
        this.accountSeedRepository = accountSeedRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        long existing = balanceRepository.count();
        if (existing > 0) {
            log.info("MongoSeedRunner: balances collection already has {} documents — skipping seed",
                    existing);
            return;
        }

        List<Balance> balances = accountSeedRepository.loadAllAccountBalances();
        if (balances.isEmpty()) {
            log.warn("MongoSeedRunner: MySQL accounts table is empty, nothing to seed");
            return;
        }
        balanceRepository.saveAll(balances);
        log.info("MongoSeedRunner: seeded {} balances from MySQL into MongoDB", balances.size());
    }
}
