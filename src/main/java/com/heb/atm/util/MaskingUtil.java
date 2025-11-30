package com.heb.atm.util;

import lombok.experimental.UtilityClass;

/**
 * Utility class for masking sensitive data
 * Used to mask card numbers, account numbers, and other sensitive information in logs and responses
 */
@UtilityClass
public class MaskingUtil {

    /**
     * Mask card number showing only last 4 digits
     * Example: "4532015112830366" -> "************0366"
     *
     * @param cardNumber 16-digit card number
     * @return Masked card number
     */
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "************" + cardNumber.substring(cardNumber.length() - 4);
    }

    /**
     * Mask card number from char array showing only last 4 digits
     * Example: ['4','5','3','2',...,'0','3','6','6'] -> "************0366"
     *
     * @param cardNumber Card number as char array
     * @return Masked card number
     */
    public static String maskCardNumber(char[] cardNumber) {
        if (cardNumber == null || cardNumber.length < 4) {
            return "****";
        }
        return "************" + new String(cardNumber, cardNumber.length - 4, 4);
    }

    /**
     * Mask account number showing only last 4 digits
     * Example: "1234567890" -> "******7890"
     *
     * @param accountNumber Account number
     * @return Masked account number
     */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        int visibleDigits = 4;
        int maskLength = accountNumber.length() - visibleDigits;
        return "*".repeat(maskLength) + accountNumber.substring(accountNumber.length() - visibleDigits);
    }

    /**
     * Mask PIN completely
     * Example: "1234" -> "****"
     *
     * @param pin PIN code
     * @return Completely masked PIN
     */
    public static String maskPin(String pin) {
        if (pin == null) {
            return "****";
        }
        return "*".repeat(pin.length());
    }
}

