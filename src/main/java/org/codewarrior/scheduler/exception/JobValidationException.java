package org.codewarrior.scheduler.exception;

public class JobValidationException extends RuntimeException {
    public JobValidationException(String message) {
        super(message);
    }
}

