package org.codewarrior.scheduler.service;

import org.codewarrior.scheduler.core.DynamicSchedulerService;
import org.codewarrior.scheduler.core.JobRunnerService;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.dto.JobRequestDto;
import org.codewarrior.scheduler.dto.JobResponseDto;
import org.codewarrior.scheduler.exception.JobValidationException;
import org.codewarrior.scheduler.repository.SchedulerJobExecutionRepository;
import org.codewarrior.scheduler.repository.SchedulerJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JobServiceTest {

    private SchedulerJobRepository repo;
    private DynamicSchedulerService scheduler;
    private JobRunnerService jobRunner;
    private SchedulerJobExecutionRepository execRepo;
    private JobService service;

    private static SchedulerJob job(Long id, String name, boolean enabled) {
        SchedulerJob job = new SchedulerJob();
        job.setJobPkId(id);
        job.setJobName(name);
        job.setJobType("TYPE");
        job.setJobDescription("Description");
        job.setCronExpression("0/5 * * * * *");
        job.setJobParameters("{}");
        job.setJobEnabled(enabled);
        job.setExecutionCount(0);
        return job;
    }

    @BeforeEach
    void setup() {
        repo = mock(SchedulerJobRepository.class);
        scheduler = mock(DynamicSchedulerService.class);
        jobRunner = mock(JobRunnerService.class);
        execRepo = mock(SchedulerJobExecutionRepository.class);

        service = new JobService(repo, scheduler, jobRunner, execRepo);
    }

    private JobRequestDto enabledRequest() {
        return new JobRequestDto(
                null,
                "Job1",
                "TYPE1",
                null,
                "Description 1",
                "0/5 * * * * *",
                "{}",
                true,
                true,
                3,
                15,
                10,
                60000L,
                "ops@example.com"
        );
    }

    private JobRequestDto disabledRequest() {
        return new JobRequestDto(
                null,
                "Job1",
                "TYPE1",
                null,
                "Description 1",
                "0/5 * * * * *",
                "{}",
                false,
                false,
                0,
                0,
                0,
                0L,
                ""
        );
    }

    @Test
    void getJob_shouldReturnJob_whenFound() {
        SchedulerJob job = job(1L, "Job1", true);

        when(repo.findById(1L)).thenReturn(Optional.of(job));

        SchedulerJob result = service.getJob(1L);

        assertSame(job, result);
        verify(repo).findById(1L);
    }

    @Test
    void getJob_shouldThrowJobValidationException_whenNotFound() {
        when(repo.findById(1L)).thenReturn(Optional.empty());

        assertThrows(JobValidationException.class, () -> service.getJob(1L));

        verify(repo).findById(1L);
    }

    @Test
    void addJob_shouldSaveAndSchedule_whenJobIsEnabled() {
        when(repo.existsByJobName("Job1")).thenReturn(false);

        SchedulerJob saved = job(10L, "Job1", true);
        saved.setJobType("TYPE1");
        saved.setCronExpression("0/5 * * * * *");
        saved.setJobParameters("{}");

        when(repo.save(any(SchedulerJob.class))).thenReturn(saved);

        SchedulerJob result = service.addJob(enabledRequest());

        assertSame(saved, result);
        assertEquals(10L, result.getJobPkId());

        ArgumentCaptor<SchedulerJob> jobCaptor = ArgumentCaptor.forClass(SchedulerJob.class);
        verify(repo).save(jobCaptor.capture());

        SchedulerJob jobToSave = jobCaptor.getValue();

        assertEquals("Job1", jobToSave.getJobName());
        assertEquals("TYPE1", jobToSave.getJobType());
        assertEquals("0/5 * * * * *", jobToSave.getCronExpression());
        assertEquals("{}", jobToSave.getJobParameters());
        assertTrue(jobToSave.getJobEnabled());

        verify(scheduler).scheduleJob(
                eq(10L),
                any(Runnable.class),
                eq("0/5 * * * * *")
        );
    }

    @Test
    void addJob_shouldSaveButNotSchedule_whenJobIsDisabled() {
        when(repo.existsByJobName("Job1")).thenReturn(false);

        SchedulerJob saved = job(11L, "Job1", false);
        saved.setCronExpression("0/5 * * * * *");

        when(repo.save(any(SchedulerJob.class))).thenReturn(saved);

        SchedulerJob result = service.addJob(disabledRequest());

        assertSame(saved, result);
        assertFalse(result.getJobEnabled());

        verify(repo).save(any(SchedulerJob.class));
        verify(scheduler, never()).scheduleJob(anyLong(), any(Runnable.class), anyString());
    }

    @Test
    void addJob_shouldThrowJobValidationException_whenJobNameAlreadyExists() {
        when(repo.existsByJobName("Job1")).thenReturn(true);

        assertThrows(JobValidationException.class, () -> service.addJob(enabledRequest()));

        verify(repo, never()).save(any());
        verifyNoInteractions(scheduler);
    }

    @Test
    void updateJob_shouldUpdateCancelAndSchedule_whenJobIsEnabledAndNameChanged() {
        SchedulerJob existing = job(9L, "OldName", false);

        SchedulerJob saved = job(9L, "Job1", true);
        saved.setJobType("TYPE1");
        saved.setCronExpression("0/5 * * * * *");
        saved.setJobParameters("{}");

        when(repo.findById(9L)).thenReturn(Optional.of(existing));
        when(repo.existsByJobName("Job1")).thenReturn(false);
        when(repo.save(existing)).thenReturn(saved);

        SchedulerJob result = service.updateJob(9L, enabledRequest());

        assertSame(saved, result);

        assertEquals("Job1", existing.getJobName());
        assertEquals("TYPE1", existing.getJobType());
        assertEquals("0/5 * * * * *", existing.getCronExpression());
        assertEquals("{}", existing.getJobParameters());
        assertTrue(existing.getJobEnabled());

        verify(repo).existsByJobName("Job1");
        verify(repo).save(existing);
        verify(scheduler).cancel(9L);
        verify(scheduler).scheduleJob(
                eq(9L),
                any(Runnable.class),
                eq("0/5 * * * * *")
        );
    }

    @Test
    void updateJob_shouldNotCheckDuplicateName_whenNameIsUnchanged() {
        SchedulerJob existing = job(9L, "Job1", true);

        SchedulerJob saved = job(9L, "Job1", true);
        saved.setCronExpression("0/5 * * * * *");

        when(repo.findById(9L)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(saved);

        SchedulerJob result = service.updateJob(9L, enabledRequest());

        assertSame(saved, result);

        verify(repo, never()).existsByJobName(anyString());
        verify(scheduler).cancel(9L);
        verify(scheduler).scheduleJob(
                eq(9L),
                any(Runnable.class),
                eq("0/5 * * * * *")
        );
    }

    @Test
    void updateJob_shouldCancelButNotSchedule_whenUpdatedJobIsDisabled() {
        SchedulerJob existing = job(9L, "Job1", true);

        SchedulerJob saved = job(9L, "Job1", false);
        saved.setCronExpression("0/5 * * * * *");

        when(repo.findById(9L)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(saved);

        SchedulerJob result = service.updateJob(9L, disabledRequest());

        assertSame(saved, result);
        assertFalse(existing.getJobEnabled());

        verify(repo).save(existing);
        verify(scheduler).cancel(9L);
        verify(scheduler, never()).scheduleJob(anyLong(), any(Runnable.class), anyString());
    }

    @Test
    void updateJob_shouldThrowJobValidationException_whenJobNotFound() {
        when(repo.findById(1L)).thenReturn(Optional.empty());

        assertThrows(JobValidationException.class, () -> service.updateJob(1L, enabledRequest()));

        verify(repo, never()).save(any());
        verifyNoInteractions(scheduler);
    }

    @Test
    void updateJob_shouldThrowJobValidationException_whenNewJobNameAlreadyExists() {
        SchedulerJob existing = job(12L, "OldName", true);

        when(repo.findById(12L)).thenReturn(Optional.of(existing));
        when(repo.existsByJobName("Job1")).thenReturn(true);

        assertThrows(JobValidationException.class, () -> service.updateJob(12L, enabledRequest()));

        verify(repo, never()).save(any());
        verifyNoInteractions(scheduler);
    }

    @Test
    void deleteJob_shouldCancelAndDeleteJob() {
        service.deleteJob(100L);

        verify(scheduler).cancel(100L);
        verify(repo).deleteById(100L);
    }

    @Test
    void pauseJob_shouldCancelDisableAndSaveJob_whenFound() {
        SchedulerJob job = job(5L, "Job5", true);

        when(repo.findById(5L)).thenReturn(Optional.of(job));

        service.pauseJob(5L);

        assertFalse(job.getJobEnabled());

        verify(scheduler).cancel(5L);
        verify(repo).save(job);
    }

    @Test
    void pauseJob_shouldCancelThenThrowJobValidationException_whenJobNotFound() {
        when(repo.findById(5L)).thenReturn(Optional.empty());

        assertThrows(JobValidationException.class, () -> service.pauseJob(5L));

        verify(scheduler).cancel(5L);
        verify(repo, never()).save(any());
    }

    @Test
    void findRunningJobs_shouldReturnRepositoryResult() {
        List<JobResponseDto> runningJobs =
                List.of(new JobResponseDto(
                        1L,
                        "Job1",
                        "TYPE",
                        null,
                        true,
                        "CRON",
                        "{}",
                        "RUNNING",
                        null,
                        null,
                        null,
                        null
                ));

        when(execRepo.findRunningJobs()).thenReturn(runningJobs);

        List<JobResponseDto> result = service.findRunningJobs();

        assertSame(runningJobs, result);
        assertEquals(1, result.size());

        verify(execRepo).findRunningJobs();
    }

    @Test
    void findTopNCompletedRuns_shouldReturnTopThreeCompletedRunsForEveryJob() {
        JobResponseDto first =
                new JobResponseDto(1L, "Job1", "TYPE", null, true, "CRON", "{}", "SUCCESS", null, null, 10L, null);
        JobResponseDto second =
                new JobResponseDto(2L, "Job2", "TYPE", null, true, "CRON", "{}", "FAILED", null, null, 20L, "ERR");
        JobResponseDto third =
                new JobResponseDto(2L, "Job2", "TYPE", null, true, "CRON", "{}", "SUCCESS", null, null, 30L, null);

        when(repo.findAllJobPkIds()).thenReturn(List.of(1L, 2L));
        when(execRepo.findTopNCompletedRuns(1L, 3)).thenReturn(List.of(first));
        when(execRepo.findTopNCompletedRuns(2L, 3)).thenReturn(List.of(second, third));

        List<JobResponseDto> result = service.findTopNCompletedRuns();

        assertEquals(3, result.size());
        assertEquals(List.of(first, second, third), result);

        verify(repo).findAllJobPkIds();
        verify(execRepo).findTopNCompletedRuns(1L, 3);
        verify(execRepo).findTopNCompletedRuns(2L, 3);
    }

    @Test
    void findTopNCompletedRuns_shouldReturnEmptyList_whenThereAreNoJobs() {
        when(repo.findAllJobPkIds()).thenReturn(List.of());

        List<JobResponseDto> result = service.findTopNCompletedRuns();

        assertTrue(result.isEmpty());

        verify(repo).findAllJobPkIds();
        verify(execRepo, never()).findTopNCompletedRuns(anyLong(), anyInt());
    }

    @Test
    void findAllJobs_shouldMapJobsToResponseDtos() {
        SchedulerJob first = job(11L, "J1", true);
        first.setJobType("TYPE1");
        first.setJobDescription("Description 1");
        first.setCronExpression("CRON1");
        first.setJobParameters("{}");

        SchedulerJob second = job(12L, "J2", false);
        second.setJobType("TYPE2");
        second.setJobDescription("Description 2");
        second.setCronExpression("CRON2");
        second.setJobParameters("{\"a\":1}");

        when(repo.findAll()).thenReturn(List.of(first, second));

        List<JobResponseDto> response = service.findAllJobs();

        assertEquals(2, response.size());

        assertEquals(11L, response.get(0).jobId());
        assertEquals("J1", response.get(0).jobName());
        assertEquals("TYPE1", response.get(0).jobType());
        assertEquals("Description 1", response.get(0).jobDescription());
        assertTrue(response.get(0).jobEnabled());
        assertEquals("CRON1", response.get(0).cronExpression());
        assertEquals("{}", response.get(0).jobParameters());

        assertEquals(12L, response.get(1).jobId());
        assertEquals("J2", response.get(1).jobName());
        assertEquals("TYPE2", response.get(1).jobType());
        assertEquals("Description 2", response.get(1).jobDescription());
        assertFalse(response.get(1).jobEnabled());
        assertEquals("CRON2", response.get(1).cronExpression());
        assertEquals("{\"a\":1}", response.get(1).jobParameters());

        verify(repo).findAll();
    }

    @Test
    void enableJob_shouldEnableSaveAndScheduleJob_whenFound() {
        SchedulerJob job = job(9L, "Job9", false);
        job.setCronExpression("CRON");

        when(repo.findById(9L)).thenReturn(Optional.of(job));

        service.enableJob(9L);

        assertTrue(job.getJobEnabled());

        verify(repo).save(job);
        verify(scheduler).scheduleJob(
                eq(9L),
                any(Runnable.class),
                eq("CRON")
        );
    }

    @Test
    void enableJob_shouldThrowJobValidationException_whenJobNotFound() {
        when(repo.findById(7L)).thenReturn(Optional.empty());

        assertThrows(JobValidationException.class, () -> service.enableJob(7L));

        verify(repo, never()).save(any());
        verify(scheduler, never()).scheduleJob(anyLong(), any(Runnable.class), anyString());
    }

    @Test
    void runNow_shouldRunSchedulerNow_whenJobExistsAndIsNotRunning() {
        SchedulerJob job = job(33L, "ManualJob", true);
        job.setManualTriggerAllowed(true);

        when(repo.findById(33L)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(33L)).thenReturn(false);

        service.runNow(33L, "USER1");

        verify(repo).findById(33L);
        verify(execRepo).existsRunningExecutionWithLock(33L);
        verify(scheduler).runNow(33L, "USER1");
    }

    @Test
    void runNow_shouldThrowJobValidationException_whenJobIsAlreadyRunning() {
        SchedulerJob job = job(77L, "RunningJob", true);
        job.setManualTriggerAllowed(true);
        when(repo.findById(77L)).thenReturn(Optional.of(job));
        when(execRepo.existsRunningExecutionWithLock(77L)).thenReturn(true);

        JobValidationException exception = assertThrows(
                JobValidationException.class,
                () -> service.runNow(77L, "USER1")
        );

        assertTrue(exception.getMessage().contains("RunningJob"));
        assertTrue(exception.getMessage().contains("already running"));

        verify(repo).findById(77L);
        verify(execRepo).existsRunningExecutionWithLock(77L);
        verify(scheduler, never()).runNow(anyLong(), anyString());
    }

    @Test
    void runNow_shouldRunSchedulerNow_whenJobExistsEnabledAndManualTriggerAllowed() {
        SchedulerJob job = job(33L, "ManualJob", true);
        job.setManualTriggerAllowed(true);

        when(repo.findById(33L)).thenReturn(Optional.of(job));

        service.runNow(33L, "USER1");

        verify(repo).findById(33L);
        verify(scheduler).runNow(33L, "USER1");
        //verifyNoInteractions(execRepo);
    }

    @Test
    void runNow_shouldThrowJobValidationException_whenJobNotFound() {
        when(repo.findById(66L)).thenReturn(Optional.empty());

        assertThrows(JobValidationException.class, () -> service.runNow(66L, "USER1"));

        verify(repo).findById(66L);
        verify(scheduler, never()).runNow(anyLong(), anyString());
        verifyNoInteractions(execRepo);
    }

    @Test
    void runNow_shouldThrowJobValidationException_whenManualTriggerNotAllowed() {
        SchedulerJob job = job(77L, "ManualBlockedJob", true);
        job.setManualTriggerAllowed(false);

        when(repo.findById(77L)).thenReturn(Optional.of(job));

        JobValidationException exception = assertThrows(
                JobValidationException.class,
                () -> service.runNow(77L, "USER1")
        );

        assertTrue(exception.getMessage().contains("manual trigger is not allowed"));

        verify(repo).findById(77L);
        verify(scheduler, never()).runNow(anyLong(), anyString());
        verifyNoInteractions(execRepo);
    }

    @Test
    void runNow_shouldThrowJobValidationException_whenJobIsDisabled() {
        SchedulerJob job = job(88L, "DisabledManualJob", false);
        job.setManualTriggerAllowed(true);

        when(repo.findById(88L)).thenReturn(Optional.of(job));

        JobValidationException exception = assertThrows(
                JobValidationException.class,
                () -> service.runNow(88L, "USER1")
        );

        assertTrue(exception.getMessage().contains("disabled"));

        verify(repo).findById(88L);
        verify(scheduler, never()).runNow(anyLong(), anyString());
        verifyNoInteractions(execRepo);
    }

    @Test
    void addJob_shouldMapAllRequestFields() {
        when(repo.existsByJobName("Job1")).thenReturn(false);

        SchedulerJob saved = job(10L, "Job1", true);
        saved.setCronExpression("0/5 * * * * *");

        when(repo.save(any(SchedulerJob.class))).thenReturn(saved);

        service.addJob(enabledRequest());

        ArgumentCaptor<SchedulerJob> jobCaptor = ArgumentCaptor.forClass(SchedulerJob.class);
        verify(repo).save(jobCaptor.capture());

        SchedulerJob jobToSave = jobCaptor.getValue();

        assertEquals("Job1", jobToSave.getJobName());
        assertEquals("TYPE1", jobToSave.getJobType());
        assertEquals("Description 1", jobToSave.getJobDescription());
        assertEquals("0/5 * * * * *", jobToSave.getCronExpression());
        assertEquals("{}", jobToSave.getJobParameters());
        assertTrue(jobToSave.getJobEnabled());
        assertTrue(jobToSave.getManualTriggerAllowed());
        assertEquals(3, jobToSave.getRetryCount());
        assertEquals(15, jobToSave.getRetryDelaySeconds());
        assertEquals(10, jobToSave.getMaxWaitingQueueSize());
        assertEquals(60000L, jobToSave.getMaxAllowedDurationMs());
        assertEquals("ops@example.com", jobToSave.getAlertEmail());
        assertNotNull(jobToSave.getConcurrencyPolicy());
        assertNotNull(jobToSave.getAlertCooldownMinutes());
    }

    @Test
    void updateJob_shouldMapAllRequestFields() {
        SchedulerJob existing = job(9L, "Job1", false);

        SchedulerJob saved = job(9L, "Job1", true);
        saved.setCronExpression("0/5 * * * * *");

        when(repo.findById(9L)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(saved);

        service.updateJob(9L, enabledRequest());

        assertEquals("Job1", existing.getJobName());
        assertEquals("TYPE1", existing.getJobType());
        assertEquals("Description 1", existing.getJobDescription());
        assertEquals("0/5 * * * * *", existing.getCronExpression());
        assertEquals("{}", existing.getJobParameters());
        assertTrue(existing.getJobEnabled());
        assertTrue(existing.getManualTriggerAllowed());
        assertEquals(3, existing.getRetryCount());
        assertEquals(15, existing.getRetryDelaySeconds());
        assertEquals(10, existing.getMaxWaitingQueueSize());
        assertEquals(60000L, existing.getMaxAllowedDurationMs());
        assertEquals("ops@example.com", existing.getAlertEmail());
    }
}