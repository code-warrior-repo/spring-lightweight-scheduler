package org.codewarrior.scheduler.core;

import org.codewarrior.scheduler.constants.JobConstants;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import org.codewarrior.scheduler.dto.JobExecutionStatus;
import org.codewarrior.scheduler.repository.SchedulerJobExecutionRepository;
import org.codewarrior.scheduler.repository.SchedulerJobRepository;
import org.codewarrior.scheduler.service.AlertEmailService;
import org.codewarrior.scheduler.service.CronService;
import org.codewarrior.scheduler.util.HostUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Log4j2
public class JobExecutionTxService {

    private static final String JOB = "[JOB {}] ";
    private static final String EXEC = "[EXEC {}] ";
    private static final String WAIT = "[WAIT {}] ";
    private static final String SYSTEM = "SYSTEM";
    private static final Random RANDOM = new Random();

    private final SchedulerJobExecutionRepository execRepo;
    private final SchedulerJobRepository jobRepo;
    private final CronService cronService;
    private final AlertEmailService alertEmailService;

    @Transactional
    public SchedulerJobExecution save(SchedulerJobExecution exec) {
        SchedulerJobExecution saved = execRepo.save(exec);
        log.debug(EXEC + "Saved with status {}", saved.getJobExecPkId(), saved.getStatus());
        return saved;
    }

    @Transactional
    public SchedulerJob findJobForExecution(Long jobId) {
        return jobRepo.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found"));
    }

    @Transactional
    public StartExecutionResult startExecution(Long jobId, String triggeredBy) {
        return startExecution(jobId, triggeredBy, triggeredBy);
    }


    @Transactional
    public StartExecutionResult startExecution(Long jobId, String startedBy, String triggerType) {

        SchedulerJob job = jobRepo.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found"));

        if (!Boolean.TRUE.equals(job.getJobEnabled())) {
            log.warn(JOB + "Job is disabled", jobId);
            return StartExecutionResult.skip("Job disabled");
        }

        if (job.getCircuitOpenUntil() != null &&
                job.getCircuitOpenUntil().isAfter(LocalDateTime.now())) {
            log.warn(JOB + "Circuit OPEN until {}", jobId, job.getCircuitOpenUntil());
            return StartExecutionResult.skip("Circuit open");
        }

        boolean isRunning = execRepo.existsRunningExecutionWithLock(jobId);

        if (isRunning) {
            String concurrencyPolicy = Optional.ofNullable(job.getConcurrencyPolicy())
                    .orElse(JobConstants.JOB_EXECUTION_SKIP);

            switch (concurrencyPolicy) {
                case JobConstants.JOB_EXECUTION_SKIP:
                    log.warn(JOB + "SKIP — already running", jobId);
                    return StartExecutionResult.skip("Already running");

                case JobConstants.JOB_EXECUTION_QUEUE:
                    log.warn(JOB + "Already RUNNING → queueing", jobId);
                    queueJob(job, "Already running");
                    return StartExecutionResult.skip("Queued");

                case JobConstants.JOB_EXECUTION_PARALLEL:
                    log.warn(JOB + "PARALLEL policy selected — allowing another run", jobId);
                    break;

                default:
                    log.warn(JOB + "Unknown concurrency policy '{}'. Defaulting to SKIP",
                            jobId,
                            concurrencyPolicy);
                    return StartExecutionResult.skip("Unknown concurrency policy");
            }
        }

        SchedulerJobExecution exec = createRunningExecution(job, startedBy, triggerType);

        return StartExecutionResult.run(exec);
    }

    private SchedulerJobExecution createRunningExecution(SchedulerJob job, String startedBy, String triggerType) {

        SchedulerJobExecution exec = new SchedulerJobExecution();
        exec.setJob(job);
        exec.setStartedBy(startedBy);
        exec.setTriggerType(triggerType);
        exec.setRetryNumber(0);
        exec.setStartedAt(LocalDateTime.now());
        exec.setRunningOnHost(HostUtil.hostname());
        exec.setStatus(JobExecutionStatus.RUNNING);
        exec.setTimeoutOccurred(false);
        exec.setLongRunningAlertSent(false);

        job.setLastRunStartedAt(exec.getStartedAt());

        if (job.getCronExpression() != null) {
            job.setNextRun(cronService.computeNextRun(job.getCronExpression(), LocalDateTime.now()));
        }

        SchedulerJobExecution saved = execRepo.save(exec);
        jobRepo.save(job);

        log.info(EXEC + "RUNNING started for job {}", saved.getJobExecPkId(), job.getJobName());

        return saved;
    }

