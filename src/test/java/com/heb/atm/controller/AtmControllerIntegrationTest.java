package com.heb.atm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heb.atm.dto.*;
import com.heb.atm.model.Account;
import com.heb.atm.repository.AccountRepository;
import com.heb.atm.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AtmControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();

        testAccount = new Account();
        testAccount.setAccountNumber("9999");
        testAccount.setCustomerName("Test User");
        testAccount.setPin("1111");
        testAccount.setBalance(new BigDecimal("3000.00"));
        testAccount.setDailyWithdrawalLimit(new BigDecimal("1000.00"));
        testAccount.setDailyWithdrawnAmount(BigDecimal.ZERO);
        testAccount.setLastWithdrawalDate(LocalDate.now());
        testAccount.setActive(true);
        testAccount = accountRepository.save(testAccount);
    }

    @Test
    void testAuthenticate_Success() throws Exception {
        AuthRequest request = new AuthRequest("9999", "1111");

        mockMvc.perform(post("/api/atm/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.customerName").value("Test User"));
    }

    @Test
    void testAuthenticate_InvalidPin() throws Exception {
        AuthRequest request = new AuthRequest("9999", "0000");

        mockMvc.perform(post("/api/atm/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetBalance() throws Exception {
        mockMvc.perform(get("/api/atm/balance/{accountId}", testAccount.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("9999"))
                .andExpect(jsonPath("$.balance").value(3000.00))
                .andExpect(jsonPath("$.dailyWithdrawalLimit").value(1000.00));
    }

    @Test
    void testWithdraw_Success() throws Exception {
        WithdrawalRequest request = new WithdrawalRequest(
                testAccount.getId(),
                new BigDecimal("200.00"),
                "1111"
        );

        mockMvc.perform(post("/api/atm/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.balanceAfter").value(2800.00));
    }

    @Test
    void testWithdraw_InsufficientFunds() throws Exception {
        WithdrawalRequest request = new WithdrawalRequest(
                testAccount.getId(),
                new BigDecimal("5000.00"),
                "1111"
        );

        mockMvc.perform(post("/api/atm/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDeposit_Success() throws Exception {
        DepositRequest request = new DepositRequest(
                testAccount.getId(),
                new BigDecimal("500.00"),
                "1111"
        );

        mockMvc.perform(post("/api/atm/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.balanceAfter").value(3500.00));
    }

    @Test
    void testGetTransactionHistory() throws Exception {
        // First make a deposit to create a transaction
        DepositRequest depositRequest = new DepositRequest(
                testAccount.getId(),
                new BigDecimal("100.00"),
                "1111"
        );

        mockMvc.perform(post("/api/atm/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depositRequest)));

        // Then get transaction history
        mockMvc.perform(get("/api/atm/transactions/{accountId}", testAccount.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$[0].amount").value(100.00));
    }

    @Test
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/api/atm/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("ATM API is running"));
    }
}

