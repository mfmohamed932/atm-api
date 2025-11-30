package com.heb.atm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Account balance information")
public class BalanceResponse {

    @Schema(description = "Masked card number (shows only last 4 digits)", example = "************0366")
    private String maskedCardNumber;

    @Schema(description = "Customer name", example = "John Doe")
    private String customerName;

    @Schema(description = "Current balance (actual account balance)", example = "5000.00")
    private BigDecimal balance;

    @Schema(description = "Available balance (balance minus pending withdrawals)", example = "4900.00")
    private BigDecimal availableBalance;

    @Schema(description = "Daily withdrawal limit", example = "1000.00")
    private BigDecimal dailyWithdrawalLimit;

    @Schema(description = "Remaining daily withdrawal limit", example = "750.00")
    private BigDecimal remainingDailyLimit;
}

