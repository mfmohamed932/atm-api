package com.heb.atm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response")
public class AuthResponse {
    @Schema(description = "Authentication status", example = "true")
    private boolean authenticated;

    @Schema(description = "Account ID for subsequent requests", example = "1")
    private Long accountId;

    @Schema(description = "Customer name", example = "John Doe")
    private String customerName;

    @Schema(description = "Message", example = "Authentication successful")
    private String message;
}
