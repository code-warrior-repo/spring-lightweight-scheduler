package org.codewarrior.scheduler.service;

import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import org.codewarrior.scheduler.dto.JobExecutionStatus;
import org.codewarrior.scheduler.repository.SchedulerJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertEmailServiceTest {

    private JavaMailSender mailSender;
    private SchedulerJobRepository jobRepo;
    private AlertEmailService service;

    @BeforeEach
    void setup() {
        mailSender = mock(JavaMailSender.class);
        jobRepo = mock(SchedulerJobRepository.class);
        service = new AlertEmailService(mailSender, jobRepo);

        setFlag("notifyOnFailure", true);
        setFlag("notifyOnRetry", true);
        setFlag("notifyOnTimeout", true);
        setFlag("notifyOnLongRunning", true);
    }

    private void setFlag(String fieldName, boolean value) {
        try {
            var field = AlertEmailService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(service, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SchedulerJob createJob() {
        SchedulerJob job = new SchedulerJob();
        job.setJobPkId(1L);
        job.setJobName("Test Job");
        job.setAlertEmail("test@example.com");
        job.setRetryCount(3);
        job.setAlertCooldownMinutes(0);
        return job;
    }

    private SchedulerJobExecution createExec() {
        SchedulerJobExecution exec = new SchedulerJobExecution();
        exec.setJobExecPkId(123L);
        exec.setStatus(JobExecutionStatus.FAILED);
        exec.setStartedBy("USER");
        exec.setErrorMessage("ERR");
        exec.setRetryNumber(1);
        exec.setStartedAt(LocalDateTime.now().minusMinutes(1));
        exec.setQueuedAt(LocalDateTime.now().plusMinutes(1));
        return exec;
    }

    @Test
    void testSendFailureAlert() {
        SchedulerJob job = createJob();
        SchedulerJobExecution exec = createExec();

        service.sendFailureAlert(job, exec);

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());
        verify(jobRepo).save(job);

        SimpleMailMessage msg = captor.getValue();

        assertTrue(msg.getSubject().contains("FAILED"));
        assertEquals("test@example.com", msg.getTo()[0]);
        assertNotNull(job.getLastAlertSentAt());
    }

    @Test
    void testSendTimeoutAlert() {
        SchedulerJob job = createJob();
        SchedulerJobExecution exec = createExec();

        job.setMaxAllowedDurationMs(5000L);

        service.sendTimeoutAlert(job, exec);

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());
        verify(jobRepo).save(job);

        SimpleMailMessage msg = captor.getValue();
        assertTrue(msg.getSubject().contains("TIMEOUT"));
    }

    @Test
    void testSendRetryScheduledAlert() {
        SchedulerJob job = createJob();
        SchedulerJobExecution exec = createExec();
        exec.setRetryNumber(2);

        service.sendRetryScheduledAlert(job, exec);

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());
        verify(jobRepo).save(job);

        SimpleMailMessage msg = captor.getValue();
        assertTrue(msg.getSubject().contains("Retry"));
    }

    @Test
    void testSendRetriesExhaustedAlert() {
        SchedulerJob job = createJob();
        SchedulerJobExecution exec = createExec();

        service.sendRetriesExhaustedAlert(job, exec);

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());
        verify(jobRepo).save(job);

        SimpleMailMessage msg = captor.getValue();
        assertTrue(msg.getSubject().contains("Retries Exhausted"));
    }

    @Test
    void testSendLongRunningAlert() {
        SchedulerJob job = createJob();
        SchedulerJobExecution exec = createExec();

        service.sendLongRunningAlert(job, exec);

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());
        verify(jobRepo).save(job);

        SimpleMailMessage msg = captor.getValue();
        assertTrue(msg.getSubject().contains("Running Too Long"));
    }

    @Test
    void testSkipsWhenEmailDisabled() {
        SchedulerJob job = createJob();
        SchedulerJobExecution exec = createExec();

        setFlag("notifyOnFailure", false);

        service.sendFailureAlert(job, exec);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(jobRepo, never()).save(any());
    }

    @Test
    void testSkipsWhenEmailMissing() {
        SchedulerJob job = createJob();
        job.setAlertEmail(null);

        SchedulerJobExecution exec = createExec();

        service.sendFailureAlert(job, exec);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(jobRepo, never()).save(any());
    }

    @Test
    void testSkipsWhenInCooldown() {
        SchedulerJob job = createJob();
        job.setAlertCooldownMinutes(30);
        job.setLastAlertSentAt(LocalDateTime.now().minusMinutes(5));

        SchedulerJobExecution exec = createExec();

        service.sendFailureAlert(job, exec);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(jobRepo, never()).save(any());
    }

    @Test
    void testSendsWhenCooldownExpired() {
        SchedulerJob job = createJob();
        job.setAlertCooldownMinutes(30);
        job.setLastAlertSentAt(LocalDateTime.now().minusMinutes(31));

        SchedulerJobExecution exec = createExec();

        service.sendFailureAlert(job, exec);

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(jobRepo).save(job);
    }

    @Test
    void testSendsWhenCooldownIsNull() {
        SchedulerJob job = createJob();
        job.setAlertCooldownMinutes(null);
        job.setLastAlertSentAt(LocalDateTime.now());

        SchedulerJobExecution exec = createExec();

        service.sendFailureAlert(job, exec);

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(jobRepo).save(job);
    }

    @Test
    void testSendHandlesMailExceptionAndDoesNotMarkAlertSent() {
        SchedulerJob job = createJob();
        SchedulerJobExecution exec = createExec();

        doThrow(new RuntimeException("SMTP DOWN"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> service.sendFailureAlert(job, exec));

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(jobRepo, never()).save(any());
    }
}