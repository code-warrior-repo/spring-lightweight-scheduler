package org.codewarrior.scheduler.core;

import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import org.codewarrior.scheduler.service.AlertEmailService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Log4j2
public class JobRunnerService {

    private static final String JOB = "[JOB {}] ";
    private static final String EXEC = "[EXEC {}] ";
    private final JobExecutionTxService txService;
    private final JobRegistry jobRegistry;
    private final AlertEmailService alertEmailService;

    @Value("${scheduler.runner.corePoolSize:4}")
    private int corePoolSize;

    @Value("${scheduler.runner.maxPoolSize:16}")
    private int maxPoolSize;

    @Value("${scheduler.runner.queueCapacity:500}")
    private int queueCapacity;

    @Value("${scheduler.runner.keepAliveSeconds:60}")
    private int keepAliveSeconds;

    @Value("${scheduler.runner.shutdownTimeoutSeconds:30}")
    private int shutdownTimeoutSeconds;

    private ExecutorService executor;

    @PostConstruct
    public void initPool() {
        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @PreDestroy
    public void shutdownPool() {
        if (executor == null) {
            return;
        }

        executor.shutdown();

        try {
            if (!executor.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("[RUNNER] Executor did not terminate within {} seconds. Forcing shutdown.",
                        shutdownTimeoutSeconds);
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public void runJob(Long jobId, String triggeredBy) {
        runJob(jobId, triggeredBy, true);
    }

    public void runJob(Long jobId, String triggeredBy, boolean processWaiting) {
        runJob(jobId, triggeredBy, triggeredBy, processWaiting);
    }

    public void runJob(Long jobId, String startedBy, String triggerType, boolean processWaiting) {

        log.info(JOB + "Triggered by {} triggerType={}", jobId, startedBy, triggerType);

        JobExecutionTxService.StartExecutionResult startResult =
                txService.startExecution(jobId, startedBy, triggerType);


        if (!startResult.shouldRun()) {
            log.info(JOB + "Execution not started. Reason={}", jobId, startResult.reason());
            return;
        }

        SchedulerJobExecution exec = startResult.execution();
        SchedulerJob job = exec.getJob();

        try {
            runWithTimeout(job, exec);
            txService.markSuccess(exec.getJobExecPkId());

        } catch (TimeoutException te) {
            log.error(EXEC + "Timeout", exec.getJobExecPkId(), te);

            SchedulerJobExecution updated = txService.markTimeout(exec.getJobExecPkId());
            alertEmailService.sendTimeoutAlert(updated.getJob(), updated);

        } catch (Exception ex) {
            log.error(EXEC + "Failed with exception", exec.getJobExecPkId(), ex);

            txService.markFailure(exec.getJobExecPkId(), ex);
        }

        if (processWaiting) {
            processWaiting(jobId);
        }
    }

    private void runWithTimeout(SchedulerJob job, SchedulerJobExecution exec)
            throws Exception {

        Runnable runnable = jobRegistry.create(job.getJobType(), job, exec);

        long timeoutMs = Optional.ofNullable(job.getMaxAllowedDurationMs()).orElse(0L);

        if (timeoutMs <= 0) {
            log.info(EXEC + "Running WITHOUT timeout", exec.getJobExecPkId());
            runnable.run();
            return;
        }

        log.info(EXEC + "Running WITH timeout={}ms", exec.getJobExecPkId(), timeoutMs);

        Future<?> future = executor.submit(runnable);

        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw te;
        }
    }

    public void processWaiting(Long jobId) {
        log.info(JOB + "Processing WAITING queue", jobId);

        while (true) {
            Optional<SchedulerJobExecution> nextOpt = txService.moveNextWaitingToRunning(jobId);

            if (nextOpt.isEmpty()) {
                log.debug(JOB + "Queue empty or next retry delay not reached", jobId);
                return;
            }

            SchedulerJobExecution next = nextOpt.get();
            SchedulerJob job = next.getJob();

            try {
                runWithTimeout(job, next);
                txService.markSuccess(next.getJobExecPkId());

            } catch (TimeoutException te) {
                log.error(EXEC + "Timeout while processing WAITING execution", next.getJobExecPkId(), te);

                SchedulerJobExecution updated = txService.markTimeout(next.getJobExecPkId());
                alertEmailService.sendTimeoutAlert(updated.getJob(), updated);

            } catch (Exception ex) {
                log.error(EXEC + "Failed while processing WAITING execution", next.getJobExecPkId(), ex);

                SchedulerJobExecution updated = txService.markFailure(next.getJobExecPkId(), ex);
                alertEmailService.sendFailureAlert(updated.getJob(), updated);
            }
        }
    }
}