package org.codewarrior.scheduler.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;

public class EmailListValidator implements ConstraintValidator<ValidEmailList, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {

        // Allow null or blank — optional field
        if (value == null || value.isBlank()) {
            return true;
        }

        // Remove trailing comma(s)
        String cleaned = value.replaceAll(",+$", "").trim();

        // After cleanup, still allow blank
        if (cleaned.isBlank()) {
            return true;
        }

        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .allMatch(this::isValidEmail);
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}
