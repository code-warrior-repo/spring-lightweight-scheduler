package org.codewarrior.scheduler.jobs;

import org.codewarrior.scheduler.core.ScheduledJob;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ScheduledJob("LoadUsersSecurityManagerJob")
public class LoadUsersSecurityManagerJob implements Runnable {

    private final SchedulerJob job;
    private final SchedulerJobExecution exec;

    public LoadUsersSecurityManagerJob(SchedulerJob job, SchedulerJobExecution exec) {
        this.job = job;
        this.exec = exec;
    }

    @Override
    public void run() {

        log.info("LoadUsersSecurityManagerJob is running");
        try {
            Thread.sleep(1000);
            log.info("LoadUsersSecurityManagerJob is finished");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
