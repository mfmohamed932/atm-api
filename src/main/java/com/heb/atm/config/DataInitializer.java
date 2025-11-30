package com.heb.atm.config;

import com.heb.atm.model.Account;
import com.heb.atm.repository.AccountRepository;
import com.heb.atm.util.MaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;

    @Override
    public void run(String... args) {
        if (accountRepository.count() == 0) {
            log.info("Initializing database with sample data...");

            List<Account> accounts = Arrays.asList(
                    createAccount("4532015112830366", "John Doe", "1234",
                            new BigDecimal("5000.00"), new BigDecimal("1000.00")),
                    createAccount("5425233430109903", "Jane Smith", "5678",
                            new BigDecimal("10000.00"), new BigDecimal("2000.00")),
                    createAccount("4916338506082832", "Bob Johnson", "9012",
                            new BigDecimal("2500.00"), new BigDecimal("500.00")),
                    createAccount("4024007134564842", "Alice Williams", "3456",
                            new BigDecimal("15000.00"), new BigDecimal("3000.00"))
            );

            accountRepository.saveAll(accounts);
            log.info("Sample data initialized successfully. Created {} accounts.", accounts.size());
            log.info("Sample accounts:");
            accounts.forEach(account ->
                log.info("  Card: {}, PIN: {}, Balance: ${}",
                    MaskingUtil.maskCardNumber(account.getCardNumber()),
                    account.getPin(),
                    account.getBalance())
            );
        } else {
            log.info("Database already contains data. Skipping initialization.");
        }
    }

    private Account createAccount(String cardNumber, String customerName, String pin,
                                   BigDecimal balance, BigDecimal dailyLimit) {
        return Account.builder()
                .cardNumber(cardNumber)
                .customerName(customerName)
                .pin(pin)
                .balance(balance)
                .availableBalance(balance) // Initially available balance = balance
                .dailyWithdrawalLimit(dailyLimit)
                .dailyWithdrawnAmount(BigDecimal.ZERO)
                .lastWithdrawalDate(LocalDate.now())
                .active(true)
                .build();
    }
}

