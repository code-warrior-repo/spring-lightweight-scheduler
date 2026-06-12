package org.codewarrior.scheduler.service;

import org.codewarrior.scheduler.core.JobExecutionTxService;
import org.codewarrior.scheduler.core.JobRunnerService;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import org.codewarrior.scheduler.dto.JobExecutionStatus;
import org.codewarrior.scheduler.repository.SchedulerJobExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobTimeoutMonitorTest {

    private SchedulerJobExecutionRepository execRepo;
    private JobRunnerService jobRunnerService;
    private AlertEmailService alertEmailService;
    private JobExecutionTxService txService;

    private JobTimeoutMonitor monitor;

    @BeforeEach
    void setup() {
        execRepo = mock(SchedulerJobExecutionRepository.class);
        jobRunnerService = mock(JobRunnerService.class);
        alertEmailService = mock(AlertEmailService.class);
        txService = mock(JobExecutionTxService.class);

        monitor = new JobTimeoutMonitor(execRepo, jobRunnerService, alertEmailService, txService);
    }

    private SchedulerJobExecution runningExec(long jobId, long msRunning, Long maxAllowedMs) {

        SchedulerJob job = new SchedulerJob();
        job.setJobPkId(jobId);
        job.setJobName("TestJob");
        job.setMaxAllowedDurationMs(maxAllowedMs);
        job.setAlertEmail("test@x.com");

        SchedulerJobExecution exec = new SchedulerJobExecution();
        exec.setJob(job);
        exec.setStatus(JobExecutionStatus.RUNNING);
        exec.setStartedAt(LocalDateTime.now().minus(Duration.ofMillis(msRunning)));
        exec.setLongRunningAlertSent(false);

        exec.setJobExecPkId(System.currentTimeMillis()); // test-only pk

        return exec;
    }

    @Test
    void testNoTimeoutWhenBelowThreshold() {

        SchedulerJobExecution exec =
                runningExec(1L, 1000, 5000L); // running 1s, max 5s

        when(execRepo.findByStatus(JobExecutionStatus.RUNNING))
                .thenReturn(List.of(exec));

        monitor.checkTimeouts();

        verify(alertEmailService, never()).sendTimeoutAlert(any(), any());
        verify(jobRunnerService, never()).processWaiting(anyLong());
        verify(txService, never()).markTimeout(any());
    }

    @Test
    void testTimeoutDetectedAndHandled() {

        SchedulerJobExecution exec =
                runningExec(2L, 10000, 1000L); // running 10s, allowed 1s

        when(execRepo.findByStatus(JobExecutionStatus.RUNNING))
                .thenReturn(List.of(exec));

        monitor.checkTimeouts();

        verify(txService).markTimeout(exec.getJobExecPkId());
        verify(alertEmailService).sendTimeoutAlert(exec.getJob(), exec);
        verify(jobRunnerService).processWaiting(2L);
    }


    @Test
    void testLongRunningAlertTriggered() {

        SchedulerJobExecution exec =
                runningExec(3L, 61 * 60 * 1000L, 999999L); // running 61 mins

        exec.setLongRunningAlertSent(false);

        when(execRepo.findByStatus(JobExecutionStatus.RUNNING))
                .thenReturn(List.of(exec));

        monitor.checkTimeouts();

        verify(alertEmailService).sendLongRunningAlert(exec.getJob(), exec);
        verify(txService).save(exec); // longRunningAlertSent updated
    }

    @Test
    void testLongRunningAlertNotRepeated() {

        SchedulerJobExecution exec =
                runningExec(4L, 61 * 60 * 1000L, 999999L);

        exec.setLongRunningAlertSent(true); // already sent

        when(execRepo.findByStatus(JobExecutionStatus.RUNNING))
                .thenReturn(List.of(exec));

        monitor.checkTimeouts();

        verify(alertEmailService, never()).sendLongRunningAlert(any(), any());
        verify(txService, never()).save(any());
    }

    @Test
    void testTimeoutDetectedByMonitorTriggersHandling() {

        SchedulerJobExecution exec =
                runningExec(5L, 5000, 1000L); // running 5s, max 1s allowed

        when(execRepo.findByStatus(JobExecutionStatus.RUNNING))
                .thenReturn(List.of(exec));

        monitor.checkTimeouts();

        verify(txService).markTimeout(exec.getJobExecPkId());
        verify(alertEmailService).sendTimeoutAlert(exec.getJob(), exec);
        verify(jobRunnerService).processWaiting(5L);
    }
}
