package org.codewarrior.scheduler.validation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EmailListValidatorTest {
    EmailListValidator emailListValidator = new EmailListValidator();

    @Test
    void testIsValid() {
        boolean result = emailListValidator.isValid("test@test.com", null);
        Assertions.assertTrue(result);
    }

    @Test
    void testIsValidList() {
        boolean result = emailListValidator.isValid("test@test.com, test1@test.com", null);
        Assertions.assertTrue(result);
    }

    @Test
    void testIs_InValid() {
        boolean result = emailListValidator.isValid("value", null);
        Assertions.assertFalse(result);
    }

    @Test
    void testIs_InValidList() {
        boolean result = emailListValidator.isValid("test@test.com, test1@test", null);
        Assertions.assertFalse(result);
    }

    @Test
    void testIs_Null() {
        boolean result = emailListValidator.isValid(null, null);
        Assertions.assertTrue(result);
    }

    @Test
    void testIs_Empty() {
        boolean result = emailListValidator.isValid(" ", null);
        Assertions.assertTrue(result);
    }

    @Test
    void testIs_JustComma() {
        boolean result = emailListValidator.isValid(",", null);
        Assertions.assertTrue(result);
    }
}

