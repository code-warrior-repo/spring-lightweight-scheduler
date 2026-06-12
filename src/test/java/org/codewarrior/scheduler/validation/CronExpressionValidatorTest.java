package org.codewarrior.scheduler.validation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CronExpressionValidatorTest {
    CronExpressionValidator cronExpressionValidator = new CronExpressionValidator();

    @Test
    void testIs_Valid() {
        boolean result = cronExpressionValidator.isValid("0 */1 * * * *", null);
        Assertions.assertTrue(result);
    }

    @Test
    void testIs_InValid() {
        boolean result = cronExpressionValidator.isValid("cron", null);
        Assertions.assertFalse(result);
    }
}

