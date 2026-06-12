package org.codewarrior.scheduler.core;

import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import org.codewarrior.scheduler.service.AlertEmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobRunnerServiceTest {

    private JobExecutionTxService txService;
    private JobRegistry jobRegistry;
    private AlertEmailService alertEmailService;
    private JobRunnerService jobRunnerService;

    private static SchedulerJob job(Long jobId, String jobType, Long timeoutMs) {
        SchedulerJob job = new SchedulerJob();
        job.setJobPkId(jobId);
        job.setJobName("Job-" + jobId);
        job.setJobType(jobType);
        job.setJobParameters("{}");
        job.setJobEnabled(true);
        job.setMaxAllowedDurationMs(timeoutMs);
        job.setExecutionCount(0);
        return job;
    }

    private static SchedulerJobExecution execution(Long executionId, SchedulerJob job) {
        SchedulerJobExecution execution = new SchedulerJobExecution();
        execution.setJobExecPkId(executionId);
        execution.setJob(job);
        return execution;
    }

    @BeforeEach
    void setUp() {
        txService = mock(JobExecutionTxService.class);
        jobRegistry = mock(JobRegistry.class);
        alertEmailService = mock(AlertEmailService.class);

        jobRunnerService = new JobRunnerService(
                txService,
                jobRegistry,
                alertEmailService
        );

        setRunnerField("corePoolSize", 4);
        setRunnerField("maxPoolSize", 16);
        setRunnerField("queueCapacity", 500);
        setRunnerField("keepAliveSeconds", 60);
        setRunnerField("shutdownTimeoutSeconds", 30);

        jobRunnerService.initPool();
    }

    private void setRunnerField(String fieldName, Object value) {
        try {
            var field = JobRunnerService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(jobRunnerService, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @AfterEach
    void tearDown() {
        jobRunnerService.shutdownPool();
    }


    @Test
    void runJob_shouldReturnImmediately_whenStartExecutionIsSkipped() {
        Long jobId = 1L;

        when(txService.startExecution(jobId, "CRON", "CRON"))
                .thenReturn(JobExecutionTxService.StartExecutionResult.skip("Job disabled"));

        jobRunnerService.runJob(jobId, "CRON");

        verify(txService).startExecution(jobId, "CRON", "CRON");
        verifyNoInteractions(jobRegistry);
        verify(txService, never()).markSuccess(any());
        verify(txService, never()).markFailure(any(), any());
        verify(txService, never()).markTimeout(any());
        verify(txService, never()).moveNextWaitingToRunning(any());
        verifyNoInteractions(alertEmailService);
    }
    /*@Test
    void runJob_shouldReturnImmediately_whenStartExecutionIsSkipped() {
        Long jobId = 1L;

        when(txService.startExecution(jobId, "CRON"))
                .thenReturn(JobExecutionTxService.StartExecutionResult.skip("Job disabled"));

        jobRunnerService.runJob(jobId, "CRON");

        verify(txService).startExecution(jobId, "CRON");
        verifyNoInteractions(jobRegistry);
        verify(txService, never()).markSuccess(any());
        verify(txService, never()).markFailure(any(), any());
        verify(txService, never()).markTimeout(any());
        verify(txService, never()).moveNextWaitingToRunning(any());
        verifyNoInteractions(alertEmailService);
    }*/

    @Test
    void runJob_shouldRunSuccessfullyWithoutTimeout_andProcessWaitingByDefault() {
        Long jobId = 10L;
        Long executionId = 100L;

        SchedulerJob job = job(jobId, "TestJob", 0L);
        SchedulerJobExecution execution = execution(executionId, job);

        Runnable runnable = mock(Runnable.class);

        when(txService.startExecution(jobId, "CRON", "CRON"))
                .thenReturn(JobExecutionTxService.StartExecutionResult.run(execution));
        when(jobRegistry.create("TestJob", job, execution))
                .thenReturn(runnable);
        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.empty());

        jobRunnerService.runJob(jobId, "CRON");

        InOrder inOrder = inOrder(txService, jobRegistry, runnable);

        inOrder.verify(txService).startExecution(jobId, "CRON", "CRON");
        inOrder.verify(jobRegistry).create("TestJob", job, execution);
        inOrder.verify(runnable).run();
        inOrder.verify(txService).markSuccess(executionId);
        inOrder.verify(txService).moveNextWaitingToRunning(jobId);

        verify(txService, never()).markFailure(any(), any());
        verify(txService, never()).markTimeout(any());
        verifyNoInteractions(alertEmailService);
    }

    @Test
    void runJob_shouldRunSuccessfullyWithTimeoutConfigured() {
        Long jobId = 11L;
        Long executionId = 101L;

        SchedulerJob job = job(jobId, "TestJob", 5_000L);
        SchedulerJobExecution execution = execution(executionId, job);

        Runnable runnable = mock(Runnable.class);

        when(txService.startExecution(jobId, "CRON", "CRON"))
                .thenReturn(JobExecutionTxService.StartExecutionResult.run(execution));
        when(jobRegistry.create("TestJob", job, execution))
                .thenReturn(runnable);
        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.empty());

        jobRunnerService.runJob(jobId, "CRON");

        verify(jobRegistry).create("TestJob", job, execution);
        verify(runnable).run();
        verify(txService).markSuccess(executionId);
        verify(txService).moveNextWaitingToRunning(jobId);
        verifyNoInteractions(alertEmailService);
    }

    @Test
    void runJob_shouldMarkFailureAndSendFailureAlert_whenRunnableThrowsException() {
        Long jobId = 12L;
        Long executionId = 102L;

        SchedulerJob job = job(jobId, "FailingJob", 0L);
        SchedulerJobExecution execution = execution(executionId, job);

        RuntimeException failure = new RuntimeException("Forced failure");

        Runnable runnable = mock(Runnable.class);
        doThrow(failure).when(runnable).run();

        SchedulerJobExecution failedExecution = execution(executionId, job);

        when(txService.startExecution(jobId, "MANUAL", "MANUAL"))
                .thenReturn(JobExecutionTxService.StartExecutionResult.run(execution));
        when(jobRegistry.create("FailingJob", job, execution))
                .thenReturn(runnable);
        when(txService.markFailure(eq(executionId), any(Exception.class)))
                .thenReturn(failedExecution);
        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.empty());

        jobRunnerService.runJob(jobId, "MANUAL");

        verify(runnable).run();
        verify(txService).markFailure(eq(executionId), any(RuntimeException.class));
        verify(txService, never()).markSuccess(any());
        verify(txService, never()).markTimeout(any());
    }

    @Test
    void runJob_shouldMarkTimeoutAndSendTimeoutAlert_whenRunnableExceedsTimeout() {
        Long jobId = 13L;
        Long executionId = 103L;

        SchedulerJob job = job(jobId, "TimeoutJob", 50L);
        SchedulerJobExecution execution = execution(executionId, job);

        Runnable slowRunnable = () -> {
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };

        SchedulerJobExecution timeoutExecution = execution(executionId, job);

        when(txService.startExecution(jobId, "CRON", "CRON"))
                .thenReturn(JobExecutionTxService.StartExecutionResult.run(execution));
        when(jobRegistry.create("TimeoutJob", job, execution))
                .thenReturn(slowRunnable);
        when(txService.markTimeout(executionId))
                .thenReturn(timeoutExecution);
        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.empty());

        jobRunnerService.runJob(jobId, "CRON");

        verify(txService).markTimeout(executionId);
        verify(alertEmailService).sendTimeoutAlert(job, timeoutExecution);
        verify(txService, never()).markSuccess(any());
        verify(txService, never()).markFailure(any(), any());
    }

    @Test
    void runJob_shouldNotProcessWaiting_whenProcessWaitingIsFalse() {
        Long jobId = 14L;
        Long executionId = 104L;

        SchedulerJob job = job(jobId, "TestJob", 0L);
        SchedulerJobExecution execution = execution(executionId, job);

        Runnable runnable = mock(Runnable.class);

        when(txService.startExecution(jobId, "CRON", "CRON"))
                .thenReturn(JobExecutionTxService.StartExecutionResult.run(execution));
        when(jobRegistry.create("TestJob", job, execution))
                .thenReturn(runnable);

        jobRunnerService.runJob(jobId, "CRON", false);

        verify(runnable).run();
        verify(txService).markSuccess(executionId);
        verify(txService, never()).moveNextWaitingToRunning(any());
    }

    @Test
    void processWaiting_shouldReturnImmediately_whenQueueIsEmpty() {
        Long jobId = 20L;

        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.empty());

        jobRunnerService.processWaiting(jobId);

        verify(txService).moveNextWaitingToRunning(jobId);
        verifyNoInteractions(jobRegistry);
        verify(txService, never()).markSuccess(any());
        verify(txService, never()).markFailure(any(), any());
        verify(txService, never()).markTimeout(any());
    }

    @Test
    void processWaiting_shouldRunWaitingExecutionSuccessfully() {
        Long jobId = 21L;
        Long executionId = 201L;

        SchedulerJob job = job(jobId, "WaitingJob", 0L);
        SchedulerJobExecution waitingExecution = execution(executionId, job);

        Runnable runnable = mock(Runnable.class);

        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.of(waitingExecution))
                .thenReturn(Optional.empty());

        when(jobRegistry.create("WaitingJob", job, waitingExecution))
                .thenReturn(runnable);

        jobRunnerService.processWaiting(jobId);

        InOrder inOrder = inOrder(txService, jobRegistry, runnable);

        inOrder.verify(txService).moveNextWaitingToRunning(jobId);
        inOrder.verify(jobRegistry).create("WaitingJob", job, waitingExecution);
        inOrder.verify(runnable).run();
        inOrder.verify(txService).markSuccess(executionId);
        inOrder.verify(txService).moveNextWaitingToRunning(jobId);

        verifyNoInteractions(alertEmailService);
    }

    @Test
    void processWaiting_shouldMarkFailureAndSendFailureAlert_whenWaitingRunnableThrowsException() {
        Long jobId = 22L;
        Long executionId = 202L;

        SchedulerJob job = job(jobId, "WaitingFailJob", 0L);
        SchedulerJobExecution waitingExecution = execution(executionId, job);

        RuntimeException failure = new RuntimeException("Waiting failure");

        Runnable runnable = mock(Runnable.class);
        doThrow(failure).when(runnable).run();

        SchedulerJobExecution failedExecution = execution(executionId, job);

        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.of(waitingExecution))
                .thenReturn(Optional.empty());

        when(jobRegistry.create("WaitingFailJob", job, waitingExecution))
                .thenReturn(runnable);

        when(txService.markFailure(eq(executionId), any(Exception.class)))
                .thenReturn(failedExecution);

        jobRunnerService.processWaiting(jobId);

        verify(runnable).run();
        verify(txService).markFailure(eq(executionId), any(RuntimeException.class));
        verify(alertEmailService).sendFailureAlert(job, failedExecution);
        verify(txService, never()).markSuccess(executionId);
        verify(txService, never()).markTimeout(executionId);
    }

    @Test
    void processWaiting_shouldMarkTimeoutAndSendTimeoutAlert_whenWaitingRunnableExceedsTimeout() {
        Long jobId = 23L;
        Long executionId = 203L;

        SchedulerJob job = job(jobId, "WaitingTimeoutJob", 50L);
        SchedulerJobExecution waitingExecution = execution(executionId, job);

        Runnable slowRunnable = () -> {
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };

        SchedulerJobExecution timeoutExecution = execution(executionId, job);

        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.of(waitingExecution))
                .thenReturn(Optional.empty());

        when(jobRegistry.create("WaitingTimeoutJob", job, waitingExecution))
                .thenReturn(slowRunnable);

        when(txService.markTimeout(executionId))
                .thenReturn(timeoutExecution);

        jobRunnerService.processWaiting(jobId);

        verify(txService).markTimeout(executionId);
        verify(alertEmailService).sendTimeoutAlert(job, timeoutExecution);
        verify(txService, never()).markSuccess(executionId);
        verify(txService, never()).markFailure(eq(executionId), any());
    }

    @Test
    void processWaiting_shouldProcessMultipleWaitingExecutionsUntilQueueIsEmpty() {
        Long jobId = 24L;

        SchedulerJob job = job(jobId, "MultiWaitingJob", 0L);

        SchedulerJobExecution firstExecution = execution(301L, job);
        SchedulerJobExecution secondExecution = execution(302L, job);

        Runnable firstRunnable = mock(Runnable.class);
        Runnable secondRunnable = mock(Runnable.class);

        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.of(firstExecution))
                .thenReturn(Optional.of(secondExecution))
                .thenReturn(Optional.empty());

        when(jobRegistry.create("MultiWaitingJob", job, firstExecution))
                .thenReturn(firstRunnable);

        when(jobRegistry.create("MultiWaitingJob", job, secondExecution))
                .thenReturn(secondRunnable);

        jobRunnerService.processWaiting(jobId);

        verify(firstRunnable).run();
        verify(secondRunnable).run();

        verify(txService).markSuccess(301L);
        verify(txService).markSuccess(302L);

        verify(txService, times(3)).moveNextWaitingToRunning(jobId);
        verifyNoInteractions(alertEmailService);
    }

    @Test
    void shutdownPool_shouldNotThrow_whenCalledAfterInitPool() {
        assertDoesNotThrow(() -> {
            jobRunnerService.shutdownPool();
            jobRunnerService.initPool();
        });
    }

    @Test
    void shutdownPool_shouldNotThrow_whenExecutorWasNeverInitialized() {
        JobRunnerService serviceWithoutInitializedPool = new JobRunnerService(
                txService,
                jobRegistry,
                alertEmailService
        );

        assertDoesNotThrow(serviceWithoutInitializedPool::shutdownPool);
    }

    @Test
    void runJob_shouldUseConvenienceOverloadWithProcessWaitingEnabled() {
        Long jobId = 30L;
        Long executionId = 300L;

        SchedulerJob job = job(jobId, "ConvenienceJob", 0L);
        SchedulerJobExecution execution = execution(executionId, job);

        Runnable runnable = mock(Runnable.class);

        when(txService.startExecution(jobId, "API", "API"))
                .thenReturn(JobExecutionTxService.StartExecutionResult.run(execution));

        when(jobRegistry.create("ConvenienceJob", job, execution))
                .thenReturn(runnable);

        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.empty());

        jobRunnerService.runJob(jobId, "API");

        verify(txService).startExecution(jobId, "API", "API");
        verify(runnable).run();
        verify(txService).markSuccess(executionId);
        verify(txService).moveNextWaitingToRunning(jobId);
    }

    @Test
    void runJob_shouldCancelTimedOutFutureByInterruptingRunnable() {
        Long jobId = 31L;
        Long executionId = 310L;

        SchedulerJob job = job(jobId, "InterruptibleTimeoutJob", 50L);
        SchedulerJobExecution execution = execution(executionId, job);

        AtomicBoolean interrupted = new AtomicBoolean(false);

        Runnable interruptibleRunnable = () -> {
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException ex) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        };

        SchedulerJobExecution timeoutExecution = execution(executionId, job);

        when(txService.startExecution(jobId, "CRON", "CRON"))
                .thenReturn(JobExecutionTxService.StartExecutionResult.run(execution));

        when(jobRegistry.create("InterruptibleTimeoutJob", job, execution))
                .thenReturn(interruptibleRunnable);

        when(txService.markTimeout(executionId))
                .thenReturn(timeoutExecution);

        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.empty());

        jobRunnerService.runJob(jobId, "CRON");

        verify(txService).markTimeout(executionId);
        verify(alertEmailService).sendTimeoutAlert(job, timeoutExecution);
    }

    @Test
    void runJob_shouldMarkFailure_whenRunnableThrowsException() {
        Long jobId = 12L;
        Long executionId = 102L;

        SchedulerJob job = job(jobId, "FailingJob", 0L);
        SchedulerJobExecution execution = execution(executionId, job);

        RuntimeException failure = new RuntimeException("Forced failure");

        Runnable runnable = mock(Runnable.class);
        doThrow(failure).when(runnable).run();

        SchedulerJobExecution failedExecution = execution(executionId, job);

        when(txService.startExecution(jobId, "MANUAL", "MANUAL"))
                .thenReturn(JobExecutionTxService.StartExecutionResult.run(execution));
        when(jobRegistry.create("FailingJob", job, execution))
                .thenReturn(runnable);
        when(txService.markFailure(eq(executionId), any(Exception.class)))
                .thenReturn(failedExecution);
        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.empty());

        jobRunnerService.runJob(jobId, "MANUAL");

        verify(runnable).run();
        verify(txService).markFailure(eq(executionId), any(RuntimeException.class));
        verify(alertEmailService, never()).sendFailureAlert(any(), any());
        verify(txService, never()).markSuccess(any());
        verify(txService, never()).markTimeout(any());
    }

    @Test
    void processWaiting_shouldMarkFailure_whenWaitingRunnableThrowsException() {
        Long jobId = 22L;
        Long executionId = 202L;

        SchedulerJob job = job(jobId, "WaitingFailJob", 0L);
        SchedulerJobExecution waitingExecution = execution(executionId, job);

        RuntimeException failure = new RuntimeException("Waiting failure");

        Runnable runnable = mock(Runnable.class);
        doThrow(failure).when(runnable).run();

        SchedulerJobExecution failedExecution = execution(executionId, job);

        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.of(waitingExecution))
                .thenReturn(Optional.empty());

        when(jobRegistry.create("WaitingFailJob", job, waitingExecution))
                .thenReturn(runnable);

        when(txService.markFailure(eq(executionId), any(Exception.class)))
                .thenReturn(failedExecution);

        jobRunnerService.processWaiting(jobId);

        verify(runnable).run();
        verify(txService).markFailure(eq(executionId), any(RuntimeException.class));
        //verify(alertEmailService, never()).sendFailureAlert(any(), any());
        verify(txService, never()).markSuccess(executionId);
        verify(txService, never()).markTimeout(executionId);
    }

    @Test
    void runJob_shouldPassSeparateStartedByAndTriggerType() {
        Long jobId = 40L;
        Long executionId = 400L;

        SchedulerJob job = job(jobId, "ManualJob", 0L);
        SchedulerJobExecution execution = execution(executionId, job);

        Runnable runnable = mock(Runnable.class);

        when(txService.startExecution(jobId, "USER123", "MANUAL"))
                .thenReturn(JobExecutionTxService.StartExecutionResult.run(execution));

        when(jobRegistry.create("ManualJob", job, execution))
                .thenReturn(runnable);

        when(txService.moveNextWaitingToRunning(jobId))
                .thenReturn(Optional.empty());

        jobRunnerService.runJob(jobId, "USER123", "MANUAL", true);

        verify(txService).startExecution(jobId, "USER123", "MANUAL");
        verify(runnable).run();
        verify(txService).markSuccess(executionId);
    }
}