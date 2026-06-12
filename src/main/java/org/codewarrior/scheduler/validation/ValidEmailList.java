package org.codewarrior.scheduler.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = EmailListValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmailList {

    String message() default "One or more email addresses are invalid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

