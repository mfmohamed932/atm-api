package com.heb.atm.service;

import com.heb.atm.dto.CompleteTransactionRequest;
import com.heb.atm.dto.DepositRequest;
import com.heb.atm.dto.TransactionResponse;
import com.heb.atm.exception.AccountNotFoundException;
import com.heb.atm.exception.DatabaseException;
import com.heb.atm.model.Account;
import com.heb.atm.model.Transaction;
import com.heb.atm.repository.AccountRepository;
import com.heb.atm.repository.TransactionRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepositService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    @Retryable(
        retryFor = {OptimisticLockException.class, ObjectOptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 1)
    )
    public TransactionResponse initiateDeposit(DepositRequest request) {
        log.info("Initiating deposit for account ID: {}, amount: {}", request.getAccountId(), request.getAmount());

        try {
            // CRITICAL: Always load FRESH account data from DB on each retry
            Account account = accountRepository.findById(request.getAccountId())
                    .orElseThrow(() -> {
                        log.error("Deposit initiation failed - Account not found for account ID: {}",
                                request.getAccountId());
                        return new AccountNotFoundException("Account not found");
                    });

            // Validate account is active
            if (!account.isActive()) {
                log.warn("Account is not active: {}", account.getId());
                logFailedTransaction(account, Transaction.TransactionType.DEPOSIT, request.getAmount(), "Account is not active");
                throw new AccountNotFoundException("Account is not active");
            }

            BigDecimal expectedBalance = account.getBalance().add(request.getAmount());

            Transaction transaction = Transaction.builder()
                    .account(account)
                    .type(Transaction.TransactionType.DEPOSIT)
                    .amount(request.getAmount())
                    .balanceAfter(expectedBalance)
                    .timestamp(LocalDateTime.now())
                    .description("Deposit initiated - awaiting cash verification")
                    .status(Transaction.TransactionStatus.PENDING)
                    .build();

            transactionRepository.save(transaction);

            return TransactionResponse.builder()
                    .transactionId(transaction.getId())
                    .type(transaction.getType().name())
                    .amount(transaction.getAmount())
                    .balanceAfter(transaction.getBalanceAfter())
                    .timestamp(transaction.getTimestamp())
                    .description(transaction.getDescription())
                    .status(transaction.getStatus().name())
                    .success(false)
                    .message("Deposit initiated - awaiting cash verification")
                    .build();
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict on deposit initiation - will retry");
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error during deposit initiation for account ID: {}", request.getAccountId(), e);
            throw new DatabaseException("Failed to initiate deposit due to database error", e);
        }
    }

    @Transactional
    @Retryable(
        retryFor = {OptimisticLockException.class, ObjectOptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 1)
    )
    public TransactionResponse completeDeposit(CompleteTransactionRequest request) {
        log.info("Completing deposit transaction ID: {} with status: {}", request.getTransactionId(), request.getStatus());

        try {
            Transaction transaction = transactionRepository.findById(request.getTransactionId())
                    .orElseThrow(() -> {
                        log.error("Deposit completion failed - Transaction not found for transaction ID: {}",
                                request.getTransactionId());
                        return new AccountNotFoundException("Transaction not found");
                    });

            if (transaction.getStatus() != Transaction.TransactionStatus.PENDING) {
                String errorMsg = String.format("Transaction %d is not in PENDING status (current: %s)",
                        request.getTransactionId(), transaction.getStatus());
                log.error("Deposit completion failed - {}", errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            if (transaction.getType() != Transaction.TransactionType.DEPOSIT) {
                String errorMsg = String.format("Transaction %d is not a deposit (current type: %s)",
                        request.getTransactionId(), transaction.getType());
                log.error("Deposit completion failed - {}", errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            Transaction.TransactionStatus newStatus;
            try {
                newStatus = Transaction.TransactionStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.error("Deposit completion failed - Invalid status: {} for transaction ID: {}",
                        request.getStatus(), request.getTransactionId());
                throw new IllegalArgumentException("Invalid status: " + request.getStatus());
            }

            Account account = accountRepository.findById(transaction.getAccount().getId())
                    .orElseThrow(() -> new AccountNotFoundException("Account not found"));

            if (newStatus == Transaction.TransactionStatus.SUCCESS) {
                // Cash was inserted and verified - complete the deposit
                BigDecimal newBalance = account.getBalance().add(transaction.getAmount());
                account.setBalance(newBalance);
                account.setAvailableBalance(account.getAvailableBalance().add(transaction.getAmount()));

                transaction.setStatus(Transaction.TransactionStatus.SUCCESS);
                transaction.setBalanceAfter(account.getBalance());
                transaction.setDescription("Cash deposit completed");

                accountRepository.save(account);
                transactionRepository.save(transaction);

                log.info("Deposit completed successfully. New balance: {}, Available balance: {}",
                        account.getBalance(), account.getAvailableBalance());

                return buildResponse(transaction, true, "Deposit completed successfully");

            } else if (newStatus == Transaction.TransactionStatus.FAILED ||
                       newStatus == Transaction.TransactionStatus.DECLINED) {
                // Deposit failed or declined - no balance changes
                transaction.setStatus(newStatus);
                transaction.setBalanceAfter(account.getBalance());
                transaction.setDescription(request.getReason() != null ?
                        request.getReason() : "Deposit " + newStatus.name());

                transactionRepository.save(transaction);

                log.warn("Deposit failed: {}. No balance changes", transaction.getDescription());

                return buildResponse(transaction, false,
                        "Deposit " + newStatus.name().toLowerCase());
            } else {
                throw new IllegalArgumentException("Unsupported status: " + request.getStatus());
            }

        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict on deposit completion - will retry");
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error during deposit completion for transaction ID: {}",
                    request.getTransactionId(), e);
            throw new DatabaseException("Failed to complete deposit due to database error", e);
        }
    }

    private TransactionResponse buildResponse(Transaction transaction, boolean success, String message) {
        return TransactionResponse.builder()
                .transactionId(transaction.getId())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .balanceAfter(transaction.getBalanceAfter())
                .timestamp(transaction.getTimestamp())
                .description(transaction.getDescription())
                .status(transaction.getStatus().name())
                .success(success)
                .message(message)
                .build();
    }

    private void logFailedTransaction(Account account, Transaction.TransactionType type,
                                      BigDecimal amount, String errorMessage) {
        try {
            Transaction failedTransaction = Transaction.builder()
                    .account(account)
                    .type(type)
                    .amount(amount)
                    .balanceAfter(account.getBalance())
                    .timestamp(LocalDateTime.now())
                    .description(errorMessage)
                    .status(Transaction.TransactionStatus.FAILED)
                    .build();

            transactionRepository.save(failedTransaction);
            log.warn("Failed transaction logged: {} - {}", type, errorMessage);
        } catch (Exception e) {
            log.error("Failed to log failed transaction", e);
        }
    }
}

