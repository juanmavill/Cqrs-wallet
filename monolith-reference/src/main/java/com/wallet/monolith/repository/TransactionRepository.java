package com.wallet.monolith.repository;

import com.wallet.monolith.model.MonolithTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<MonolithTransaction, String> {
}
