package com.heb.atm.repository;

import com.heb.atm.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByCardNumber(String cardNumber);
    Optional<Account> findByCardNumberAndPin(String cardNumber, String pin);
    boolean existsByCardNumber(String cardNumber);
}

