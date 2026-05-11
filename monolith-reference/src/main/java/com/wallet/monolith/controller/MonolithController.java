package com.wallet.monolith.controller;

import com.wallet.monolith.exception.AccountNotFoundException;
import com.wallet.monolith.exception.InsufficientFundsException;
import com.wallet.monolith.model.Account;
import com.wallet.monolith.model.MonolithTransaction;
import com.wallet.monolith.service.MonolithService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class MonolithController {

    private final MonolithService monolithService;

    public MonolithController(MonolithService monolithService) {
        this.monolithService = monolithService;
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        MonolithService.TransactionResult result = monolithService.process(
                request.getAccountId(), request.getAmount(), request.getType());
        return ResponseEntity.ok(TransactionResponse.builder()
                .transactionId(result.transactionId())
                .status("SUCCESS")
                .timestamp(result.timestamp())
                .build());
    }

    @GetMapping("/balance/{accountId}")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountId) {
        Account account = monolithService.getAccount(accountId);
        return ResponseEntity.ok(BalanceResponse.builder()
                .accountId(account.getId())
                .balance(account.getBalance())
                .lastUpdated(account.getUpdatedAt())
                .build());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(AccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("ACCOUNT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficient(InsufficientFundsException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(error("INSUFFICIENT_FUNDS", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("VALIDATION_ERROR", details));
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of(
                "error", code,
                "message", message,
                "timestamp", Instant.now().toString()
        );
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionRequest {
        @NotBlank
        private String accountId;
        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be positive")
        private BigDecimal amount;
        @NotNull
        private MonolithTransaction.Type type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionResponse {
        private String transactionId;
        private String status;
        private Instant timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceResponse {
        private String accountId;
        private BigDecimal balance;
        private Instant lastUpdated;
    }
}
