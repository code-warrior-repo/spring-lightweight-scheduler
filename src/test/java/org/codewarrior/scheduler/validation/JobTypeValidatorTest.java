package org.codewarrior.scheduler.validation;

import org.codewarrior.scheduler.core.JobRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

class JobTypeValidatorTest {
    @Mock
    JobRegistry registry;
    @InjectMocks
    JobTypeValidator jobTypeValidator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testIsValid() {
        when(registry.isValidJobType(anyString())).thenReturn(true);

        boolean result = jobTypeValidator.isValid("TestJob", null);
        Assertions.assertTrue(result);
    }

    @Test
    void testIs_InValid() {
        when(registry.isValidJobType(anyString())).thenReturn(false);

        boolean result = jobTypeValidator.isValid("FakeJob", null);
        Assertions.assertFalse(result);
    }
}

