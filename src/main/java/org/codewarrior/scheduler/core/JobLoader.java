package org.codewarrior.scheduler.core;

import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.repository.SchedulerJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Log4j2
public class JobLoader {

    private final SchedulerJobRepository jobRepo;
    private final DynamicSchedulerService cronService;
    private final JobRunnerService jobRunnerService;

    @EventListener(ApplicationReadyEvent.class)
    public void loadJobs() {

        List<SchedulerJob> jobs = jobRepo.findByJobEnabled(true);

        for (SchedulerJob job : jobs) {
            try {
                log.info("Scheduling job: {} with cron {}", job.getJobName(), job.getCronExpression());
                cronService.scheduleJob(
                        job.getJobPkId(),
                        () -> jobRunnerService.runJob(job.getJobPkId(), "CRON"),
                        job.getCronExpression()
                );
            } catch (Exception ex) {
                log.error("[JOB {}] Failed to schedule job {} with cron {}: {}",
                        job.getJobPkId(),
                        job.getJobName(),
                        job.getCronExpression(),
                        ex.getMessage(),
                        ex);
            }
        }
    }
}
