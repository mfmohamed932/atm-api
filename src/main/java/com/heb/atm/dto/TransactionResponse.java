package com.heb.atm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Transaction response")
public class TransactionResponse {
    @Schema(description = "Transaction ID", example = "1")
    private Long transactionId;

    @Schema(description = "Transaction type", example = "WITHDRAWAL")
    private String type;

    @Schema(description = "Transaction amount", example = "100.00")
    private BigDecimal amount;

    @Schema(description = "Balance after transaction", example = "4900.00")
    private BigDecimal balanceAfter;

    @Schema(description = "Transaction timestamp", example = "2025-11-27T10:30:00")
    private LocalDateTime timestamp;

    @Schema(description = "Transaction description", example = "Cash withdrawal")
    private String description;

    @Schema(description = "Transaction status", example = "SUCCESS", allowableValues = {"PENDING", "SUCCESS", "FAILED", "DECLINED"})
    private String status;

    @Schema(description = "Success status", example = "true")
    private boolean success;

    @Schema(description = "Message", example = "Withdrawal successful")
    private String message;
}

