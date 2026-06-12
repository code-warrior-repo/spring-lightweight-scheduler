package org.codewarrior.scheduler.integration;

import org.codewarrior.scheduler.constants.JobConstants;
import org.codewarrior.scheduler.core.JobExecutionTxService;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import org.codewarrior.scheduler.dto.JobExecutionStatus;
import org.codewarrior.scheduler.repository.SchedulerJobExecutionRepository;
import org.codewarrior.scheduler.repository.SchedulerJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ActiveProfiles("integ-test")
@SpringBootTest
@Transactional
class QueueFlowIntegrationTest {

    @Autowired
    SchedulerJobRepository jobRepo;

    @Autowired
    SchedulerJobExecutionRepository execRepo;

    @Autowired
    JobExecutionTxService txService;

    @Test
    void startExecution_shouldCreateWaitingExecution_whenPolicyIsQueueAndJobAlreadyRunning() {
        SchedulerJob job = new SchedulerJob();
        job.setJobName("QueueJob");
        job.setJobType("TestJob");
        job.setCronExpression("0/5 * * * * *");
        job.setJobEnabled(true);
        job.setExecutionCount(0);
        job.setConcurrencyPolicy(JobConstants.JOB_EXECUTION_QUEUE);
        job.setMaxWaitingQueueSize(5);
        job.setRetryCount(0);
        job.setRetryDelaySeconds(0);
        job.setMaxAllowedDurationMs(60000L);

        SchedulerJob savedJob = jobRepo.save(job);

        SchedulerJobExecution running = new SchedulerJobExecution();
        running.setJob(savedJob);
        running.setStatus(JobExecutionStatus.RUNNING);
        running.setStartedAt(LocalDateTime.now());
        running.setStartedBy("CRON");
        running.setTriggerType("CRON");
        running.setRetryNumber(0);
        running.setTimeoutOccurred(false);
        running.setLongRunningAlertSent(false);
        execRepo.save(running);

        JobExecutionTxService.StartExecutionResult result =
                txService.startExecution(savedJob.getJobPkId(), "CRON");

        assertFalse(result.shouldRun());
        assertEquals("Queued", result.reason());

        List<SchedulerJobExecution> waiting =
                execRepo.findByJob_JobPkIdAndStatus(savedJob.getJobPkId(), JobExecutionStatus.WAITING);

        assertEquals(1, waiting.size());
        assertEquals("Already running", waiting.get(0).getWaitReason());
    }

    @Test
    void startExecution_shouldCreateSkippedExecution_whenQueueIsFull() {
        SchedulerJob job = new SchedulerJob();
        job.setJobName("QueueFullJob");
        job.setJobType("TestJob");
        job.setCronExpression("0/5 * * * * *");
        job.setJobEnabled(true);
        job.setExecutionCount(0);
        job.setConcurrencyPolicy(JobConstants.JOB_EXECUTION_QUEUE);
        job.setMaxWaitingQueueSize(0);
        job.setRetryCount(0);
        job.setRetryDelaySeconds(0);
        job.setMaxAllowedDurationMs(60000L);

        SchedulerJob savedJob = jobRepo.save(job);

        SchedulerJobExecution running = new SchedulerJobExecution();
        running.setJob(savedJob);
        running.setStatus(JobExecutionStatus.RUNNING);
        running.setStartedAt(LocalDateTime.now());
        running.setStartedBy("CRON");
        running.setTriggerType("CRON");
        running.setRetryNumber(0);
        running.setTimeoutOccurred(false);
        running.setLongRunningAlertSent(false);
        execRepo.save(running);

        JobExecutionTxService.StartExecutionResult result =
                txService.startExecution(savedJob.getJobPkId(), "CRON");

        assertFalse(result.shouldRun());

        List<SchedulerJobExecution> skipped =
                execRepo.findByJob_JobPkIdAndStatus(savedJob.getJobPkId(), JobExecutionStatus.SKIPPED);

        assertEquals(1, skipped.size());
        assertEquals("Queue full", skipped.get(0).getWaitReason());
    }
}