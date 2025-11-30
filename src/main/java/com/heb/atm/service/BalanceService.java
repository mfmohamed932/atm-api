package com.heb.atm.service;

import com.heb.atm.dto.BalanceResponse;
import com.heb.atm.exception.AccountNotFoundException;
import com.heb.atm.exception.DatabaseException;
import com.heb.atm.model.Account;
import com.heb.atm.repository.AccountRepository;
import com.heb.atm.util.MaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceService {

    private final AccountRepository accountRepository;

    public BalanceResponse getBalance(Long accountId) {
        log.info("Getting balance for account ID: {}", accountId);

        try {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> {
                        log.error("Account not found for account ID: {}", accountId);
                        return new AccountNotFoundException("Account not found");
                    });

            resetDailyLimitIfNeeded(account);

            return BalanceResponse.builder()
                    .maskedCardNumber(MaskingUtil.maskCardNumber(account.getCardNumber()))
                    .customerName(account.getCustomerName())
                    .balance(account.getBalance())
                    .availableBalance(account.getAvailableBalance())
                    .dailyWithdrawalLimit(account.getDailyWithdrawalLimit())
                    .remainingDailyLimit(account.getRemainingDailyLimit())
                    .build();
        } catch (DataAccessException e) {
            log.error("Database error while getting balance for account ID: {}", accountId, e);
            throw new DatabaseException("Failed to retrieve balance due to database error", e);
        }
    }

    private void resetDailyLimitIfNeeded(Account account) {
        if (account.getLastWithdrawalDate() == null ||
                !account.getLastWithdrawalDate().equals(LocalDate.now())) {
            account.setDailyWithdrawnAmount(BigDecimal.ZERO);
            account.setLastWithdrawalDate(LocalDate.now());
            try {
                accountRepository.save(account);
            } catch (DataAccessException e) {
                log.error("Database error while resetting daily limit for account ID: {}",
                        account.getId(), e);
                throw new DatabaseException("Failed to reset daily limit due to database error", e);
            }
        }
    }
}

