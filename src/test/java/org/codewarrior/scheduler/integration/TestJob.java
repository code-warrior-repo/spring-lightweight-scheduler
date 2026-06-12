package org.codewarrior.scheduler.integration;

import org.codewarrior.scheduler.core.ScheduledJob;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ScheduledJob("TestJob")
public class TestJob implements Runnable {


    private final SchedulerJob job;
    private final SchedulerJobExecution exec;

    public TestJob(SchedulerJob job, SchedulerJobExecution exec) {
        this.job = job;
        this.exec = exec;
    }

    @Override
    public void run() {

        log.info("Running TestJob for jobId={}, params={}", job.getJobPkId(), job.getJobParameters());
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        log.info("TestJob finished execId={}", exec.getJobExecPkId());
    }
}
