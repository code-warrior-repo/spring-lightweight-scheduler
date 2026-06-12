package org.codewarrior.scheduler.validation;

import org.codewarrior.scheduler.core.JobRegistry;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class JobTypeValidator implements ConstraintValidator<ValidJobType, String> {

    private JobRegistry registry;

    @Autowired
    public JobTypeValidator(JobRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && registry.isValidJobType(value);
    }
}
