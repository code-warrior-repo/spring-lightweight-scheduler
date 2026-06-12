package org.codewarrior.scheduler.service;

import org.codewarrior.scheduler.core.JobExecutionTxService;
import org.codewarrior.scheduler.core.JobRunnerService;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import org.codewarrior.scheduler.dto.JobExecutionStatus;
import org.codewarrior.scheduler.repository.SchedulerJobExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
public class JobTimeoutMonitor {

    private static final long ONE_MIN_MS = 60_000L;
    private static final String EXEC = "[EXEC {}] ";
    private static final String JOB = "[JOB {}] ";

    private final SchedulerJobExecutionRepository execRepo;
    private final JobRunnerService jobRunnerService;
    private final AlertEmailService alertEmailService;
    private final JobExecutionTxService txService;

    @Value("${scheduler.alerts.longRunningThresholdMinutes:60}")
    private int longRunningMinutes;


    @Scheduled(fixedDelay = ONE_MIN_MS)
    public void checkTimeouts() {

        log.debug("[MONITOR] Checking for long‑running and timeout jobs...");

        List<SchedulerJobExecution> running =
                execRepo.findByStatus(JobExecutionStatus.RUNNING);

        LocalDateTime now = LocalDateTime.now();

        for (SchedulerJobExecution exec : running) {
            inspect(exec, now);
        }
    }


    private void inspect(SchedulerJobExecution exec, LocalDateTime now) {

        SchedulerJob job = exec.getJob();

        log.debug(EXEC + "Inspecting job {}", exec.getJobExecPkId(), job.getJobName());

        checkLongRunning(exec);

        Long maxMs = job.getMaxAllowedDurationMs();
        if (maxMs == null || maxMs <= 0) return;

        long runningMs = Duration.between(exec.getStartedAt(), now).toMillis();

        if (runningMs > maxMs) {

            log.warn(EXEC + "TIMEOUT detected for job {} ({}ms > {}ms)",
                    exec.getJobExecPkId(), job.getJobName(), runningMs, maxMs);

            txService.markTimeout(exec.getJobExecPkId());
            alertEmailService.sendTimeoutAlert(job, exec);
            jobRunnerService.processWaiting(job.getJobPkId());
        }
    }


    private void checkLongRunning(SchedulerJobExecution exec) {

        if (Boolean.TRUE.equals(exec.getLongRunningAlertSent()))
            return;

        long runningMs = Duration.between(exec.getStartedAt(), LocalDateTime.now()).toMillis();
        long thresholdMs = longRunningMinutes * ONE_MIN_MS;

        if (runningMs > thresholdMs) {

            SchedulerJob job = exec.getJob();

            log.warn(EXEC + "Long-running execution detected (> {} mins) for job {}",
                    exec.getJobExecPkId(), longRunningMinutes, job.getJobName());

            alertEmailService.sendLongRunningAlert(job, exec);

            exec.setLongRunningAlertSent(true);
            txService.save(exec);
        }
    }
}