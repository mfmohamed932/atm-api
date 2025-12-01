package com.heb.atm.controller;

import com.heb.atm.dto.*;
import com.heb.atm.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("v1/atm")
@RequiredArgsConstructor
@Tag(name = "ATM API", description = "APIs for ATM operations")
@CrossOrigin(origins = "*")
public class AtmController {

    private final AuthenticationService authenticationService;
    private final BalanceService balanceService;
    private final WithdrawalService withdrawalService;
    private final DepositService depositService;
    private final TransactionHistoryService transactionHistoryService;

    @PostMapping("/authenticate")
    @Operation(summary = "Authenticate customer", description = "Authenticate customer with 16-digit card number and PIN")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = com.heb.atm.exception.ErrorResponse.class)))
    })
    public ResponseEntity<AuthResponse> authenticate(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authenticationService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/balance/{accountId}")
    @Operation(summary = "Get account balance", description = "Get current account balance and daily withdrawal limit")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balance retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = com.heb.atm.exception.ErrorResponse.class)))
    })
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable Long accountId) {
        BalanceResponse response = balanceService.getBalance(accountId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/withdraw/initiate")
    @Operation(summary = "Initiate withdrawal (Phase 1)",
               description = "Initiate a withdrawal transaction - creates PENDING transaction. Requires accountId from authentication. ATM machine should then call /withdraw/complete")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Withdrawal initiated with PENDING status",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Insufficient funds or daily limit exceeded",
                    content = @Content(schema = @Schema(implementation = com.heb.atm.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = com.heb.atm.exception.ErrorResponse.class)))
    })
    public ResponseEntity<TransactionResponse> initiateWithdrawal(@Valid @RequestBody WithdrawalRequest request) {
        TransactionResponse response = withdrawalService.initiateWithdrawal(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/withdraw/complete")
    @Operation(summary = "Complete transaction (Phase 2)",
               description = "Complete a PENDING transaction with SUCCESS, FAILED, or DECLINED status after ATM operation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction completed",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid transaction or status",
                    content = @Content(schema = @Schema(implementation = com.heb.atm.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transaction not found",
                    content = @Content(schema = @Schema(implementation = com.heb.atm.exception.ErrorResponse.class)))
    })
    public ResponseEntity<TransactionResponse> completeTransaction(@Valid @RequestBody CompleteTransactionRequest request) {
        TransactionResponse response = withdrawalService.completeTransaction(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/deposit/initiate")
    @Operation(summary = "Initiate deposit (Phase 1)",
               description = "Initiate a deposit transaction - creates PENDING transaction. Requires accountId from authentication. Customer should then insert cash into ATM, then call /deposit/complete")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deposit initiated with PENDING status",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = com.heb.atm.exception.ErrorResponse.class)))
    })
    public ResponseEntity<TransactionResponse> initiateDeposit(@Valid @RequestBody DepositRequest request) {
        TransactionResponse response = depositService.initiateDeposit(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/deposit/complete")
    @Operation(summary = "Complete deposit (Phase 2)",
               description = "Complete a PENDING deposit transaction with SUCCESS, FAILED, or DECLINED status after ATM cash verification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deposit completed",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid transaction or status",
                    content = @Content(schema = @Schema(implementation = com.heb.atm.exception.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transaction not found",
                    content = @Content(schema = @Schema(implementation = com.heb.atm.exception.ErrorResponse.class)))
    })
    public ResponseEntity<TransactionResponse> completeDeposit(@Valid @RequestBody CompleteTransactionRequest request) {
        TransactionResponse response = depositService.completeDeposit(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/{accountId}")
    @Operation(summary = "Get transaction history", description = "Get transaction history for an account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = com.heb.atm.exception.ErrorResponse.class)))
    })
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory(@PathVariable Long accountId) {
        List<TransactionResponse> response = transactionHistoryService.getTransactionHistory(accountId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the API is running")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ATM API is running");
    }
}


