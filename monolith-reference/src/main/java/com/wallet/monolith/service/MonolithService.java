package com.wallet.monolith.service;

import com.wallet.monolith.exception.AccountNotFoundException;
import com.wallet.monolith.exception.InsufficientFundsException;
import com.wallet.monolith.model.Account;
import com.wallet.monolith.model.MonolithTransaction;
import com.wallet.monolith.repository.AccountRepository;
import com.wallet.monolith.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class MonolithService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public MonolithService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResult process(String accountId, BigDecimal amount, MonolithTransaction.Type type) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        BigDecimal newBalance;
        if (type == MonolithTransaction.Type.DEBIT) {
            if (account.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(accountId, account.getBalance(), amount);
            }
            newBalance = account.getBalance().subtract(amount);
        } else {
            newBalance = account.getBalance().add(amount);
        }
        account.setBalance(newBalance);
        accountRepository.save(account);

        Instant now = Instant.now();
        MonolithTransaction tx = MonolithTransaction.builder()
                .id(UUID.randomUUID().toString())
                .accountId(accountId)
                .amount(amount)
                .type(type)
                .status(MonolithTransaction.Status.COMPLETED)
                .timestamp(now)
                .build();
        transactionRepository.save(tx);

        return new TransactionResult(tx.getId(), now);
    }

    @Transactional(readOnly = true)
    public Account getAccount(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public record TransactionResult(String transactionId, Instant timestamp) {}
}
