package com.heb.atm.service;

import com.heb.atm.dto.AuthRequest;
import com.heb.atm.dto.AuthResponse;
import com.heb.atm.exception.AccountNotFoundException;
import com.heb.atm.exception.DatabaseException;
import com.heb.atm.exception.InvalidPinException;
import com.heb.atm.model.Account;
import com.heb.atm.repository.AccountRepository;
import com.heb.atm.util.EncryptionUtil;
import com.heb.atm.util.MaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final AccountRepository accountRepository;
    private final EncryptionUtil encryptionUtil;

    public AuthResponse authenticate(AuthRequest request) {
        log.info("Authentication attempt with encrypted credentials");

        try {
            // Decrypt card number and PIN from frontend
            String decryptedCardNumber = encryptionUtil.decryptCardNumber(request.getCardNumber());
            String decryptedPIN = encryptionUtil.decryptPIN(request.getPin());

            log.info("Decryption successful, authenticating for card: {}", MaskingUtil.maskCardNumber(decryptedCardNumber));

            Account account = accountRepository.findByCardNumberAndPin(
                    decryptedCardNumber,
                    decryptedPIN
            ).orElseThrow(() -> {
                log.error("Authentication failed - Invalid card number or PIN");
                return new InvalidPinException("Invalid card number or PIN");
            });

            if (!account.isActive()) {
                log.error("Authentication failed - Account is not active for account ID: {}", account.getId());
                throw new AccountNotFoundException("Account is not active");
            }

            log.info("Authentication successful for account ID: {}", account.getId());
            return AuthResponse.builder()
                    .authenticated(true)
                    .accountId(account.getId())
                    .customerName(account.getCustomerName())
                    .message("Authentication successful")
                    .build();
        } catch (IllegalArgumentException e) {
            log.error("Decryption failed - Invalid encrypted data format", e);
            throw new InvalidPinException("Invalid card number or PIN format");
        } catch (DataAccessException e) {
            log.error("Database error during authentication", e);
            throw new DatabaseException("Failed to authenticate due to database error", e);
        } catch (Exception e) {
            log.error("Authentication failed with unexpected error", e);
            throw new InvalidPinException("Authentication failed");
        }
    }
}

