package com.wallet.query.exception;

public class BalanceNotFoundException extends RuntimeException {

    public BalanceNotFoundException(String accountId) {
        super("Balance not found for account: " + accountId);
    }
}
