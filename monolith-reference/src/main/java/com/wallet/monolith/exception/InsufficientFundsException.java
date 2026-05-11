package com.wallet.monolith.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountId, BigDecimal balance, BigDecimal requested) {
        super("Insufficient funds for account " + accountId
                + " (balance=" + balance + ", requested=" + requested + ")");
    }
}
