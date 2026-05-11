package com.wallet.query.controller;

import com.wallet.query.exception.BalanceNotFoundException;
import com.wallet.query.model.Balance;
import com.wallet.query.service.BalanceService;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/balance")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<Balance> getBalance(@PathVariable String accountId) {
        return ResponseEntity.ok(balanceService.getBalance(accountId));
    }

    @ExceptionHandler(BalanceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(BalanceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("BALANCE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<Map<String, Object>> handleMongoDown(DataAccessResourceFailureException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error("READ_STORE_UNAVAILABLE", "MongoDB is unavailable"));
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of(
                "error", code,
                "message", message,
                "timestamp", Instant.now().toString()
        );
    }
}
