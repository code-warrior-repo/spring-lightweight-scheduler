package org.codewarrior.scheduler.service;

import org.codewarrior.scheduler.core.JobRunnerService;
import org.codewarrior.scheduler.repository.SchedulerJobExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
public class WaitingExecutionDispatcher {
    private final SchedulerJobExecutionRepository execRepo;
    private final JobRunnerService jobRunnerService;

    @Value("${scheduler.waiting.dispatcher.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${scheduler.waiting.dispatcher.fixedDelayMs:30000}")
    public void dispatchDueWaitingExecutions() {
        if (!enabled) {
            log.debug("[WAIT-DISPATCHER] Disabled");
            return;
        }

        List<Long> jobIds = execRepo.findJobIdsWithDueWaitingExecutions(LocalDateTime.now());

        if (jobIds.isEmpty()) {
            log.debug("[WAIT-DISPATCHER] No due WAITING executions found");
            return;
        }

        log.info("[WAIT-DISPATCHER] Found {} job(s) with due WAITING executions", jobIds.size());

        for (Long jobId : jobIds) {
            try {
                jobRunnerService.processWaiting(jobId);
            } catch (Exception ex) {
                log.error("[WAIT-DISPATCHER][JOB {}] Failed to process WAITING executions: {}",
                        jobId,
                        ex.getMessage(),
                        ex);
            }
        }
    }
}

