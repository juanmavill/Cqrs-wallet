package com.wallet.command.event;

public record TransactionPersistedEvent(BalanceUpdatedEvent payload) {
}
