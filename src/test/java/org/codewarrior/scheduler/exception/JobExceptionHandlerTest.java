package org.codewarrior.scheduler.exception;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobExceptionHandlerTest {
    JobExceptionHandler jobExceptionHandler = new JobExceptionHandler();

    @Test
    void testHandleJobBeanValidation() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "field", "Field is invalid");

        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<?> result = jobExceptionHandler.handleJobBeanValidation(ex);
        Assertions.assertEquals(
                new ResponseEntity<>(Collections.emptyMap(), new HttpHeaders(), HttpStatus.BAD_REQUEST),
                result
        );
    }

    @Test
    void testHandleJobErrors() {

        JobValidationException ex =
                new JobValidationException("Invalid Job");

        ResponseEntity<?> response = jobExceptionHandler.handleJobErrors(ex);

        Assertions.assertNotNull(response);
    }
}

