package org.codewarrior.scheduler.integration;

import org.codewarrior.scheduler.core.JobRunnerService;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import org.codewarrior.scheduler.dto.JobExecutionStatus;
import org.codewarrior.scheduler.repository.SchedulerJobExecutionRepository;
import org.codewarrior.scheduler.repository.SchedulerJobRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@ActiveProfiles("integ-test")
@SpringBootTest
class SchedulerIntegrationTest {

    @Autowired
    SchedulerJobRepository jobRepo;
    @Autowired
    SchedulerJobExecutionRepository execRepo;
    @Autowired
    JobRunnerService jobRunnerService;

    @Test
    void insertTestJob() {
        SchedulerJob job = new SchedulerJob();
        job.setJobName("TEST_JOB");
        job.setJobType("TestJob");
        job.setCronExpression("* 0 0 * * *");
        job.setExecutionCount(0);
        job.setJobEnabled(true);
        job.setMaxWaitingQueueSize(5);
        job.setMaxAllowedDurationMs(60000L);
        SchedulerJob saved = jobRepo.save(job);

        assertNotNull(saved.getJobPkId());
        assertEquals("TEST_JOB", saved.getJobName());
    }

    @Disabled
    @Test
    void testFullExecutionFlow() {
        // Create and save a NEW job correctly
        SchedulerJob job = new SchedulerJob();
        job.setJobEnabled(true);
        job.setJobName("FlowTest");
        job.setJobType("TestJob");
        job.setCronExpression("* * * * * *");
        job.setMaxWaitingQueueSize(5);
        job.setExecutionCount(0);
        job.setMaxAllowedDurationMs(60000L);
        job.setRetryCount(0);
        job.setRetryDelaySeconds(60000);
        job = jobRepo.save(job);

        // Now we can safely run the job
        jobRunnerService.runJob(job.getJobPkId(), "CRON");

        List<SchedulerJobExecution> execs =
                execRepo.findByJob_JobPkIdAndStatus(job.getJobPkId(), JobExecutionStatus.SUCCESS);

        assertFalse(execs.isEmpty());
    }
}