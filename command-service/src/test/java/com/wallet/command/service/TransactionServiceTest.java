package com.wallet.command.service;

import java.math.BigDecimal;
import java.util.Optional;

import com.wallet.command.dto.TransactionRequest;
import com.wallet.command.dto.TransactionResponse;
import com.wallet.command.event.TransactionPersistedEvent;
import com.wallet.command.exception.AccountNotFoundException;
import com.wallet.command.exception.InsufficientFundsException;
import com.wallet.command.model.Account;
import com.wallet.command.model.Transaction;
import com.wallet.command.repository.AccountRepository;
import com.wallet.command.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the write-side rules: how each operation is applied to the balance, what
 * is rejected, and when the event feeding the read model is emitted. A silent
 * failure here desynchronises the two sides of the CQRS split.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final String ACCOUNT_ID = "ACC-001";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private TransactionService transactionService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .id(ACCOUNT_ID)
                .ownerName("Ada Lovelace")
                .balance(new BigDecimal("100.00"))
                .build();
    }

    @Test
    void creditIncreasesBalanceAndPublishesNewBalance() {
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));

        TransactionResponse response = transactionService.process(
                request(new BigDecimal("25.50"), Transaction.Type.CREDIT));

        assertThat(account.getBalance()).isEqualByComparingTo("125.50");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getTransactionId()).isNotBlank();
        assertThat(publishedEvent().payload().getNewBalance()).isEqualByComparingTo("125.50");
    }

    @Test
    void debitDecreasesBalanceWhenFundsAreSufficient() {
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));

        transactionService.process(request(new BigDecimal("40.00"), Transaction.Type.DEBIT));

        assertThat(account.getBalance()).isEqualByComparingTo("60.00");
        assertThat(publishedEvent().payload().getNewBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    void debitOfTheExactBalanceIsAllowed() {
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));

        transactionService.process(request(new BigDecimal("100.00"), Transaction.Type.DEBIT));

        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void debitBeyondTheBalanceIsRejectedWithoutTouchingState() {
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> transactionService.process(
                request(new BigDecimal("100.01"), Transaction.Type.DEBIT)))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
        verify(transactionRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any(TransactionPersistedEvent.class));
    }

    @Test
    void unknownAccountIsRejectedWithoutPublishingAnything() {
        when(accountRepository.findByIdForUpdate("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.process(
                TransactionRequest.builder()
                        .accountId("MISSING")
                        .amount(new BigDecimal("10.00"))
                        .type(Transaction.Type.CREDIT)
                        .build()))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accountRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any(TransactionPersistedEvent.class));
    }

    /**
     * The balance is read under a pessimistic lock so two concurrent movements on
     * the same account cannot overwrite each other. Were this swapped for
     * findById, the sufficient-funds rule could be evaluated against a stale
     * balance.
     */
    @Test
    void balanceIsReadWithAPessimisticLock() {
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));

        transactionService.process(request(new BigDecimal("1.00"), Transaction.Type.CREDIT));

        verify(accountRepository).findByIdForUpdate(ACCOUNT_ID);
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void persistsTheMovementAsACompletedTransaction() {
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));

        transactionService.process(request(new BigDecimal("30.00"), Transaction.Type.DEBIT));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.getAmount()).isEqualByComparingTo("30.00");
        assertThat(saved.getType()).isEqualTo(Transaction.Type.DEBIT);
        assertThat(saved.getStatus()).isEqualTo(Transaction.Status.COMPLETED);
        assertThat(saved.getTimestamp()).isNotNull();
    }

    private TransactionRequest request(BigDecimal amount, Transaction.Type type) {
        return TransactionRequest.builder()
                .accountId(ACCOUNT_ID)
                .amount(amount)
                .type(type)
                .build();
    }

    private TransactionPersistedEvent publishedEvent() {
        ArgumentCaptor<TransactionPersistedEvent> captor =
                ArgumentCaptor.forClass(TransactionPersistedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }
}
