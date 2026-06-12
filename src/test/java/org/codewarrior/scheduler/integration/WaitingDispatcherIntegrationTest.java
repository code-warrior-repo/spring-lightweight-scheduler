package org.codewarrior.scheduler.integration;

import org.codewarrior.scheduler.core.JobRegistry;
import org.codewarrior.scheduler.core.JobRunnerService;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import org.codewarrior.scheduler.dto.JobExecutionStatus;
import org.codewarrior.scheduler.repository.SchedulerJobExecutionRepository;
import org.codewarrior.scheduler.repository.SchedulerJobRepository;
import org.codewarrior.scheduler.service.WaitingExecutionDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("integ-test")
@SpringBootTest
class WaitingDispatcherIntegrationTest {

    @Autowired
    SchedulerJobRepository jobRepo;

    @Autowired
    SchedulerJobExecutionRepository execRepo;

    @Autowired
    JobRunnerService jobRunnerService;

    @Autowired
    JobRegistry jobRegistry;

    @Autowired
    WaitingExecutionDispatcher dispatcher;

    @Test
    void dispatcher_shouldProcessDueRetryExecution() {
        AtomicInteger attempts = new AtomicInteger();

        SchedulerJob job = new SchedulerJob();
        job.setJobName("DispatcherRetryJob");
        job.setJobType("DispatcherRetryJobType");
        job.setCronExpression("0/5 * * * * *");
        job.setJobParameters("{}");
        job.setJobEnabled(true);
        job.setExecutionCount(0);
        job.setRetryCount(1);
        job.setRetryDelaySeconds(0);
        job.setManualTriggerAllowed(true);
        job.setMaxAllowedDurationMs(5000L);
        job.setMaxWaitingQueueSize(5);
        job.setConcurrencyPolicy("SKIP");

        SchedulerJob savedJob = jobRepo.save(job);

        jobRegistry.register("DispatcherRetryJobType", (j, exec) -> () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new RuntimeException("first attempt fails");
            }
        });

        jobRunnerService.runJob(savedJob.getJobPkId(), "CRON", false);

        List<SchedulerJobExecution> waiting =
                execRepo.findByJob_JobPkIdAndStatus(savedJob.getJobPkId(), JobExecutionStatus.WAITING);

        assertEquals(1, waiting.size());

        dispatcher.dispatchDueWaitingExecutions();

        List<SchedulerJobExecution> success =
                execRepo.findByJob_JobPkIdAndStatus(savedJob.getJobPkId(), JobExecutionStatus.SUCCESS);

        assertEquals(0, success.size());
        assertEquals(1, attempts.get());
    }
}