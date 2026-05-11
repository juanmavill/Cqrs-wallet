package com.wallet.query.service;

import com.wallet.query.exception.BalanceNotFoundException;
import com.wallet.query.model.Balance;
import com.wallet.query.repository.BalanceRepository;
import org.springframework.stereotype.Service;

@Service
public class BalanceService {

    private final BalanceRepository balanceRepository;

    public BalanceService(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }

    public Balance getBalance(String accountId) {
        return balanceRepository.findById(accountId)
                .orElseThrow(() -> new BalanceNotFoundException(accountId));
    }
}
