package com.example.portfolio.validator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputValidatorTest {

    // ── IS_NOT_BLANK ──────────────────────────────────────────────────────────

    @Test
    void isNotBlank_null_returnsFalse() {
        assertFalse(InputValidator.IS_NOT_BLANK.test(null));
    }

    @Test
    void isNotBlank_empty_returnsFalse() {
        assertFalse(InputValidator.IS_NOT_BLANK.test(""));
    }

    @Test
    void isNotBlank_whitespaceOnly_returnsFalse() {
        assertFalse(InputValidator.IS_NOT_BLANK.test("   "));
    }

    @Test
    void isNotBlank_validString_returnsTrue() {
        assertTrue(InputValidator.IS_NOT_BLANK.test("hello"));
    }

    @Test
    void isNotBlank_stringWithSpaces_returnsTrue() {
        assertTrue(InputValidator.IS_NOT_BLANK.test("hello world"));
    }

    // ── IS_VALID_EMAIL ────────────────────────────────────────────────────────

    @Test
    void isValidEmail_null_returnsFalse() {
        assertFalse(InputValidator.IS_VALID_EMAIL.test(null));
    }

    @Test
    void isValidEmail_valid_returnsTrue() {
        assertTrue(InputValidator.IS_VALID_EMAIL.test("john@example.com"));
    }

    @Test
    void isValidEmail_withPlusAndDot_returnsTrue() {
        assertTrue(InputValidator.IS_VALID_EMAIL.test("john.doe+test@sub.example.org"));
    }

    @Test
    void isValidEmail_missingAt_returnsFalse() {
        assertFalse(InputValidator.IS_VALID_EMAIL.test("johnexample.com"));
    }

    @Test
    void isValidEmail_missingDomain_returnsFalse() {
        assertFalse(InputValidator.IS_VALID_EMAIL.test("john@"));
    }

    @Test
    void isValidEmail_singleCharTLD_returnsFalse() {
        assertFalse(InputValidator.IS_VALID_EMAIL.test("john@example.c"));
    }

    @Test
    void isValidEmail_blank_returnsFalse() {
        assertFalse(InputValidator.IS_VALID_EMAIL.test("  "));
    }

    // ── IS_VALID_PASSWORD ─────────────────────────────────────────────────────

    @Test
    void isValidPassword_null_returnsFalse() {
        assertFalse(InputValidator.IS_VALID_PASSWORD.test(null));
    }

    @Test
    void isValidPassword_tooShort_returnsFalse() {
        assertFalse(InputValidator.IS_VALID_PASSWORD.test("Ab1@"));
    }

    @Test
    void isValidPassword_missingUppercase_returnsFalse() {
        assertFalse(InputValidator.IS_VALID_PASSWORD.test("abcdef1@"));
    }

    @Test
    void isValidPassword_missingLowercase_returnsFalse() {
        assertFalse(InputValidator.IS_VALID_PASSWORD.test("ABCDEF1@"));
    }

    @Test
    void isValidPassword_missingDigit_returnsFalse() {
        assertFalse(InputValidator.IS_VALID_PASSWORD.test("Abcdefg@"));
    }

    @Test
    void isValidPassword_missingSpecialChar_returnsFalse() {
        assertFalse(InputValidator.IS_VALID_PASSWORD.test("Abcdef12"));
    }

    @Test
    void isValidPassword_valid_returnsTrue() {
        assertTrue(InputValidator.IS_VALID_PASSWORD.test("Secret@123"));
    }

    @Test
    void isValidPassword_eachAllowedSpecialChar() {
        // All chars in @#$%^*-_ must be accepted
        for (char c : "@#$%^*-_".toCharArray()) {
            assertTrue(InputValidator.IS_VALID_PASSWORD.test("Password1" + c),
                    "Special char '" + c + "' should be accepted");
        }
    }
}
