package org.codewarrior.scheduler.integration;

import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import org.codewarrior.scheduler.dto.JobExecutionStatus;
import org.codewarrior.scheduler.repository.SchedulerJobExecutionRepository;
import org.codewarrior.scheduler.repository.SchedulerJobRepository;
import org.codewarrior.scheduler.service.JobTimeoutMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("integ-test")
@SpringBootTest
@Transactional
class TimeoutMonitorIntegrationTest {

    @Autowired
    SchedulerJobRepository jobRepo;

    @Autowired
    SchedulerJobExecutionRepository execRepo;

    @Autowired
    JobTimeoutMonitor monitor;

    @Test
    void checkTimeouts_shouldMarkRunningExecutionAsTimeout() {
        SchedulerJob job = new SchedulerJob();
        job.setJobName("TimeoutMonitorJob");
        job.setJobType("TestJob");
        job.setCronExpression("0/5 * * * * *");
        job.setJobEnabled(true);
        job.setExecutionCount(0);
        job.setRetryCount(0);
        job.setRetryDelaySeconds(0);
        job.setMaxWaitingQueueSize(5);
        job.setMaxAllowedDurationMs(10L);

        SchedulerJob savedJob = jobRepo.save(job);

        SchedulerJobExecution exec = new SchedulerJobExecution();
        exec.setJob(savedJob);
        exec.setStatus(JobExecutionStatus.RUNNING);
        exec.setStartedAt(LocalDateTime.now().minusSeconds(5));
        exec.setStartedBy("CRON");
        exec.setTriggerType("CRON");
        exec.setRetryNumber(0);
        exec.setTimeoutOccurred(false);
        exec.setLongRunningAlertSent(false);

        SchedulerJobExecution savedExec = execRepo.save(exec);

        monitor.checkTimeouts();

        SchedulerJobExecution refreshed =
                execRepo.findById(savedExec.getJobExecPkId()).orElseThrow();

        assertEquals(JobExecutionStatus.TIMEOUT, refreshed.getStatus());
        assertTrue(refreshed.getTimeoutOccurred());
        assertNotNull(refreshed.getCompletedAt());
    }
}