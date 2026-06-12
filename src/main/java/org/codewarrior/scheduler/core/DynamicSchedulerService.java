package org.codewarrior.scheduler.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@RequiredArgsConstructor
@Log4j2
public class DynamicSchedulerService {

    private final ThreadPoolTaskScheduler threadPoolTaskScheduler;
    private final JobRunnerService jobRunnerService;

    private final Map<Long, ScheduledFuture<?>> scheduledJobs = new ConcurrentHashMap<>();

    public synchronized void scheduleJob(Long jobId, Runnable jobTask, String cronExpression) {
        cancel(jobId);

        log.info("Scheduling job {} with cron {}", jobId, cronExpression);

        ScheduledFuture<?> future =
                threadPoolTaskScheduler.schedule(jobTask, new CronTrigger(cronExpression));

        scheduledJobs.put(jobId, future);
    }

    public synchronized void cancel(Long jobId) {
        ScheduledFuture<?> future = scheduledJobs.get(jobId);

        if (future != null) {
            log.info("Cancelling scheduled job {}", jobId);
            future.cancel(false);
            scheduledJobs.remove(jobId);
        }
    }

    /**
     * User-triggered manual run.
     */

    public void runNow(Long jobId, String userId) {
        log.info("User {} manually triggered job {}", userId, jobId);

        threadPoolTaskScheduler.submit(() -> {
            try {
                jobRunnerService.runJob(jobId, userId, "MANUAL", true);
            } catch (Exception ex) {
                log.error("Error running job {} via runNow: {}", jobId, ex.getMessage(), ex);
            }
        });
    }

}
