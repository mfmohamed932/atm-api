package com.heb.atm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String cardNumber;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String pin;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false)
    private BigDecimal availableBalance;

    @Column(nullable = false)
    private BigDecimal dailyWithdrawalLimit;

    @Column(nullable = false)
    private BigDecimal dailyWithdrawnAmount;

    @Column(nullable = false)
    private LocalDate lastWithdrawalDate;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Transaction> transactions = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    public BigDecimal getRemainingDailyLimit() {
        if (lastWithdrawalDate == null || !lastWithdrawalDate.equals(LocalDate.now())) {
            dailyWithdrawnAmount = BigDecimal.ZERO;
        }
        return dailyWithdrawalLimit.subtract(dailyWithdrawnAmount);
    }
}