    private SchedulerJobExecution queueJob(SchedulerJob job, String reason) {

        long waiting = execRepo.countByJob_JobPkIdAndStatus(
                job.getJobPkId(),
                JobExecutionStatus.WAITING
        );

        int maxWaitingQueueSize = Optional.ofNullable(job.getMaxWaitingQueueSize()).orElse(0);

        log.info(JOB + "Queue check, WAITING={} Reason={}",
                job.getJobPkId(),
                waiting,
                reason);

        if (waiting >= maxWaitingQueueSize) {
            SchedulerJobExecution skipped = new SchedulerJobExecution();
            skipped.setJob(job);
            skipped.setStatus(JobExecutionStatus.SKIPPED);
            skipped.setStartedBy(SYSTEM);
            skipped.setTriggerType(SYSTEM);
            skipped.setQueuedAt(LocalDateTime.now());
            skipped.setWaitReason("Queue full");
            skipped.setRetryNumber(0);
            skipped.setTimeoutOccurred(false);
            skipped.setLongRunningAlertSent(false);

            SchedulerJobExecution saved = execRepo.save(skipped);

            log.warn(EXEC + "SKIPPED due to queue full", saved.getJobExecPkId());
            return saved;
        }

        SchedulerJobExecution waitingExec = new SchedulerJobExecution();
        waitingExec.setJob(job);
        waitingExec.setStatus(JobExecutionStatus.WAITING);
        waitingExec.setStartedBy(JobConstants.JOB_EXECUTION_RETRY);
        waitingExec.setTriggerType(JobConstants.JOB_EXECUTION_RETRY);
        waitingExec.setQueuedAt(LocalDateTime.now());
        waitingExec.setWaitReason(reason);
        waitingExec.setRetryNumber(0);
        waitingExec.setRunningOnHost(null);
        waitingExec.setTimeoutOccurred(false);
        waitingExec.setLongRunningAlertSent(false);

        SchedulerJobExecution saved = execRepo.save(waitingExec);

        log.info(WAIT + "Added to WAITING queue for job {}",
                saved.getJobExecPkId(),
                job.getJobName());

        return saved;
    }

    @Transactional
    public SchedulerJobExecution markSuccess(Long executionId) {

        SchedulerJobExecution exec = execRepo.findById(executionId)
                .orElseThrow(() -> new IllegalStateException("Execution not found"));

        if (exec.getStatus() != JobExecutionStatus.RUNNING) {
            log.warn(EXEC + "Cannot mark SUCCESS because current status is {}",
                    exec.getJobExecPkId(),
                    exec.getStatus());
            return exec;
        }

        SchedulerJob job = exec.getJob();

        exec.setStatus(JobExecutionStatus.SUCCESS);
        exec.setCompletedAt(LocalDateTime.now());
        exec.setDurationMs(computeDurationMs(exec));
        exec.setFailureType(null);
        exec.setErrorMessage(null);
        exec.setTimeoutOccurred(false);

        job.setLastRunStatus(JobConstants.JOB_EXECUTION_STATUS_SUCCESS);
        job.setLastRunCompletedAt(exec.getCompletedAt());
        job.setLastRunError(null);
        job.setExecutionCount(safeInt(job.getExecutionCount()) + 1);

        if (job.getCronExpression() != null) {
            job.setNextRun(cronService.computeNextRun(job.getCronExpression(), LocalDateTime.now()));
        }

        jobRepo.save(job);
        SchedulerJobExecution saved = execRepo.save(exec);

        log.info(EXEC + "SUCCESS duration={}ms", saved.getJobExecPkId(), saved.getDurationMs());

        return saved;
    }

