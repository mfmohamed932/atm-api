package com.heb.atm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to complete/finalize a pending transaction")
public class CompleteTransactionRequest {

    @NotNull(message = "Transaction ID is required")
    @Schema(description = "Transaction ID to complete", example = "123")
    private Long transactionId;

    @NotNull(message = "Status is required")
    @Schema(description = "Final status", example = "SUCCESS", allowableValues = {"SUCCESS", "FAILED", "DECLINED"})
    private String status;

    @Schema(description = "Reason for failure/decline", example = "Machine error: Unable to dispense cash")
    private String reason;
}

