package org.codewarrior.scheduler.integration;

import org.codewarrior.scheduler.core.JobRegistry;
import org.codewarrior.scheduler.core.JobRunnerService;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@ActiveProfiles("integ-test")
@SpringBootTest
@Transactional
class RetryFlowIntegrationTest {

    @Autowired
    SchedulerJobRepository jobRepo;

    @Autowired
    SchedulerJobExecutionRepository execRepo;

    @Autowired
    JobRunnerService jobRunnerService;

    @Autowired
    JobRegistry jobRegistry;

    @Test
    void testRetryFlow() {

        // --------------------------------------------------------------------
        // 1. Create job
        // --------------------------------------------------------------------
        SchedulerJob job = new SchedulerJob();
        job.setJobName("RetryTestJob");
        job.setJobType("TestJob");
        job.setCronExpression("0/2 * * * * *");
        job.setJobParameters("{}");
        job.setJobEnabled(true);
        job.setExecutionCount(0);
        job.setRetryCount(2);
        job.setRetryDelaySeconds(0);
        job.setManualTriggerAllowed(true);
        job.setMaxAllowedDurationMs(100L);

        job = jobRepo.save(job);
        Long jobId = job.getJobPkId();

        // --------------------------------------------------------------------
        // 2. Dynamically override FAIL_ALWAYS job type for testing
        //    (This is supported even in annotation-based JobRegistry)
        // --------------------------------------------------------------------
        jobRegistry.register("TestJob",
                (j, exec) -> () -> {
                    throw new RuntimeException("Forced failure for retry test");
                }
        );

        // --------------------------------------------------------------------
        // 3. Run the job
        // --------------------------------------------------------------------
        jobRunnerService.runJob(jobId, "CRON", false);

        // --------------------------------------------------------------------
        // 4. Validate retry entry created
        // --------------------------------------------------------------------
        List<SchedulerJobExecution> waiting =
                execRepo.findByJob_JobPkIdAndStatus(jobId, JobExecutionStatus.WAITING);

        assertFalse(waiting.isEmpty(), "Retry should have produced WAITING executions");
        SchedulerJobExecution retryExec = waiting.get(0);

        assertEquals("RETRY", retryExec.getStartedBy());
        assertEquals(1, retryExec.getRetryNumber());
        assertNotNull(retryExec.getQueuedAt());
        assertEquals(JobExecutionStatus.WAITING, retryExec.getStatus());
    }
}