    @Transactional
    public SchedulerJobExecution markFailure(Long executionId, Exception ex) {

        SchedulerJobExecution exec = execRepo.findById(executionId)
                .orElseThrow(() -> new IllegalStateException("Execution not found"));

        if (exec.getStatus() != JobExecutionStatus.RUNNING) {
            log.warn(EXEC + "Cannot mark FAILED because current status is {}",
                    exec.getJobExecPkId(),
                    exec.getStatus());
            return exec;
        }

        SchedulerJob job = exec.getJob();

        exec.setStatus(JobExecutionStatus.FAILED);
        exec.setCompletedAt(LocalDateTime.now());
        exec.setDurationMs(computeDurationMs(exec));
        exec.setErrorMessage(ExceptionUtils.getStackTrace(ex));
        exec.setFailureType(JobConstants.JOB_EXECUTION_STATUS_SYSTEM_ERROR);
        exec.setTimeoutOccurred(false);

        job.setLastRunStatus(JobConstants.JOB_EXECUTION_STATUS_FAILED);
        job.setLastRunCompletedAt(exec.getCompletedAt());
        job.setLastRunError(exec.getErrorMessage());
        job.setLastFailureAt(exec.getCompletedAt());
        job.setExecutionCount(safeInt(job.getExecutionCount()) + 1);
        job.setAlertFailureCount(safeInt(job.getAlertFailureCount()) + 1);

        jobRepo.save(job);
        SchedulerJobExecution saved = execRepo.save(exec);

        Optional<SchedulerJobExecution> retry = createRetryIfAllowed(saved, job);

        if (retry.isPresent()) {
            alertEmailService.sendRetryScheduledAlert(job, retry.get());
        } else {
            alertEmailService.sendRetriesExhaustedAlert(job, saved);
        }

        log.warn(EXEC + "FAILED duration={}ms", saved.getJobExecPkId(), saved.getDurationMs());

        return saved;
    }


    private Optional<SchedulerJobExecution> createRetryIfAllowed(SchedulerJobExecution failedExec, SchedulerJob job) {

        int retryNumber = safeInt(failedExec.getRetryNumber());
        int retryCount = safeInt(job.getRetryCount());

        if (retryNumber >= retryCount) {
            return Optional.empty();
        }

        long jitter = RANDOM.nextInt(3);
        long retryDelaySeconds = safeInt(job.getRetryDelaySeconds());
        long delay = retryDelaySeconds + jitter;

        LocalDateTime queuedAt = LocalDateTime.now().plusSeconds(delay);

        SchedulerJobExecution retry = new SchedulerJobExecution();
        retry.setJob(job);
        retry.setRetryNumber(retryNumber + 1);
        retry.setStatus(JobExecutionStatus.WAITING);
        retry.setQueuedAt(queuedAt);
        retry.setWaitReason("Retry #" + retry.getRetryNumber());
        retry.setStartedBy(JobConstants.JOB_EXECUTION_RETRY);
        retry.setTriggerType(JobConstants.JOB_EXECUTION_RETRY);
        retry.setRunningOnHost(null);
        retry.setTimeoutOccurred(false);
        retry.setLongRunningAlertSent(false);

        SchedulerJobExecution saved = execRepo.save(retry);

        log.info(WAIT + "Added retry #{} queuedAt={}",
                saved.getJobExecPkId(),
                saved.getRetryNumber(),
                queuedAt);

        return Optional.of(saved);
    }

    @Transactional
    public SchedulerJobExecution markTimeout(Long executionId) {

        SchedulerJobExecution exec = execRepo.findById(executionId)
                .orElseThrow(() -> new IllegalStateException("Execution not found"));

        if (exec.getStatus() != JobExecutionStatus.RUNNING) {
            log.warn(EXEC + "Cannot mark TIMEOUT because current status is {}",
                    exec.getJobExecPkId(),
                    exec.getStatus());
            return exec;
        }

        SchedulerJob job = exec.getJob();

        exec.setStatus(JobExecutionStatus.TIMEOUT);
        exec.setTimeoutOccurred(true);
        exec.setCompletedAt(LocalDateTime.now());
        exec.setDurationMs(computeDurationMs(exec));
        exec.setFailureType(JobConstants.JOB_EXECUTION_STATUS_TIMEOUT);

        job.setLastRunStatus(JobConstants.JOB_EXECUTION_STATUS_TIMEOUT);
        job.setLastRunCompletedAt(exec.getCompletedAt());
        job.setExecutionCount(safeInt(job.getExecutionCount()) + 1);

        jobRepo.save(job);
        SchedulerJobExecution saved = execRepo.save(exec);

        log.warn(EXEC + "TIMEOUT marked", saved.getJobExecPkId());

        return saved;
    }

