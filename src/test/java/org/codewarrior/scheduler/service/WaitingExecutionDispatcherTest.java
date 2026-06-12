package org.codewarrior.scheduler.service;

import org.codewarrior.scheduler.core.JobRunnerService;
import org.codewarrior.scheduler.repository.SchedulerJobExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WaitingExecutionDispatcherTest {

    private SchedulerJobExecutionRepository execRepo;
    private JobRunnerService jobRunnerService;
    private WaitingExecutionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        execRepo = mock(SchedulerJobExecutionRepository.class);
        jobRunnerService = mock(JobRunnerService.class);
        dispatcher = new WaitingExecutionDispatcher(execRepo, jobRunnerService);

        setField("enabled", true);
    }

    private void setField(String name, Object value) {
        try {
            Field field = WaitingExecutionDispatcher.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(dispatcher, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void dispatchDueWaitingExecutions_shouldReturnImmediately_whenDisabled() {
        setField("enabled", false);

        dispatcher.dispatchDueWaitingExecutions();

        verifyNoInteractions(execRepo);
        verifyNoInteractions(jobRunnerService);
    }

    @Test
    void dispatchDueWaitingExecutions_shouldReturn_whenNoDueWaitingJobsExist() {
        when(execRepo.findJobIdsWithDueWaitingExecutions(any()))
                .thenReturn(List.of());

        dispatcher.dispatchDueWaitingExecutions();

        verify(execRepo).findJobIdsWithDueWaitingExecutions(any());
        verifyNoInteractions(jobRunnerService);
    }

    @Test
    void dispatchDueWaitingExecutions_shouldProcessAllDueJobIds() {
        when(execRepo.findJobIdsWithDueWaitingExecutions(any()))
                .thenReturn(List.of(1L, 2L, 3L));

        dispatcher.dispatchDueWaitingExecutions();

        verify(jobRunnerService).processWaiting(1L);
        verify(jobRunnerService).processWaiting(2L);
        verify(jobRunnerService).processWaiting(3L);
    }

    @Test
    void dispatchDueWaitingExecutions_shouldContinue_whenOneJobFails() {
        when(execRepo.findJobIdsWithDueWaitingExecutions(any()))
                .thenReturn(List.of(1L, 2L, 3L));

        doThrow(new RuntimeException("boom"))
                .when(jobRunnerService)
                .processWaiting(2L);

        assertDoesNotThrow(() -> dispatcher.dispatchDueWaitingExecutions());

        verify(jobRunnerService).processWaiting(1L);
        verify(jobRunnerService).processWaiting(2L);
        verify(jobRunnerService).processWaiting(3L);
    }
}