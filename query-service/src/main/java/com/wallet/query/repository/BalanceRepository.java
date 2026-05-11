package com.wallet.query.repository;

import com.wallet.query.model.Balance;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BalanceRepository extends MongoRepository<Balance, String> {
}
