package com.heb.atm.service;

import com.heb.atm.dto.CompleteTransactionRequest;
import com.heb.atm.dto.TransactionResponse;
import com.heb.atm.dto.WithdrawalRequest;
import com.heb.atm.exception.*;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    @Retryable(
        retryFor = {OptimisticLockException.class, ObjectOptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 1)
    )
    public TransactionResponse initiateWithdrawal(WithdrawalRequest request) {
        log.info("Initiating withdrawal for account ID: {}, amount: {}",
                request.getAccountId(), request.getAmount());

        try {
            // CRITICAL: Always load FRESH account data from DB on each retry
            Account account = accountRepository.findById(request.getAccountId())
                    .orElseThrow(() -> {
                        log.error("Withdrawal initiation failed - Account not found for account ID: {}",
                                request.getAccountId());
                        return new AccountNotFoundException("Account not found");
                    });

            // Validate account is active
            if (!account.isActive()) {
                log.warn("Account is not active: {}", request.getAccountId());
                logFailedTransaction(account, Transaction.TransactionType.WITHDRAWAL,
                        request.getAmount(), "Account is not active");
                throw new AccountNotFoundException("Account is not active");
            }

            // Reset daily limit if needed (idempotent - checks date first)
            resetDailyLimitIfNeeded(account);

            // RE-VALIDATE with FRESH data on each retry
            // Check available balance using CURRENT values from DB
            if (account.getAvailableBalance().compareTo(request.getAmount()) < 0) {
                String errorMsg = String.format("Insufficient funds. Available balance: $%.2f",
                        account.getAvailableBalance());
                log.error("Withdrawal initiation failed - {} for account ID: {}",
                        errorMsg, request.getAccountId());
                logDeclinedTransaction(account, Transaction.TransactionType.WITHDRAWAL,
                        request.getAmount(), errorMsg);
                throw new InsufficientFundsException(errorMsg);
            }

            // RE-CALCULATE daily limit with FRESH current values
            BigDecimal newDailyWithdrawn = account.getDailyWithdrawnAmount().add(request.getAmount());
            if (newDailyWithdrawn.compareTo(account.getDailyWithdrawalLimit()) > 0) {
                String errorMsg = String.format("Daily withdrawal limit exceeded. Remaining limit: $%.2f",
                        account.getRemainingDailyLimit());
                log.error("Withdrawal initiation failed - {} for account ID: {}",
                        errorMsg, request.getAccountId());
                logDeclinedTransaction(account, Transaction.TransactionType.WITHDRAWAL,
                        request.getAmount(), errorMsg);
                throw new DailyLimitExceededException(errorMsg);
            }

            // IDEMPOTENT STATE CHANGES:
            // Subtract from available balance (based on FRESH current value)
            account.setAvailableBalance(account.getAvailableBalance().subtract(request.getAmount()));

            // Calculate expected balance after withdrawal completes
            BigDecimal expectedBalance = account.getBalance().subtract(request.getAmount());

            // CRITICAL: Create transaction ONLY ONCE per successful save
            Transaction transaction = Transaction.builder()
                    .account(account)
                    .type(Transaction.TransactionType.WITHDRAWAL)
                    .amount(request.getAmount())
                    .balanceAfter(expectedBalance)
                    .timestamp(LocalDateTime.now())
                    .description("Withdrawal initiated - awaiting ATM confirmation")
                    .status(Transaction.TransactionStatus.PENDING)
                    .build();

            // ATOMIC SAVE: If this fails with OptimisticLockException:
            // - Transaction is NOT saved (rollback)
            // - Method retries from the beginning
            // - Fresh account data is loaded
            // - New transaction object is created (not duplicate)
            accountRepository.save(account); // This throws OptimisticLockException on version conflict
            transactionRepository.save(transaction); // Only reached if account save succeeds

            log.info("Withdrawal initiated with PENDING status. Transaction ID: {}", transaction.getId());

            return TransactionResponse.builder()
                    .transactionId(transaction.getId())
                    .type(transaction.getType().name())
                    .amount(transaction.getAmount())
                    .balanceAfter(transaction.getBalanceAfter())
                    .timestamp(transaction.getTimestamp())
                    .description(transaction.getDescription())
                    .status(transaction.getStatus().name())
                    .success(false)
                    .message("Transaction initiated - please proceed with ATM operation")
                    .build();

        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            // Let Spring Retry handle this - will retry the entire method
            log.warn("Optimistic lock conflict on withdrawal initiation - will retry");
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error during withdrawal initiation for account ID: {}",
                    request.getAccountId(), e);
            throw new DatabaseException("Failed to initiate withdrawal due to database error", e);
        }
    }

    @Transactional
    @Retryable(
        retryFor = {OptimisticLockException.class, ObjectOptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 1)
    )
    public TransactionResponse completeTransaction(CompleteTransactionRequest request) {
        log.info("Completing transaction ID: {} with status: {}",
                request.getTransactionId(), request.getStatus());

        try {
            // CRITICAL: Always load FRESH transaction and account data on each retry
            Transaction transaction = transactionRepository.findById(request.getTransactionId())
                    .orElseThrow(() -> {
                        log.error("Transaction completion failed - Transaction not found for transaction ID: {}",
                                request.getTransactionId());
                        return new AccountNotFoundException("Transaction not found");
                    });

            // Idempotent check - safe to repeat on retry
            if (transaction.getStatus() != Transaction.TransactionStatus.PENDING) {
                String errorMsg = String.format("Transaction %d is not in PENDING status (current: %s)",
                        request.getTransactionId(), transaction.getStatus());
                log.error("Transaction completion failed - {}", errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            Transaction.TransactionStatus newStatus;
            try {
                newStatus = Transaction.TransactionStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.error("Transaction completion failed - Invalid status: {} for transaction ID: {}",
                        request.getStatus(), request.getTransactionId());
                throw new IllegalArgumentException("Invalid status: " + request.getStatus());
            }

            // Load FRESH account data (critical for retry safety)
            Account account = accountRepository.findById(transaction.getAccount().getId())
                    .orElseThrow(() -> new AccountNotFoundException("Account not found"));

            if (newStatus == Transaction.TransactionStatus.SUCCESS) {
                // ATM successfully dispensed cash - complete the transaction
                // IDEMPOTENT STATE CHANGES: All calculations based on FRESH data

                // Deduct from actual balance (availableBalance already reduced on initiate)
                // Use FRESH account.getBalance() from DB
                account.setBalance(account.getBalance().subtract(transaction.getAmount()));

                // Update daily withdrawn amount with FRESH current value
                account.setDailyWithdrawnAmount(
                    account.getDailyWithdrawnAmount().add(transaction.getAmount())
                );

                account.setLastWithdrawalDate(LocalDate.now());

                transaction.setStatus(Transaction.TransactionStatus.SUCCESS);
                transaction.setBalanceAfter(account.getBalance());
                transaction.setDescription("Cash withdrawal completed");

                // ATOMIC: Both saved together - if account save fails, transaction rollback
                accountRepository.save(account); // Throws OptimisticLockException on conflict
                transactionRepository.save(transaction);

                log.info("Transaction completed successfully. New balance: {}, Available balance: {}",
                        account.getBalance(), account.getAvailableBalance());

                return buildResponse(transaction, true, "Transaction completed successfully");

            } else if (newStatus == Transaction.TransactionStatus.FAILED ||
                       newStatus == Transaction.TransactionStatus.DECLINED) {
                // ATM failed to dispense cash or declined - rollback
                // Restore available balance using FRESH current value
                account.setAvailableBalance(
                    account.getAvailableBalance().add(transaction.getAmount())
                );

                transaction.setStatus(newStatus);
                transaction.setBalanceAfter(account.getBalance());
                transaction.setDescription(request.getReason() != null ?
                        request.getReason() : "Transaction declined");

                // ATOMIC: Both saved together - if account save fails, transaction rollback
                accountRepository.save(account); // Throws OptimisticLockException on conflict
                transactionRepository.save(transaction);

                log.warn("Transaction failed: {}. Available balance restored to: {}",
                        transaction.getDescription(), account.getAvailableBalance());

                return buildResponse(transaction, false,
                        "Transaction " + newStatus.name().toLowerCase());
            } else {
                throw new IllegalArgumentException("Unsupported status: " + request.getStatus());
            }

        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            // Let Spring Retry handle this - will retry the entire method
            log.warn("Optimistic lock conflict on transaction completion - will retry");
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error during transaction completion for transaction ID: {}",
                    request.getTransactionId(), e);
            throw new DatabaseException("Failed to complete transaction due to database error", e);
        }
    }

    // Legacy method for backward compatibility
    @Transactional
    public TransactionResponse withdraw(WithdrawalRequest request) {
        // Initiate the withdrawal
        TransactionResponse initiateResponse = initiateWithdrawal(request);

        // Immediately complete it for legacy flow
        CompleteTransactionRequest complete = new CompleteTransactionRequest();
        complete.setTransactionId(initiateResponse.getTransactionId());
        complete.setStatus("SUCCESS");

        return completeTransaction(complete);
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

    private void resetDailyLimitIfNeeded(Account account) {
        if (account.getLastWithdrawalDate() == null ||
                !account.getLastWithdrawalDate().equals(LocalDate.now())) {
            account.setDailyWithdrawnAmount(BigDecimal.ZERO);
            account.setLastWithdrawalDate(LocalDate.now());
            accountRepository.save(account);
        }
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

    private void logDeclinedTransaction(Account account, Transaction.TransactionType type,
                                       BigDecimal amount, String reason) {
        try {
            Transaction declinedTransaction = Transaction.builder()
                    .account(account)
                    .type(type)
                    .amount(amount)
                    .balanceAfter(account.getBalance())
                    .timestamp(LocalDateTime.now())
                    .description(reason)
                    .status(Transaction.TransactionStatus.DECLINED)
                    .build();

            transactionRepository.save(declinedTransaction);
            log.warn("Declined transaction logged: {} - {}", type, reason);
        } catch (Exception e) {
            log.error("Failed to log declined transaction", e);
        }
    }
}

