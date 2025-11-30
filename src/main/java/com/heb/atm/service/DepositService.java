package com.heb.atm.service;

import com.heb.atm.dto.DepositRequest;
import com.heb.atm.dto.TransactionResponse;
import com.heb.atm.exception.AccountNotFoundException;
import com.heb.atm.exception.DatabaseException;
import com.heb.atm.exception.InvalidPinException;
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
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepositService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionResponse initiateDeposit(DepositRequest request) {
        log.info("Initiating deposit for account ID: {}, amount: {}",
                request.getAccountId(), request.getAmount());

        try {
            Account account = accountRepository.findById(request.getAccountId())
                    .orElseThrow(() -> {
                        log.error("Deposit initiation failed - Account not found for account ID: {}",
                                request.getAccountId());
                        return new AccountNotFoundException("Account not found");
                    });


            if (!account.isActive()) {
                log.error("Deposit initiation failed - Account is not active for account ID: {}",
                        request.getAccountId());
                logFailedTransaction(account, Transaction.TransactionType.DEPOSIT,
                        request.getAmount(), "Account is not active");
                throw new AccountNotFoundException("Account is not active");
            }

            // Create PENDING transaction - balances not updated yet
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

            log.info("Deposit initiated with PENDING status. Transaction ID: {}", transaction.getId());

            return TransactionResponse.builder()
                    .transactionId(transaction.getId())
                    .type(transaction.getType().name())
                    .amount(transaction.getAmount())
                    .balanceAfter(expectedBalance)
                    .timestamp(transaction.getTimestamp())
                    .description(transaction.getDescription())
                    .status(transaction.getStatus().name())
                    .success(false)
                    .message("Deposit initiated - please insert cash into ATM")
                    .build();
        } catch (DataAccessException e) {
            log.error("Database error during deposit initiation for account ID: {}",
                    request.getAccountId(), e);
            throw new DatabaseException("Failed to initiate deposit due to database error", e);
        }
    }

    @Transactional
    public TransactionResponse completeDeposit(Long transactionId, String status, String reason) {
        log.info("Completing deposit transaction ID: {} with status: {}", transactionId, status);

        try {
            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> {
                        log.error("Deposit completion failed - Transaction not found for transaction ID: {}",
                                transactionId);
                        return new AccountNotFoundException("Transaction not found");
                    });

            if (transaction.getStatus() != Transaction.TransactionStatus.PENDING) {
                String errorMsg = String.format("Transaction %d is not in PENDING status (current: %s)",
                        transactionId, transaction.getStatus());
                log.error("Deposit completion failed - {}", errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            if (transaction.getType() != Transaction.TransactionType.DEPOSIT) {
                String errorMsg = String.format("Transaction %d is not a DEPOSIT (type: %s)",
                        transactionId, transaction.getType());
                log.error("Deposit completion failed - {}", errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            Account account = transaction.getAccount();
            Transaction.TransactionStatus newStatus;

            try {
                newStatus = Transaction.TransactionStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.error("Deposit completion failed - Invalid status: {} for transaction ID: {}",
                        status, transactionId);
                throw new IllegalArgumentException("Invalid status: " + status);
            }

            if (newStatus == Transaction.TransactionStatus.SUCCESS) {
                // ATM successfully received cash - update balances
                BigDecimal newBalance = account.getBalance().add(transaction.getAmount());
                account.setBalance(newBalance);

                BigDecimal newAvailableBalance = account.getAvailableBalance().add(transaction.getAmount());
                account.setAvailableBalance(newAvailableBalance);

                transaction.setStatus(Transaction.TransactionStatus.SUCCESS);
                transaction.setBalanceAfter(newBalance);
                transaction.setDescription("Cash deposit completed");

                accountRepository.save(account);
                log.info("Deposit completed successfully. Transaction ID: {}, New balance: {}, Available balance: {}",
                        transaction.getId(), newBalance, newAvailableBalance);

            } else if (newStatus == Transaction.TransactionStatus.FAILED) {
                // ATM machine error - deposit failed
                transaction.setStatus(Transaction.TransactionStatus.FAILED);
                transaction.setBalanceAfter(account.getBalance());
                transaction.setDescription(reason != null ?
                        "ATM Error: " + reason : "ATM machine error - cash not accepted");

                log.warn("Deposit failed: {}. No balance changes for transaction ID: {}",
                        transaction.getDescription(), transactionId);

            } else if (newStatus == Transaction.TransactionStatus.DECLINED) {
                // Business rule violation - deposit declined
                transaction.setStatus(Transaction.TransactionStatus.DECLINED);
                transaction.setBalanceAfter(account.getBalance());
                transaction.setDescription(reason != null ?
                        reason : "Deposit declined");

                log.warn("Deposit declined: {}. No balance changes for transaction ID: {}",
                        transaction.getDescription(), transactionId);
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
                            "Deposit completed successfully" :
                            "Deposit " + transaction.getStatus().name().toLowerCase())
                    .build();
        } catch (DataAccessException e) {
            log.error("Database error during deposit completion for transaction ID: {}", transactionId, e);
            throw new DatabaseException("Failed to complete deposit due to database error", e);
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
}

