package org.codewarrior.scheduler.service;

import org.codewarrior.scheduler.constants.JobConstants;
import org.codewarrior.scheduler.core.DynamicSchedulerService;
import org.codewarrior.scheduler.core.JobRunnerService;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.dto.JobRequestDto;
import org.codewarrior.scheduler.dto.JobResponseDto;
import org.codewarrior.scheduler.exception.JobValidationException;
import org.codewarrior.scheduler.repository.SchedulerJobExecutionRepository;
import org.codewarrior.scheduler.repository.SchedulerJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
public class JobService {

    private final SchedulerJobRepository repo;
    private final DynamicSchedulerService scheduler;
    private final JobRunnerService jobRunnerService;
    private final SchedulerJobExecutionRepository execRepo;


    public SchedulerJob getJob(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new JobValidationException(JobConstants.JOB_NOT_FOUND));
    }


    public SchedulerJob addJob(JobRequestDto req) {

        if (repo.existsByJobName(req.jobName())) {
            throw new JobValidationException("Job name already exists");
        }

        SchedulerJob job = new SchedulerJob();
        applyRequest(job, req);

        SchedulerJob saved = repo.save(job);

        if (Boolean.TRUE.equals(saved.getJobEnabled())) {
            scheduler.scheduleJob(
                    saved.getJobPkId(),
                    () -> jobRunnerService.runJob(saved.getJobPkId(), "CRON"),
                    saved.getCronExpression()
            );
        }
        log.info("[JOB - {}] Added and scheduled", saved.getJobPkId());
        return saved;
    }


    public SchedulerJob updateJob(Long id, JobRequestDto req) {

        SchedulerJob job = repo.findById(id)
                .orElseThrow(() -> new JobValidationException(JobConstants.JOB_NOT_FOUND));

        if (!job.getJobName().equals(req.jobName())
                && repo.existsByJobName(req.jobName())) {

            throw new JobValidationException("Job name already exists");
        }

        applyRequest(job, req);

        SchedulerJob saved = repo.save(job);

        scheduler.cancel(id);
        if (Boolean.TRUE.equals(saved.getJobEnabled())) {
            scheduler.scheduleJob(
                    saved.getJobPkId(),
                    () -> jobRunnerService.runJob(saved.getJobPkId(), "CRON"),
                    saved.getCronExpression()
            );
        }
        log.info("[JOB - {}] Updated and rescheduled", id);
        return saved;
    }


    public void deleteJob(Long id) {
        scheduler.cancel(id);
        repo.deleteById(id);
        log.info("[JOB - {}] Deleted", id);
    }


    public void pauseJob(Long id) {
        scheduler.cancel(id);

        SchedulerJob job = repo.findById(id)
                .orElseThrow(() -> new JobValidationException(JobConstants.JOB_NOT_FOUND));

        job.setJobEnabled(false);
        repo.save(job);

        log.info("[JOB - {}] Paused", id);
    }


    public List<JobResponseDto> findRunningJobs() {
        return execRepo.findRunningJobs();
    }


    public List<JobResponseDto> findTopNCompletedRuns() {
        List<Long> jobIds = repo.findAllJobPkIds();
        List<JobResponseDto> result = new ArrayList<>();

        for (Long jobId : jobIds) {
            result.addAll(execRepo.findTopNCompletedRuns(jobId, 3));
        }
        return result;
    }


    public List<JobResponseDto> findAllJobs() {
        return repo.findAll().stream().map(
                job -> new JobResponseDto(
                        job.getJobPkId(),
                        job.getJobName(),
                        job.getJobType(),
                        job.getJobDescription(),
                        job.getJobEnabled(),
                        job.getCronExpression(),
                        job.getJobParameters(),
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ).toList();
    }


    public void enableJob(Long id) {

        SchedulerJob job = repo.findById(id)
                .orElseThrow(() -> new JobValidationException(JobConstants.JOB_NOT_FOUND));

        job.setJobEnabled(true);
        repo.save(job);
        scheduler.scheduleJob(
                id,
                () -> jobRunnerService.runJob(id, "CRON"),
                job.getCronExpression()
        );

        log.info("[JOB - {}] Enabled", id);
    }



    public void runNow(Long id, String userId) {

        SchedulerJob job = repo.findById(id)
                .orElseThrow(() -> new JobValidationException(JobConstants.JOB_NOT_FOUND));

        if (!Boolean.TRUE.equals(job.getManualTriggerAllowed())) {
            throw new JobValidationException("[JOB - " + job.getJobName() + "] manual trigger is not allowed");
        }

        if (!Boolean.TRUE.equals(job.getJobEnabled())) {
            throw new JobValidationException("[JOB - " + job.getJobName() + "] is disabled");
        }

        boolean running = execRepo.existsRunningExecutionWithLock(id);

        if (running) {
            throw new JobValidationException("[JOB - " + job.getJobName() + "] is already running");
        }

        log.info("[JOB - {}] Manual run requested by {}", job.getJobName(), userId);
        scheduler.runNow(id, userId);
    }

    private void applyRequest(SchedulerJob job, JobRequestDto req) {
        job.setJobName(req.jobName());
        job.setJobType(req.jobType());
        job.setJobDescription(req.jobDescription());
        job.setCronExpression(req.cronExpression());
        job.setJobParameters(req.jobParameters());
        job.setJobEnabled(req.jobEnabled());
        job.setManualTriggerAllowed(req.manualTriggerAllowed());
        job.setRetryCount(req.retryCount());
        job.setRetryDelaySeconds(req.retryDelaySeconds());
        job.setMaxWaitingQueueSize(req.maxWaitingQueueSize());
        job.setMaxAllowedDurationMs(req.maxAllowedDurationMs());
        job.setAlertEmail(req.alertEmail());

        if (job.getConcurrencyPolicy() == null) {
            job.setConcurrencyPolicy(JobConstants.JOB_EXECUTION_SKIP);
        }

        if (job.getAlertCooldownMinutes() == null) {
            job.setAlertCooldownMinutes(30);
        }
    }
}