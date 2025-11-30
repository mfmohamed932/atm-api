package com.heb.atm.service;

import com.heb.atm.dto.TransactionResponse;
import com.heb.atm.exception.AccountNotFoundException;
import com.heb.atm.exception.DatabaseException;
import com.heb.atm.model.Account;
import com.heb.atm.model.Transaction;
import com.heb.atm.repository.AccountRepository;
import com.heb.atm.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionHistoryService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public List<TransactionResponse> getTransactionHistory(Long accountId) {
        log.info("Getting transaction history for account ID: {}", accountId);

        try {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> {
                        log.error("Transaction history retrieval failed - Account not found for account ID: {}",
                                accountId);
                        return new AccountNotFoundException("Account not found");
                    });

            List<Transaction> transactions = transactionRepository
                    .findByAccountIdOrderByTimestampDesc(accountId);

            return transactions.stream()
                    .map(t -> TransactionResponse.builder()
                            .transactionId(t.getId())
                            .type(t.getType().name())
                            .amount(t.getAmount())
                            .balanceAfter(t.getBalanceAfter())
                            .timestamp(t.getTimestamp())
                            .description(t.getDescription())
                            .status(t.getStatus().name())
                            .success(t.getStatus() == Transaction.TransactionStatus.SUCCESS)
                            .message(null)
                            .build())
                    .collect(Collectors.toList());
        } catch (DataAccessException e) {
            log.error("Database error while retrieving transaction history for account ID: {}",
                    accountId, e);
            throw new DatabaseException("Failed to retrieve transaction history due to database error", e);
        }
    }
}

