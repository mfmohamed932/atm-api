package com.heb.atm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication request with encrypted card number and PIN")
public class AuthRequest {
    @NotBlank(message = "Card number is required")
    @Schema(description = "Encrypted card number (Base64 encoded)", example = "VGhpcyBpcyBhIGJhc2U2NCBlbmNvZGVk...")
    private String cardNumber;

    @NotBlank(message = "PIN is required")
    @Schema(description = "Encrypted PIN (Base64 encoded)", example = "QW5vdGhlciBiYXNlNjQ...")
    private String pin;
}

