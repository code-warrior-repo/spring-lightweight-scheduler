package org.codewarrior.scheduler.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.scheduling.support.CronTrigger;

public class CronExpressionValidator implements ConstraintValidator<ValidCron, String> {

    @Override
    public boolean isValid(String cron, ConstraintValidatorContext context) {
        if (cron == null || cron.isBlank()) return false;
        try {
            new CronTrigger(cron);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
