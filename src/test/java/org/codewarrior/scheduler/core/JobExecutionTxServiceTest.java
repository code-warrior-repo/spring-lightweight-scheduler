package org.codewarrior.scheduler.core;

import org.codewarrior.scheduler.constants.JobConstants;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import org.codewarrior.scheduler.dto.JobExecutionStatus;
import org.codewarrior.scheduler.repository.SchedulerJobExecutionRepository;
import org.codewarrior.scheduler.repository.SchedulerJobRepository;
import org.codewarrior.scheduler.service.AlertEmailService;
import org.codewarrior.scheduler.service.CronService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobExecutionTxServiceTest {

    private SchedulerJobExecutionRepository execRepo;
    private SchedulerJobRepository jobRepo;
    private CronService cronService;
    private JobExecutionTxService service;
    private AlertEmailService alertService;

    private static SchedulerJob job(Long jobId) {
        SchedulerJob job = new SchedulerJob();
        job.setJobPkId(jobId);
        job.setJobName("Job-" + jobId);
        job.setJobType("TestJob");
        job.setJobParameters("{}");
        job.setCronExpression(null);
        job.setJobEnabled(true);
        job.setExecutionCount(0);
        job.setRetryCount(0);
        job.setRetryDelaySeconds(0);
        job.setMaxWaitingQueueSize(10);
        job.setAlertFailureCount(0);
        return job;
    }

    private static SchedulerJobExecution execution(
            Long executionId,
            SchedulerJob job,
            JobExecutionStatus status
    ) {
        SchedulerJobExecution execution = new SchedulerJobExecution();
        execution.setJobExecPkId(executionId);
        execution.setJob(job);
        execution.setStatus(status);
        execution.setStartedAt(LocalDateTime.now().minusSeconds(1));
        execution.setRetryNumber(0);
        execution.setTimeoutOccurred(false);
        execution.setLongRunningAlertSent(false);
        return execution;
    }

    @BeforeEach
    void setUp() {
        execRepo = mock(SchedulerJobExecutionRepository.class);
        jobRepo = mock(SchedulerJobRepository.class);
        cronService = mock(CronService.class);
        alertService = mock(AlertEmailService.class);
        service = new JobExecutionTxService(
                execRepo,
                jobRepo,
                cronService,
                alertService
        );
    }

    @Test
    void startExecution_shouldThrowException_whenJobNotFound() {
        Long jobId = 1L;

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.startExecution(jobId, "CRON")
        );

        assertEquals("Job not found", exception.getMessage());
        verify(jobRepo).findByIdForUpdate(jobId);
        verifyNoInteractions(execRepo);
    }

    @Test
    void startExecution_shouldSkip_whenJobIsDisabled() {
        Long jobId = 2L;
        SchedulerJob job = job(jobId);
        job.setJobEnabled(false);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));

        JobExecutionTxService.StartExecutionResult result =
                service.startExecution(jobId, "CRON");

        assertFalse(result.shouldRun());
        assertNull(result.execution());
        assertEquals("Job disabled", result.reason());

        verify(jobRepo).findByIdForUpdate(jobId);
        verify(execRepo, never()).existsRunningExecutionWithLock(anyLong());
        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void startExecution_shouldSkip_whenCircuitIsOpen() {
        Long jobId = 3L;
        SchedulerJob job = job(jobId);
        job.setCircuitOpenUntil(LocalDateTime.now().plusMinutes(10));

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));

        JobExecutionTxService.StartExecutionResult result =
                service.startExecution(jobId, "CRON");

        assertFalse(result.shouldRun());
        assertNull(result.execution());
        assertEquals("Circuit open", result.reason());

        verify(execRepo, never()).existsRunningExecutionWithLock(anyLong());
        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void startExecution_shouldCreateRunningExecution_whenNoExecutionIsRunning() {
        Long jobId = 4L;
        LocalDateTime nextRun = LocalDateTime.now().plusMinutes(5);

        SchedulerJob job = job(jobId);
        job.setCronExpression("0 */5 * * * *");

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(false);
        when(cronService.computeNextRun(eq("0 */5 * * * *"), any(LocalDateTime.class)))
                .thenReturn(nextRun);
        when(execRepo.save(any(SchedulerJobExecution.class)))
                .thenAnswer(invocation -> {
                    SchedulerJobExecution exec = invocation.getArgument(0);
                    exec.setJobExecPkId(100L);
                    return exec;
                });

        JobExecutionTxService.StartExecutionResult result =
                service.startExecution(jobId, "CRON");

        assertTrue(result.shouldRun());
        assertNotNull(result.execution());
        assertEquals("RUNNING", result.reason());

        SchedulerJobExecution execution = result.execution();

        assertEquals(100L, execution.getJobExecPkId());
        assertEquals(job, execution.getJob());
        assertEquals(JobExecutionStatus.RUNNING, execution.getStatus());
        assertEquals("CRON", execution.getStartedBy());
        assertEquals("CRON", execution.getTriggerType());
        assertEquals(0, execution.getRetryNumber());
        assertNotNull(execution.getStartedAt());
        assertNotNull(execution.getRunningOnHost());
        assertFalse(execution.getTimeoutOccurred());
        assertFalse(execution.getLongRunningAlertSent());

        assertNotNull(job.getLastRunStartedAt());
        assertEquals(nextRun, job.getNextRun());

        verify(execRepo).save(any(SchedulerJobExecution.class));
        verify(jobRepo).save(job);
    }

    @Test
    void startExecution_shouldCreateRunningExecutionWithoutNextRun_whenCronExpressionIsNull() {
        Long jobId = 5L;

        SchedulerJob job = job(jobId);
        job.setCronExpression(null);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(false);
        when(execRepo.save(any(SchedulerJobExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JobExecutionTxService.StartExecutionResult result =
                service.startExecution(jobId, "MANUAL");

        assertTrue(result.shouldRun());
        assertNotNull(result.execution());
        assertNull(job.getNextRun());

        verifyNoInteractions(cronService);
        verify(jobRepo).save(job);
    }

    @Test
    void startExecution_shouldSkip_whenAlreadyRunningAndPolicyIsNull() {
        Long jobId = 6L;
        SchedulerJob job = job(jobId);
        job.setConcurrencyPolicy(null);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(true);

        JobExecutionTxService.StartExecutionResult result =
                service.startExecution(jobId, "CRON");

        assertFalse(result.shouldRun());
        assertNull(result.execution());
        assertEquals("Already running", result.reason());

        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void startExecution_shouldSkip_whenAlreadyRunningAndPolicyIsSkip() {
        Long jobId = 7L;
        SchedulerJob job = job(jobId);
        job.setConcurrencyPolicy(JobConstants.JOB_EXECUTION_SKIP);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(true);

        JobExecutionTxService.StartExecutionResult result =
                service.startExecution(jobId, "CRON");

        assertFalse(result.shouldRun());
        assertEquals("Already running", result.reason());

        verify(execRepo, never()).save(any());
    }

    @Test
    void startExecution_shouldQueue_whenAlreadyRunningAndPolicyIsQueue() {
        Long jobId = 8L;
        SchedulerJob job = job(jobId);
        job.setConcurrencyPolicy(JobConstants.JOB_EXECUTION_QUEUE);
        job.setMaxWaitingQueueSize(5);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(true);
        when(execRepo.countByJob_JobPkIdAndStatus(jobId, JobExecutionStatus.WAITING))
                .thenReturn(2L);
        when(execRepo.save(any(SchedulerJobExecution.class)))
                .thenAnswer(invocation -> {
                    SchedulerJobExecution exec = invocation.getArgument(0);
                    exec.setJobExecPkId(200L);
                    return exec;
                });

        JobExecutionTxService.StartExecutionResult result =
                service.startExecution(jobId, "CRON");

        assertFalse(result.shouldRun());
        assertNull(result.execution());
        assertEquals("Queued", result.reason());

        ArgumentCaptor<SchedulerJobExecution> captor =
                ArgumentCaptor.forClass(SchedulerJobExecution.class);

        verify(execRepo).save(captor.capture());

        SchedulerJobExecution queued = captor.getValue();

        assertEquals(job, queued.getJob());
        assertEquals(JobExecutionStatus.WAITING, queued.getStatus());
        assertEquals(JobConstants.JOB_EXECUTION_RETRY, queued.getStartedBy());
        assertEquals(JobConstants.JOB_EXECUTION_RETRY, queued.getTriggerType());
        assertEquals("Already running", queued.getWaitReason());
        assertEquals(0, queued.getRetryNumber());
        assertNull(queued.getRunningOnHost());
        assertNotNull(queued.getQueuedAt());
        assertFalse(queued.getTimeoutOccurred());
        assertFalse(queued.getLongRunningAlertSent());
    }

    @Test
    void startExecution_shouldCreateSkippedExecution_whenQueueIsFull() {
        Long jobId = 9L;
        SchedulerJob job = job(jobId);
        job.setConcurrencyPolicy(JobConstants.JOB_EXECUTION_QUEUE);
        job.setMaxWaitingQueueSize(2);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(true);
        when(execRepo.countByJob_JobPkIdAndStatus(jobId, JobExecutionStatus.WAITING))
                .thenReturn(2L);
        when(execRepo.save(any(SchedulerJobExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JobExecutionTxService.StartExecutionResult result =
                service.startExecution(jobId, "CRON");

        assertFalse(result.shouldRun());
        assertEquals("Queued", result.reason());

        ArgumentCaptor<SchedulerJobExecution> captor =
                ArgumentCaptor.forClass(SchedulerJobExecution.class);

        verify(execRepo).save(captor.capture());

        SchedulerJobExecution skipped = captor.getValue();

        assertEquals(JobExecutionStatus.SKIPPED, skipped.getStatus());
        assertEquals("SYSTEM", skipped.getStartedBy());
        assertEquals("SYSTEM", skipped.getTriggerType());
        assertEquals("Queue full", skipped.getWaitReason());
        assertEquals(0, skipped.getRetryNumber());
        assertNotNull(skipped.getQueuedAt());
        assertFalse(skipped.getTimeoutOccurred());
        assertFalse(skipped.getLongRunningAlertSent());
    }

    @Test
    void startExecution_shouldTreatNullMaxWaitingQueueSizeAsZeroAndSkipQueue() {
        Long jobId = 10L;
        SchedulerJob job = job(jobId);
        job.setConcurrencyPolicy(JobConstants.JOB_EXECUTION_QUEUE);
        job.setMaxWaitingQueueSize(null);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(true);
        when(execRepo.countByJob_JobPkIdAndStatus(jobId, JobExecutionStatus.WAITING))
                .thenReturn(0L);
        when(execRepo.save(any(SchedulerJobExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.startExecution(jobId, "CRON");

        ArgumentCaptor<SchedulerJobExecution> captor =
                ArgumentCaptor.forClass(SchedulerJobExecution.class);

        verify(execRepo).save(captor.capture());

        assertEquals(JobExecutionStatus.SKIPPED, captor.getValue().getStatus());
        assertEquals("Queue full", captor.getValue().getWaitReason());
    }

    @Test
    void startExecution_shouldAllowParallel_whenAlreadyRunningAndPolicyIsParallel() {
        Long jobId = 11L;
        SchedulerJob job = job(jobId);
        job.setConcurrencyPolicy(JobConstants.JOB_EXECUTION_PARALLEL);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(true);
        when(execRepo.save(any(SchedulerJobExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JobExecutionTxService.StartExecutionResult result =
                service.startExecution(jobId, "CRON");

        assertTrue(result.shouldRun());
        assertNotNull(result.execution());
        assertEquals(JobExecutionStatus.RUNNING, result.execution().getStatus());

        verify(execRepo).save(any(SchedulerJobExecution.class));
        verify(jobRepo).save(job);
    }

    @Test
    void startExecution_shouldSkip_whenConcurrencyPolicyIsUnknown() {
        Long jobId = 12L;
        SchedulerJob job = job(jobId);
        job.setConcurrencyPolicy("UNKNOWN_POLICY");

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(true);

        JobExecutionTxService.StartExecutionResult result =
                service.startExecution(jobId, "CRON");

        assertFalse(result.shouldRun());
        assertNull(result.execution());
        assertEquals("Unknown concurrency policy", result.reason());

        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void save_shouldDelegateToRepository() {
        SchedulerJobExecution execution = new SchedulerJobExecution();
        execution.setJobExecPkId(50L);
        execution.setStatus(JobExecutionStatus.RUNNING);

        when(execRepo.save(execution)).thenReturn(execution);

        SchedulerJobExecution result = service.save(execution);

        assertSame(execution, result);
        verify(execRepo).save(execution);
    }

    @Test
    void findJobForExecution_shouldReturnJob_whenFound() {
        Long jobId = 13L;
        SchedulerJob job = job(jobId);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));

        SchedulerJob result = service.findJobForExecution(jobId);

        assertSame(job, result);
    }

    @Test
    void findJobForExecution_shouldThrowException_whenJobNotFound() {
        Long jobId = 14L;

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.findJobForExecution(jobId)
        );

        assertEquals("Job not found", exception.getMessage());
    }

    @Test
    void markSuccess_shouldThrowException_whenExecutionNotFound() {
        Long executionId = 100L;

        when(execRepo.findById(executionId)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.markSuccess(executionId)
        );

        assertEquals("Execution not found", exception.getMessage());
    }

    @Test
    void markSuccess_shouldReturnExecutionUnchanged_whenStatusIsNotRunning() {
        Long executionId = 101L;
        SchedulerJob job = job(1L);
        SchedulerJobExecution execution = execution(executionId, job, JobExecutionStatus.FAILED);

        when(execRepo.findById(executionId)).thenReturn(Optional.of(execution));

        SchedulerJobExecution result = service.markSuccess(executionId);

        assertSame(execution, result);
        assertEquals(JobExecutionStatus.FAILED, result.getStatus());

        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void markSuccess_shouldUpdateExecutionAndJob_whenStatusIsRunning() {
        Long executionId = 102L;
        LocalDateTime nextRun = LocalDateTime.now().plusMinutes(5);

        SchedulerJob job = job(2L);
        job.setExecutionCount(null);
        job.setCronExpression("0 */5 * * * *");

        SchedulerJobExecution execution = execution(executionId, job, JobExecutionStatus.RUNNING);
        execution.setStartedAt(LocalDateTime.now().minusSeconds(5));
        execution.setFailureType("OLD_FAILURE");
        execution.setErrorMessage("OLD_ERROR");
        execution.setTimeoutOccurred(true);

        when(execRepo.findById(executionId)).thenReturn(Optional.of(execution));
        when(cronService.computeNextRun(eq("0 */5 * * * *"), any(LocalDateTime.class)))
                .thenReturn(nextRun);
        when(execRepo.save(execution)).thenReturn(execution);

        SchedulerJobExecution result = service.markSuccess(executionId);

        assertSame(execution, result);
        assertEquals(JobExecutionStatus.SUCCESS, result.getStatus());
        assertNotNull(result.getCompletedAt());
        assertNotNull(result.getDurationMs());
        assertNull(result.getFailureType());
        assertNull(result.getErrorMessage());
        assertFalse(result.getTimeoutOccurred());

        assertEquals(JobConstants.JOB_EXECUTION_STATUS_SUCCESS, job.getLastRunStatus());
        assertEquals(result.getCompletedAt(), job.getLastRunCompletedAt());
        assertNull(job.getLastRunError());
        assertEquals(1, job.getExecutionCount());
        assertEquals(nextRun, job.getNextRun());

        verify(jobRepo).save(job);
        verify(execRepo).save(execution);
    }

    @Test
    void markFailure_shouldThrowException_whenExecutionNotFound() {
        Long executionId = 103L;

        when(execRepo.findById(executionId)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.markFailure(executionId, new RuntimeException("boom"))
        );

        assertEquals("Execution not found", exception.getMessage());
    }

    @Test
    void markFailure_shouldReturnExecutionUnchanged_whenStatusIsNotRunning() {
        Long executionId = 104L;
        SchedulerJob job = job(3L);
        SchedulerJobExecution execution = execution(executionId, job, JobExecutionStatus.SUCCESS);

        when(execRepo.findById(executionId)).thenReturn(Optional.of(execution));

        SchedulerJobExecution result = service.markFailure(executionId, new RuntimeException("boom"));

        assertSame(execution, result);
        assertEquals(JobExecutionStatus.SUCCESS, result.getStatus());

        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void markFailure_shouldUpdateExecutionAndJobWithoutRetry_whenRetryLimitReached() {
        Long executionId = 105L;

        SchedulerJob job = job(4L);
        job.setExecutionCount(null);
        job.setAlertFailureCount(null);
        job.setRetryCount(1);
        job.setRetryDelaySeconds(30);

        SchedulerJobExecution execution = execution(executionId, job, JobExecutionStatus.RUNNING);
        execution.setStartedAt(LocalDateTime.now().minusSeconds(3));
        execution.setRetryNumber(1);

        RuntimeException failure = new RuntimeException("forced failure");

        when(execRepo.findById(executionId)).thenReturn(Optional.of(execution));
        when(execRepo.save(execution)).thenReturn(execution);

        SchedulerJobExecution result = service.markFailure(executionId, failure);

        assertSame(execution, result);
        assertEquals(JobExecutionStatus.FAILED, result.getStatus());
        assertNotNull(result.getCompletedAt());
        assertNotNull(result.getDurationMs());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("forced failure"));
        assertEquals(JobConstants.JOB_EXECUTION_STATUS_SYSTEM_ERROR, result.getFailureType());
        assertFalse(result.getTimeoutOccurred());

        assertEquals(JobConstants.JOB_EXECUTION_STATUS_FAILED, job.getLastRunStatus());
        assertEquals(result.getCompletedAt(), job.getLastRunCompletedAt());
        assertEquals(result.getCompletedAt(), job.getLastFailureAt());
        assertEquals(result.getErrorMessage(), job.getLastRunError());
        assertEquals(1, job.getExecutionCount());
        assertEquals(1, job.getAlertFailureCount());

        verify(jobRepo).save(job);
        verify(execRepo, times(1)).save(any(SchedulerJobExecution.class));
    }

    @Test
    void markFailure_shouldCreateRetry_whenRetryIsAllowed() {
        Long executionId = 106L;

        SchedulerJob job = job(5L);
        job.setRetryCount(3);
        job.setRetryDelaySeconds(10);

        SchedulerJobExecution execution = execution(executionId, job, JobExecutionStatus.RUNNING);
        execution.setStartedAt(LocalDateTime.now().minusSeconds(3));
        execution.setRetryNumber(1);

        when(execRepo.findById(executionId)).thenReturn(Optional.of(execution));
        when(execRepo.save(any(SchedulerJobExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SchedulerJobExecution result =
                service.markFailure(executionId, new RuntimeException("failed"));

        assertEquals(JobExecutionStatus.FAILED, result.getStatus());

        ArgumentCaptor<SchedulerJobExecution> captor =
                ArgumentCaptor.forClass(SchedulerJobExecution.class);

        verify(execRepo, times(2)).save(captor.capture());

        SchedulerJobExecution retryExecution = captor.getAllValues().get(1);

        assertEquals(job, retryExecution.getJob());
        assertEquals(JobExecutionStatus.WAITING, retryExecution.getStatus());
        assertEquals(2, retryExecution.getRetryNumber());
        assertEquals("Retry #2", retryExecution.getWaitReason());
        assertEquals(JobConstants.JOB_EXECUTION_RETRY, retryExecution.getStartedBy());
        assertEquals(JobConstants.JOB_EXECUTION_RETRY, retryExecution.getTriggerType());
        assertNull(retryExecution.getRunningOnHost());
        assertNotNull(retryExecution.getQueuedAt());
        assertFalse(retryExecution.getTimeoutOccurred());
        assertFalse(retryExecution.getLongRunningAlertSent());
    }

    @Test
    void markFailure_shouldTreatNullRetryNumberAndRetryCountAsZero() {
        Long executionId = 107L;

        SchedulerJob job = job(6L);
        job.setRetryCount(null);

        SchedulerJobExecution execution = execution(executionId, job, JobExecutionStatus.RUNNING);
        execution.setRetryNumber(null);

        when(execRepo.findById(executionId)).thenReturn(Optional.of(execution));
        when(execRepo.save(execution)).thenReturn(execution);

        service.markFailure(executionId, new RuntimeException("failed"));

        verify(execRepo, times(1)).save(any(SchedulerJobExecution.class));
    }

    @Test
    void markFailure_shouldTreatNullRetryDelaySecondsAsZero_whenCreatingRetry() {
        Long executionId = 108L;

        SchedulerJob job = job(7L);
        job.setRetryCount(1);
        job.setRetryDelaySeconds(null);

        SchedulerJobExecution execution = execution(executionId, job, JobExecutionStatus.RUNNING);
        execution.setRetryNumber(0);

        when(execRepo.findById(executionId)).thenReturn(Optional.of(execution));
        when(execRepo.save(any(SchedulerJobExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.markFailure(executionId, new RuntimeException("failed"));

        verify(execRepo, times(2)).save(any(SchedulerJobExecution.class));
    }

    @Test
    void markTimeout_shouldThrowException_whenExecutionNotFound() {
        Long executionId = 109L;

        when(execRepo.findById(executionId)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.markTimeout(executionId)
        );

        assertEquals("Execution not found", exception.getMessage());
    }

    @Test
    void markTimeout_shouldReturnExecutionUnchanged_whenStatusIsNotRunning() {
        Long executionId = 110L;
        SchedulerJob job = job(8L);
        SchedulerJobExecution execution = execution(executionId, job, JobExecutionStatus.SUCCESS);

        when(execRepo.findById(executionId)).thenReturn(Optional.of(execution));

        SchedulerJobExecution result = service.markTimeout(executionId);

        assertSame(execution, result);
        assertEquals(JobExecutionStatus.SUCCESS, result.getStatus());

        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void markTimeout_shouldUpdateExecutionAndJob_whenStatusIsRunning() {
        Long executionId = 111L;

        SchedulerJob job = job(9L);
        job.setExecutionCount(null);

        SchedulerJobExecution execution = execution(executionId, job, JobExecutionStatus.RUNNING);
        execution.setStartedAt(LocalDateTime.now().minusSeconds(4));

        when(execRepo.findById(executionId)).thenReturn(Optional.of(execution));
        when(execRepo.save(execution)).thenReturn(execution);

        SchedulerJobExecution result = service.markTimeout(executionId);

        assertSame(execution, result);
        assertEquals(JobExecutionStatus.TIMEOUT, result.getStatus());
        assertTrue(result.getTimeoutOccurred());
        assertNotNull(result.getCompletedAt());
        assertNotNull(result.getDurationMs());
        assertEquals(JobConstants.JOB_EXECUTION_STATUS_TIMEOUT, result.getFailureType());

        assertEquals(JobConstants.JOB_EXECUTION_STATUS_TIMEOUT, job.getLastRunStatus());
        assertEquals(result.getCompletedAt(), job.getLastRunCompletedAt());
        assertEquals(1, job.getExecutionCount());

        verify(jobRepo).save(job);
        verify(execRepo).save(execution);
    }

    @Test
    void markTimeout_shouldSetNullDuration_whenStartedAtIsNull() {
        Long executionId = 112L;

        SchedulerJob job = job(10L);
        SchedulerJobExecution execution = execution(executionId, job, JobExecutionStatus.RUNNING);
        execution.setStartedAt(null);

        when(execRepo.findById(executionId)).thenReturn(Optional.of(execution));
        when(execRepo.save(execution)).thenReturn(execution);

        SchedulerJobExecution result = service.markTimeout(executionId);

        assertEquals(JobExecutionStatus.TIMEOUT, result.getStatus());
        assertNull(result.getDurationMs());
    }

    @Test
    void fetchNextWaiting_shouldReturnEmpty_whenNoWaitingExecutionExists() {
        Long jobId = 15L;

        when(execRepo.findTopByJob_JobPkIdAndStatusOrderByQueuedAtAsc(
                jobId,
                JobExecutionStatus.WAITING
        )).thenReturn(Optional.empty());

        Optional<SchedulerJobExecution> result = service.fetchNextWaiting(jobId);

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchNextWaiting_shouldReturnWaitingExecution_whenExists() {
        Long jobId = 16L;
        SchedulerJob job = job(jobId);
        SchedulerJobExecution waitingExecution =
                execution(200L, job, JobExecutionStatus.WAITING);
        waitingExecution.setQueuedAt(LocalDateTime.now());

        when(execRepo.findTopByJob_JobPkIdAndStatusOrderByQueuedAtAsc(
                jobId,
                JobExecutionStatus.WAITING
        )).thenReturn(Optional.of(waitingExecution));

        Optional<SchedulerJobExecution> result = service.fetchNextWaiting(jobId);

        assertTrue(result.isPresent());
        assertSame(waitingExecution, result.get());
    }

    @Test
    void moveNextWaitingToRunning_shouldThrowException_whenJobNotFound() {
        Long jobId = 17L;

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.moveNextWaitingToRunning(jobId)
        );

        assertEquals("Job not found", exception.getMessage());
        verify(execRepo, never()).findNextWaitingForUpdate(anyLong());
    }

    @Test
    void moveNextWaitingToRunning_shouldReturnEmpty_whenNoWaitingExecutionExists() {
        Long jobId = 18L;
        SchedulerJob job = job(jobId);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(false);
        when(execRepo.findNextWaitingForUpdate(jobId)).thenReturn(Optional.empty());

        Optional<SchedulerJobExecution> result =
                service.moveNextWaitingToRunning(jobId);

        assertTrue(result.isEmpty());

        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void moveNextWaitingToRunning_shouldReturnEmpty_whenQueuedAtIsInFuture() {
        Long jobId = 19L;
        SchedulerJob job = job(jobId);

        SchedulerJobExecution waitingExecution =
                execution(300L, job, JobExecutionStatus.WAITING);
        waitingExecution.setQueuedAt(LocalDateTime.now().plusMinutes(10));

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(false);
        when(execRepo.findNextWaitingForUpdate(jobId)).thenReturn(Optional.of(waitingExecution));

        Optional<SchedulerJobExecution> result =
                service.moveNextWaitingToRunning(jobId);

        assertTrue(result.isEmpty());
        assertEquals(JobExecutionStatus.WAITING, waitingExecution.getStatus());

        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }
    /*
    @Test
    void moveNextWaitingToRunning_shouldThrowException_whenJobNotFound() {
        Long jobId = 17L;

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.moveNextWaitingToRunning(jobId)
        );

        assertEquals("Job not found", exception.getMessage());
        verify(execRepo, never()).findTopByJob_JobPkIdAndStatusOrderByQueuedAtAsc(anyLong(), any());
    }

    @Test
    void moveNextWaitingToRunning_shouldReturnEmpty_whenNoWaitingExecutionExists() {
        Long jobId = 18L;
        SchedulerJob job = job(jobId);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.findTopByJob_JobPkIdAndStatusOrderByQueuedAtAsc(
                jobId,
                JobExecutionStatus.WAITING
        )).thenReturn(Optional.empty());

        Optional<SchedulerJobExecution> result =
                service.moveNextWaitingToRunning(jobId);

        assertTrue(result.isEmpty());

        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void moveNextWaitingToRunning_shouldReturnEmpty_whenQueuedAtIsInFuture() {
        Long jobId = 19L;
        SchedulerJob job = job(jobId);

        SchedulerJobExecution waitingExecution =
                execution(300L, job, JobExecutionStatus.WAITING);
        waitingExecution.setQueuedAt(LocalDateTime.now().plusMinutes(10));

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.findTopByJob_JobPkIdAndStatusOrderByQueuedAtAsc(
                jobId,
                JobExecutionStatus.WAITING
        )).thenReturn(Optional.of(waitingExecution));

        Optional<SchedulerJobExecution> result =
                service.moveNextWaitingToRunning(jobId);

        assertTrue(result.isEmpty());
        assertEquals(JobExecutionStatus.WAITING, waitingExecution.getStatus());

        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }*/

    /*@Test
    void existsRunningExecutionWithLock_shouldDelegateToRepository() {
        Long jobId = 22L;

        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(true);

        boolean result = service.existsRunningExecutionWithLock(jobId);

        assertTrue(result);
        verify(execRepo).existsRunningExecutionWithLock(jobId);
    }*/
    @Test
    void moveNextWaitingToRunning_shouldMoveWaitingExecutionToRunning() {
        Long jobId = 20L;
        LocalDateTime nextRun = LocalDateTime.now().plusMinutes(5);

        SchedulerJob job = job(jobId);
        job.setCronExpression("0 */5 * * * *");

        SchedulerJobExecution waitingExecution =
                execution(301L, job, JobExecutionStatus.WAITING);
        waitingExecution.setQueuedAt(LocalDateTime.now().minusSeconds(5));
        waitingExecution.setStartedBy(null);
        waitingExecution.setWaitReason("Retry #1");

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(false);
        when(execRepo.findNextWaitingForUpdate(jobId)).thenReturn(Optional.of(waitingExecution));
        when(cronService.computeNextRun(eq("0 */5 * * * *"), any(LocalDateTime.class)))
                .thenReturn(nextRun);
        when(execRepo.save(waitingExecution)).thenReturn(waitingExecution);

        Optional<SchedulerJobExecution> result =
                service.moveNextWaitingToRunning(jobId);

        assertTrue(result.isPresent());

        SchedulerJobExecution moved = result.get();

        assertEquals(JobExecutionStatus.RUNNING, moved.getStatus());
        assertNotNull(moved.getStartedAt());
        assertNull(moved.getWaitReason());
        assertEquals(JobConstants.JOB_EXECUTION_RETRY, moved.getStartedBy());
        assertEquals(JobConstants.JOB_EXECUTION_RETRY, moved.getTriggerType());
        assertNotNull(moved.getRunningOnHost());
        assertFalse(moved.getTimeoutOccurred());
        assertFalse(moved.getLongRunningAlertSent());
        assertEquals(nextRun, job.getNextRun());

        verify(jobRepo).save(job);
        verify(execRepo).save(waitingExecution);
    }

    @Test
    void moveNextWaitingToRunning_shouldKeepExistingStartedBy() {
        Long jobId = 21L;
        SchedulerJob job = job(jobId);
        job.setCronExpression(null);

        SchedulerJobExecution waitingExecution =
                execution(302L, job, JobExecutionStatus.WAITING);
        waitingExecution.setQueuedAt(LocalDateTime.now().minusSeconds(1));
        waitingExecution.setStartedBy("MANUAL");

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(false);
        when(execRepo.findNextWaitingForUpdate(jobId)).thenReturn(Optional.of(waitingExecution));
        when(execRepo.save(waitingExecution)).thenReturn(waitingExecution);

        Optional<SchedulerJobExecution> result =
                service.moveNextWaitingToRunning(jobId);

        assertTrue(result.isPresent());
        assertEquals("MANUAL", result.get().getStartedBy());
        assertEquals("MANUAL", result.get().getTriggerType());

        verifyNoInteractions(cronService);
    }

    /*
    @Test
    void moveNextWaitingToRunning_shouldMoveWaitingExecutionToRunning() {
        Long jobId = 20L;
        LocalDateTime nextRun = LocalDateTime.now().plusMinutes(5);

        SchedulerJob job = job(jobId);
        job.setCronExpression("0 * * * * *");

        SchedulerJobExecution waitingExecution =
                execution(301L, job, JobExecutionStatus.WAITING);
        waitingExecution.setQueuedAt(LocalDateTime.now().minusSeconds(5));
        waitingExecution.setStartedBy(null);
        waitingExecution.setWaitReason("Retry #1");

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.findTopByJob_JobPkIdAndStatusOrderByQueuedAtAsc(
                jobId,
                JobExecutionStatus.WAITING
        )).thenReturn(Optional.of(waitingExecution));
        when(cronService.computeNextRun(eq("0 * * * * *"), any(LocalDateTime.class)))
                .thenReturn(nextRun);
        when(execRepo.save(waitingExecution)).thenReturn(waitingExecution);

        Optional<SchedulerJobExecution> result =
                service.moveNextWaitingToRunning(jobId);

        assertTrue(result.isPresent());

        SchedulerJobExecution moved = result.get();

        assertEquals(JobExecutionStatus.RUNNING, moved.getStatus());
        assertNotNull(moved.getStartedAt());
        assertNull(moved.getWaitReason());
        assertEquals(JobConstants.JOB_EXECUTION_RETRY, moved.getStartedBy());
        assertEquals(JobConstants.JOB_EXECUTION_RETRY, moved.getTriggerType());
        assertNotNull(moved.getRunningOnHost());
        assertFalse(moved.getTimeoutOccurred());
        assertFalse(moved.getLongRunningAlertSent());
        assertEquals(nextRun, job.getNextRun());

        verify(jobRepo).save(job);
        verify(execRepo).save(waitingExecution);
    }

    @Test
    void moveNextWaitingToRunning_shouldKeepExistingStartedBy() {
        Long jobId = 21L;
        SchedulerJob job = job(jobId);
        job.setCronExpression(null);

        SchedulerJobExecution waitingExecution =
                execution(302L, job, JobExecutionStatus.WAITING);
        waitingExecution.setQueuedAt(LocalDateTime.now().minusSeconds(1));
        waitingExecution.setStartedBy("MANUAL");

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.findTopByJob_JobPkIdAndStatusOrderByQueuedAtAsc(
                jobId,
                JobExecutionStatus.WAITING
        )).thenReturn(Optional.of(waitingExecution));
        when(execRepo.save(waitingExecution)).thenReturn(waitingExecution);

        Optional<SchedulerJobExecution> result =
                service.moveNextWaitingToRunning(jobId);

        assertTrue(result.isPresent());
        assertEquals("MANUAL", result.get().getStartedBy());
        assertEquals("MANUAL", result.get().getTriggerType());

        verifyNoInteractions(cronService);
    }
*/
    @Test
    void startExecutionResultRun_shouldCreateRunResult() {
        SchedulerJobExecution execution = new SchedulerJobExecution();

        JobExecutionTxService.StartExecutionResult result =
                JobExecutionTxService.StartExecutionResult.run(execution);

        assertTrue(result.shouldRun());
        assertSame(execution, result.execution());
        assertEquals("RUNNING", result.reason());
    }

    @Test
    void startExecutionResultSkip_shouldCreateSkipResult() {
        JobExecutionTxService.StartExecutionResult result =
                JobExecutionTxService.StartExecutionResult.skip("reason");

        assertFalse(result.shouldRun());
        assertNull(result.execution());
        assertEquals("reason", result.reason());
    }

    @Test
    void moveNextWaitingToRunning_shouldReturnEmpty_whenJobIsDisabled() {
        Long jobId = 23L;
        SchedulerJob job = job(jobId);
        job.setJobEnabled(false);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));

        Optional<SchedulerJobExecution> result = service.moveNextWaitingToRunning(jobId);

        assertTrue(result.isEmpty());

        verify(execRepo, never()).existsRunningExecutionWithLock(anyLong());
        verify(execRepo, never()).findNextWaitingForUpdate(anyLong());
        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void moveNextWaitingToRunning_shouldReturnEmpty_whenCircuitIsOpen() {
        Long jobId = 24L;
        SchedulerJob job = job(jobId);
        job.setCircuitOpenUntil(LocalDateTime.now().plusMinutes(5));

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));

        Optional<SchedulerJobExecution> result = service.moveNextWaitingToRunning(jobId);

        assertTrue(result.isEmpty());

        verify(execRepo, never()).existsRunningExecutionWithLock(anyLong());
        verify(execRepo, never()).findNextWaitingForUpdate(anyLong());
        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void moveNextWaitingToRunning_shouldReturnEmpty_whenAnotherExecutionIsRunningAndPolicyIsSkip() {
        Long jobId = 25L;
        SchedulerJob job = job(jobId);
        job.setConcurrencyPolicy(JobConstants.JOB_EXECUTION_SKIP);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(true);

        Optional<SchedulerJobExecution> result = service.moveNextWaitingToRunning(jobId);

        assertTrue(result.isEmpty());

        verify(execRepo, never()).findNextWaitingForUpdate(anyLong());
        verify(execRepo, never()).save(any());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void moveNextWaitingToRunning_shouldContinue_whenAnotherExecutionIsRunningAndPolicyIsParallel() {
        Long jobId = 26L;
        SchedulerJob job = job(jobId);
        job.setConcurrencyPolicy(JobConstants.JOB_EXECUTION_PARALLEL);

        SchedulerJobExecution waitingExecution =
                execution(401L, job, JobExecutionStatus.WAITING);
        waitingExecution.setQueuedAt(LocalDateTime.now().minusSeconds(1));

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(true);
        when(execRepo.findNextWaitingForUpdate(jobId)).thenReturn(Optional.of(waitingExecution));
        when(execRepo.save(waitingExecution)).thenReturn(waitingExecution);

        Optional<SchedulerJobExecution> result = service.moveNextWaitingToRunning(jobId);

        assertTrue(result.isPresent());
        assertEquals(JobExecutionStatus.RUNNING, result.get().getStatus());

        verify(execRepo).findNextWaitingForUpdate(jobId);
        verify(execRepo).save(waitingExecution);
        verify(jobRepo).save(job);
    }

    @Test
    void startExecution_shouldUseSeparateStartedByAndTriggerType() {
        Long jobId = 27L;
        SchedulerJob job = job(jobId);

        when(jobRepo.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(jobId)).thenReturn(false);
        when(execRepo.save(any(SchedulerJobExecution.class)))
                .thenAnswer(invocation -> {
                    SchedulerJobExecution exec = invocation.getArgument(0);
                    exec.setJobExecPkId(501L);
                    return exec;
                });

        JobExecutionTxService.StartExecutionResult result =
                service.startExecution(jobId, "USER123", JobConstants.JOB_EXECUTION_MANUAL);

        assertTrue(result.shouldRun());
        assertEquals("USER123", result.execution().getStartedBy());
        assertEquals(JobConstants.JOB_EXECUTION_MANUAL, result.execution().getTriggerType());

        verify(execRepo).save(any(SchedulerJobExecution.class));
        verify(jobRepo).save(job);
    }

    @Test
    void markFailure_shouldSendRetriesExhaustedAlert_whenRetryLimitReached() {
        Long executionId = 601L;

        SchedulerJob job = job(60L);
        job.setRetryCount(1);

        SchedulerJobExecution execution = execution(executionId, job, JobExecutionStatus.RUNNING);
        execution.setRetryNumber(1);

        when(execRepo.findById(executionId)).thenReturn(Optional.of(execution));
        when(execRepo.save(execution)).thenReturn(execution);

        SchedulerJobExecution result =
                service.markFailure(executionId, new RuntimeException("final failure"));

        assertEquals(JobExecutionStatus.FAILED, result.getStatus());

        verify(alertService).sendRetriesExhaustedAlert(job, execution);
        verify(alertService, never()).sendRetryScheduledAlert(any(), any());
    }

    @Test
    void markFailure_shouldSendRetryScheduledAlert_whenRetryIsCreated() {
        Long executionId = 602L;

        SchedulerJob job = job(61L);
        job.setRetryCount(2);
        job.setRetryDelaySeconds(0);

        SchedulerJobExecution execution = execution(executionId, job, JobExecutionStatus.RUNNING);
        execution.setRetryNumber(0);

        when(execRepo.findById(executionId)).thenReturn(Optional.of(execution));
        when(execRepo.save(any(SchedulerJobExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.markFailure(executionId, new RuntimeException("retryable failure"));

        ArgumentCaptor<SchedulerJobExecution> retryCaptor =
                ArgumentCaptor.forClass(SchedulerJobExecution.class);

        verify(alertService).sendRetryScheduledAlert(eq(job), retryCaptor.capture());
        verify(alertService, never()).sendRetriesExhaustedAlert(any(), any());

        SchedulerJobExecution retry = retryCaptor.getValue();

        assertEquals(JobExecutionStatus.WAITING, retry.getStatus());
        assertEquals(1, retry.getRetryNumber());
        assertEquals(JobConstants.JOB_EXECUTION_RETRY, retry.getStartedBy());
    }
}