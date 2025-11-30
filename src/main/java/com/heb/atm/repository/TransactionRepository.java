package com.heb.atm.repository;

import com.heb.atm.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountIdOrderByTimestampDesc(Long accountId);
    List<Transaction> findByAccountIdAndTimestampBetweenOrderByTimestampDesc(
            Long accountId, LocalDateTime start, LocalDateTime end);
}
