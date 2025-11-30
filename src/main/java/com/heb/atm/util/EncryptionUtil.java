package com.heb.atm.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encryption/Decryption utility for ATM API
 * Uses AES-256-CBC encryption to decrypt card numbers and PINs from frontend
 *
 * Algorithm: AES-256-CBC
 * Key Size: 256 bits (32 bytes)
 * Mode: CBC (Cipher Block Chaining)
 * Padding: PKCS5Padding
 * IV: 128 bits (16 bytes) - sent with encrypted data
 * Format: Base64(IV:EncryptedData)
 */
@Component
@Slf4j
public class EncryptionUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String ENCODING = "UTF-8";

    @Value("${encryption.secret.key:HEB_ATM_SECURE_KEY_2025_32BYTES!}")
    private String secretKey;

    /**
     * Decrypt card number received from frontend
     *
     * @param encryptedCardNumber Base64 encoded encrypted card number
     * @return Decrypted 16-digit card number
     */
    public String decryptCardNumber(String encryptedCardNumber) {
        if (encryptedCardNumber == null || encryptedCardNumber.trim().isEmpty()) {
            log.error("Decryption failed - Card number is null or empty");
            throw new IllegalArgumentException("Encrypted card number cannot be null or empty");
        }

        try {
            String decrypted = decrypt(encryptedCardNumber);
            log.debug("Card number decryption successful");

            // Validate decrypted card number format
            if (!decrypted.matches("^\\d{16}$")) {
                log.error("Decryption failed - Invalid card number format after decryption: {}", MaskingUtil.maskCardNumber(decrypted));
                throw new IllegalArgumentException("Decrypted card number is not valid 16-digit format");
            }

            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt card number", e);
            throw new RuntimeException("Card number decryption failed", e);
        }
    }

    /**
     * Decrypt PIN received from frontend
     *
     * @param encryptedPIN Base64 encoded encrypted PIN
     * @return Decrypted 4-digit PIN
     */
    public String decryptPIN(String encryptedPIN) {
        if (encryptedPIN == null || encryptedPIN.trim().isEmpty()) {
            log.error("Decryption failed - PIN is null or empty");
            throw new IllegalArgumentException("Encrypted PIN cannot be null or empty");
        }

        try {
            String decrypted = decrypt(encryptedPIN);
            log.debug("PIN decryption successful");

            // Validate decrypted PIN format
            if (!decrypted.matches("^\\d{4}$")) {
                log.error("Decryption failed - Invalid PIN format after decryption");
                throw new IllegalArgumentException("Decrypted PIN is not valid 4-digit format");
            }

            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt PIN", e);
            throw new RuntimeException("PIN decryption failed", e);
        }
    }

    /**
     * Core decryption method
     * Format: Base64(HexIV:Base64EncryptedData)
     * Frontend sends: IV as 32 hex characters (16 bytes), encrypted data as Base64
     *
     * @param encryptedData Base64 encoded string with format "HexIV:Base64EncryptedData"
     * @return Decrypted plain text
     */
    private String decrypt(String encryptedData) throws Exception {
        // Decode from Base64 (outer layer)
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);

        // Convert to string to extract IV and encrypted data
        String decoded = new String(encryptedBytes, StandardCharsets.UTF_8);

        // Split IV and encrypted data (format: HexIV:Base64EncryptedData)
        String[] parts = decoded.split(":");
        if (parts.length != 2) {
            log.error("Invalid encrypted data format - expected 'HexIV:Base64EncryptedData'");
            throw new IllegalArgumentException("Invalid encrypted data format");
        }

        String ivHexString = parts[0];
        String encryptedBase64String = parts[1];

        // Convert IV from hex string to bytes (32 hex chars = 16 bytes)
        byte[] iv = hexStringToBytes(ivHexString);

        // Decode encrypted data from Base64
        byte[] encrypted = Base64.getDecoder().decode(encryptedBase64String);

        // Validate IV size (must be 16 bytes for AES)
        if (iv.length != 16) {
            log.error("Invalid IV size: {} bytes (expected 16)", iv.length);
            throw new IllegalArgumentException("Invalid IV size");
        }

        // Create cipher
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(ENCODING), ALGORITHM);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        // Initialize cipher for decryption
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        // Decrypt
        byte[] decrypted = cipher.doFinal(encrypted);

        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * Convert hex string to byte array
     * Example: "a1b2c3d4" -> [0xa1, 0xb2, 0xc3, 0xd4]
     */
    private byte[] hexStringToBytes(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Encrypt data (for testing purposes)
     *
     * @param plainText Plain text to encrypt
     * @return Base64 encoded encrypted data
     */
    public String encrypt(String plainText) throws Exception {
        // Generate random IV
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);

        // Create cipher
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(ENCODING), ALGORITHM);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        // Initialize cipher for encryption
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        // Encrypt
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // Encode IV and encrypted data to Base64
        String ivBase64 = Base64.getEncoder().encodeToString(iv);
        String encryptedBase64 = Base64.getEncoder().encodeToString(encrypted);

        // Combine: IV:EncryptedData
        String combined = ivBase64 + ":" + encryptedBase64;

        // Encode the combined string to Base64
        return Base64.getEncoder().encodeToString(combined.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Test encryption and decryption
     */
    public void testEncryption() {
        try {
            log.info("=== Testing Encryption/Decryption ===");

            // Test card number
            String testCard = "4532015112830366";
            String encryptedCard = encrypt(testCard);
            String decryptedCard = decryptCardNumber(encryptedCard);

            log.info("Card Test:");
            log.info("  Original: {}", MaskingUtil.maskCardNumber(testCard));
            log.info("  Encrypted: {}...", encryptedCard.substring(0, Math.min(20, encryptedCard.length())));
            log.info("  Decrypted: {}", MaskingUtil.maskCardNumber(decryptedCard));
            log.info("  Match: {}", testCard.equals(decryptedCard) ? "✅" : "❌");

            // Test PIN
            String testPIN = "1234";
            String encryptedPIN = encrypt(testPIN);
            String decryptedPIN = decryptPIN(encryptedPIN);

            log.info("PIN Test:");
            log.info("  Original: ****");
            log.info("  Encrypted: {}...", encryptedPIN.substring(0, Math.min(20, encryptedPIN.length())));
            log.info("  Decrypted: ****");
            log.info("  Match: {}", testPIN.equals(decryptedPIN) ? "✅" : "❌");

            log.info("=== Encryption Test Complete ===");

        } catch (Exception e) {
            log.error("Encryption test failed", e);
        }
    }
}

