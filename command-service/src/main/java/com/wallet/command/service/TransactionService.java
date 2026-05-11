package com.wallet.command.service;

import com.wallet.command.dto.TransactionRequest;
import com.wallet.command.dto.TransactionResponse;
import com.wallet.command.event.BalanceUpdatedEvent;
import com.wallet.command.event.TransactionPersistedEvent;
import com.wallet.command.exception.AccountNotFoundException;
import com.wallet.command.exception.InsufficientFundsException;
import com.wallet.command.model.Account;
import com.wallet.command.model.Transaction;
import com.wallet.command.repository.AccountRepository;
import com.wallet.command.repository.TransactionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              ApplicationEventPublisher applicationEventPublisher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public TransactionResponse process(TransactionRequest request) {
        Account account = accountRepository.findByIdForUpdate(request.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.getAccountId()));

        BigDecimal newBalance = applyOperation(account.getBalance(), request);
        account.setBalance(newBalance);
        accountRepository.save(account);

        Instant now = Instant.now();
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .accountId(account.getId())
                .amount(request.getAmount())
                .type(request.getType())
                .status(Transaction.Status.COMPLETED)
                .timestamp(now)
                .build();
        transactionRepository.save(tx);

        BalanceUpdatedEvent payload = BalanceUpdatedEvent.builder()
                .accountId(account.getId())
                .newBalance(newBalance)
                .timestamp(now)
                .build();
        applicationEventPublisher.publishEvent(new TransactionPersistedEvent(payload));

        return TransactionResponse.builder()
                .transactionId(tx.getId())
                .status("SUCCESS")
                .timestamp(now)
                .build();
    }

    private BigDecimal applyOperation(BigDecimal currentBalance, TransactionRequest request) {
        if (request.getType() == Transaction.Type.DEBIT) {
            if (currentBalance.compareTo(request.getAmount()) < 0) {
                throw new InsufficientFundsException(
                        request.getAccountId(), currentBalance, request.getAmount());
            }
            return currentBalance.subtract(request.getAmount());
        }
        return currentBalance.add(request.getAmount());
    }
}