    @Transactional
    public Optional<SchedulerJobExecution> fetchNextWaiting(Long jobId) {

        Optional<SchedulerJobExecution> next =
                execRepo.findTopByJob_JobPkIdAndStatusOrderByQueuedAtAsc(
                        jobId,
                        JobExecutionStatus.WAITING
                );

        next.ifPresent(exec ->
                log.info(WAIT + "Next waiting execution at {}",
                        exec.getJobExecPkId(),
                        exec.getQueuedAt()));

        return next;
    }

    @Transactional
    public Optional<SchedulerJobExecution> moveNextWaitingToRunning(Long jobId) {

        SchedulerJob job = jobRepo.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found"));

        if (!Boolean.TRUE.equals(job.getJobEnabled())) {
            log.warn(JOB + "WAITING execution not started because job is disabled", jobId);
            return Optional.empty();
        }

        if (job.getCircuitOpenUntil() != null &&
                job.getCircuitOpenUntil().isAfter(LocalDateTime.now())) {
            log.warn(JOB + "WAITING execution not started because circuit is OPEN until {}",
                    jobId,
                    job.getCircuitOpenUntil());
            return Optional.empty();
        }

        boolean isRunning = execRepo.existsRunningExecutionWithLock(jobId);
        String concurrencyPolicy = Optional.ofNullable(job.getConcurrencyPolicy())
                .orElse(JobConstants.JOB_EXECUTION_SKIP);

        if (isRunning && !JobConstants.JOB_EXECUTION_PARALLEL.equals(concurrencyPolicy)) {
            log.info(JOB + "WAITING execution not started because another execution is RUNNING. Policy={}",
                    jobId,
                    concurrencyPolicy);
            return Optional.empty();
        }

        Optional<SchedulerJobExecution> nextOpt = execRepo.findNextWaitingForUpdate(jobId);

        if (nextOpt.isEmpty()) {
            return Optional.empty();
        }

        SchedulerJobExecution next = nextOpt.get();

        if (next.getQueuedAt() != null &&
                next.getQueuedAt().isAfter(LocalDateTime.now())) {
            log.info(EXEC + "WAITING but retry/queue delay not reached", next.getJobExecPkId());
            return Optional.empty();
        }

        next.setJob(job);
        next.setStatus(JobExecutionStatus.RUNNING);
        next.setStartedAt(LocalDateTime.now());
        next.setWaitReason(null);

        if (next.getStartedBy() == null) {
            next.setStartedBy(JobConstants.JOB_EXECUTION_RETRY);
        }

        next.setTriggerType(next.getStartedBy());
        next.setRunningOnHost(HostUtil.hostname());
        next.setTimeoutOccurred(false);
        next.setLongRunningAlertSent(false);

        job.setLastRunStartedAt(next.getStartedAt());

        if (job.getCronExpression() != null) {
            job.setNextRun(cronService.computeNextRun(job.getCronExpression(), LocalDateTime.now()));
        }

        jobRepo.save(job);
        SchedulerJobExecution saved = execRepo.save(next);

        log.info(EXEC + "WAITING → RUNNING", saved.getJobExecPkId());

        return Optional.of(saved);
    }

    private Long computeDurationMs(SchedulerJobExecution exec) {
        if (exec.getStartedAt() == null) {
            return null;
        }

        LocalDateTime completedAt = exec.getCompletedAt() != null
                ? exec.getCompletedAt()
                : LocalDateTime.now();

        return Duration.between(exec.getStartedAt(), completedAt).toMillis();
    }

    private int safeInt(Integer value) {
        return Optional.ofNullable(value).orElse(0);
    }

    public record StartExecutionResult(
            boolean shouldRun,
            SchedulerJobExecution execution,
            String reason
    ) {
        public static StartExecutionResult run(SchedulerJobExecution execution) {
            return new StartExecutionResult(true, execution, "RUNNING");
        }

        public static StartExecutionResult skip(String reason) {
            return new StartExecutionResult(false, null, reason);
        }
    }
}