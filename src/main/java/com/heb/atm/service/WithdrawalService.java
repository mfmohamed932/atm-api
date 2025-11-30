package com.heb.atm.service;

import com.heb.atm.dto.CompleteTransactionRequest;
import com.heb.atm.dto.TransactionResponse;
import com.heb.atm.dto.WithdrawalRequest;
import com.heb.atm.exception.*;
import com.heb.atm.model.Account;
import com.heb.atm.model.Transaction;
import com.heb.atm.repository.AccountRepository;
import com.heb.atm.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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
    public TransactionResponse initiateWithdrawal(WithdrawalRequest request) {
        log.info("Initiating withdrawal for account ID: {}, amount: {}",
                request.getAccountId(), request.getAmount());

        try {
            Account account = accountRepository.findById(request.getAccountId())
                    .orElseThrow(() -> {
                        log.error("Withdrawal initiation failed - Account not found for account ID: {}",
                                request.getAccountId());
                        return new AccountNotFoundException("Account not found");
                    });


            if (!account.isActive()) {
                log.error("Withdrawal initiation failed - Account is not active for account ID: {}",
                        request.getAccountId());
                logFailedTransaction(account, Transaction.TransactionType.WITHDRAWAL,
                        request.getAmount(), "Account is not active");
                throw new AccountNotFoundException("Account is not active");
            }

            resetDailyLimitIfNeeded(account);

            // Check available balance (not regular balance)
            if (account.getAvailableBalance().compareTo(request.getAmount()) < 0) {
                String errorMsg = String.format("Insufficient funds. Available balance: $%.2f",
                        account.getAvailableBalance());
                log.error("Withdrawal initiation failed - {} for account ID: {}",
                        errorMsg, request.getAccountId());
                logDeclinedTransaction(account, Transaction.TransactionType.WITHDRAWAL,
                        request.getAmount(), errorMsg);
                throw new InsufficientFundsException(errorMsg);
            }

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

            // Reserve the funds - reduce available balance
            BigDecimal newAvailableBalance = account.getAvailableBalance().subtract(request.getAmount());
            account.setAvailableBalance(newAvailableBalance);

            BigDecimal expectedBalance = account.getBalance().subtract(request.getAmount());

            Transaction transaction = Transaction.builder()
                    .account(account)
                    .type(Transaction.TransactionType.WITHDRAWAL)
                    .amount(request.getAmount())
                    .balanceAfter(expectedBalance)
                    .timestamp(LocalDateTime.now())
                    .description("Withdrawal initiated - awaiting ATM confirmation")
                    .status(Transaction.TransactionStatus.PENDING)
                    .build();

            accountRepository.save(account); // Save the reduced available balance
            transactionRepository.save(transaction);

            log.info("Withdrawal initiated with PENDING status. Transaction ID: {}", transaction.getId());

            return TransactionResponse.builder()
                    .transactionId(transaction.getId())
                    .type(transaction.getType().name())
                    .amount(transaction.getAmount())
                    .balanceAfter(expectedBalance)
                    .timestamp(transaction.getTimestamp())
                    .description(transaction.getDescription())
                    .status(transaction.getStatus().name())
                    .success(false)
                    .message("Transaction initiated - please proceed with ATM operation")
                    .build();
        } catch (DataAccessException e) {
            log.error("Database error during withdrawal initiation for account ID: {}",
                    request.getAccountId(), e);
            throw new DatabaseException("Failed to initiate withdrawal due to database error", e);
        }
    }

    @Transactional
    public TransactionResponse completeTransaction(CompleteTransactionRequest request) {
        log.info("Completing transaction ID: {} with status: {}",
                request.getTransactionId(), request.getStatus());

        try {
            Transaction transaction = transactionRepository.findById(request.getTransactionId())
                    .orElseThrow(() -> {
                        log.error("Transaction completion failed - Transaction not found for transaction ID: {}",
                                request.getTransactionId());
                        return new AccountNotFoundException("Transaction not found");
                    });

            if (transaction.getStatus() != Transaction.TransactionStatus.PENDING) {
                String errorMsg = String.format("Transaction %d is not in PENDING status (current: %s)",
                        request.getTransactionId(), transaction.getStatus());
                log.error("Transaction completion failed - {}", errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            Account account = transaction.getAccount();
            Transaction.TransactionStatus newStatus;

            try {
                newStatus = Transaction.TransactionStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.error("Transaction completion failed - Invalid status: {} for transaction ID: {}",
                        request.getStatus(), request.getTransactionId());
                throw new IllegalArgumentException("Invalid status: " + request.getStatus());
            }

            if (newStatus == Transaction.TransactionStatus.SUCCESS) {
                // ATM successfully dispensed cash - complete the transaction
                // Deduct from actual balance (availableBalance already reduced on initiate)
                BigDecimal newBalance = account.getBalance().subtract(transaction.getAmount());
                account.setBalance(newBalance);
                // availableBalance remains as is (already deducted)

                BigDecimal newDailyWithdrawn = account.getDailyWithdrawnAmount().add(transaction.getAmount());
                account.setDailyWithdrawnAmount(newDailyWithdrawn);
                account.setLastWithdrawalDate(LocalDate.now());

                transaction.setStatus(Transaction.TransactionStatus.SUCCESS);
                transaction.setBalanceAfter(newBalance);
                transaction.setDescription("Cash withdrawal completed");

                accountRepository.save(account);
                log.info("Transaction completed successfully. New balance: {}, Available balance: {}",
                        newBalance, account.getAvailableBalance());

            } else if (newStatus == Transaction.TransactionStatus.FAILED) {
                // ATM machine error - rollback
                // Restore the available balance (add back the amount)
                BigDecimal restoredAvailableBalance = account.getAvailableBalance().add(transaction.getAmount());
                account.setAvailableBalance(restoredAvailableBalance);

                transaction.setStatus(Transaction.TransactionStatus.FAILED);
                transaction.setBalanceAfter(account.getBalance());
                transaction.setDescription(request.getReason() != null ?
                        "ATM Error: " + request.getReason() : "ATM machine error");

                accountRepository.save(account);
                log.warn("Transaction failed: {}. Available balance restored to: {}",
                        transaction.getDescription(), restoredAvailableBalance);

            } else if (newStatus == Transaction.TransactionStatus.DECLINED) {
                // Business rule violation after initiation - rollback
                // Restore the available balance (add back the amount)
                BigDecimal restoredAvailableBalance = account.getAvailableBalance().add(transaction.getAmount());
                account.setAvailableBalance(restoredAvailableBalance);

                transaction.setStatus(Transaction.TransactionStatus.DECLINED);
                transaction.setBalanceAfter(account.getBalance());
                transaction.setDescription(request.getReason() != null ?
                        request.getReason() : "Transaction declined");

                accountRepository.save(account);
                log.warn("Transaction declined: {}. Available balance restored to: {}",
                        transaction.getDescription(), restoredAvailableBalance);
            }

            transactionRepository.save(transaction);

            return TransactionResponse.builder()
                    .transactionId(transaction.getId())
                    .type(transaction.getType().name())
                    .amount(transaction.getAmount())
                    .balanceAfter(transaction.getBalanceAfter())
                    .timestamp(transaction.getTimestamp())
                    .description(transaction.getDescription())
                    .status(transaction.getStatus().name())
                    .success(transaction.getStatus() == Transaction.TransactionStatus.SUCCESS)
                    .message(transaction.getStatus() == Transaction.TransactionStatus.SUCCESS ?
                            "Transaction completed successfully" :
                            "Transaction " + transaction.getStatus().name().toLowerCase())
                    .build();
        } catch (DataAccessException e) {
            log.error("Database error during transaction completion for transaction ID: {}", request.getTransactionId(), e);
            throw new DatabaseException("Failed to complete transaction due to database error", e);
        }
    }

    // Legacy method for backward compatibility
    @Transactional
    public TransactionResponse withdraw(WithdrawalRequest request) {
        // Initiate and immediately complete (for old flow compatibility)
        TransactionResponse initiated = initiateWithdrawal(request);

        CompleteTransactionRequest complete = new CompleteTransactionRequest();
        complete.setTransactionId(initiated.getTransactionId());
        complete.setStatus("SUCCESS");

        return completeTransaction(complete);
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

