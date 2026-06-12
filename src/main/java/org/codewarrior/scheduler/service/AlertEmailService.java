package org.codewarrior.scheduler.service;

import org.codewarrior.scheduler.constants.EmailTemplates;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import org.codewarrior.scheduler.repository.SchedulerJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Log4j2
public class AlertEmailService {

    private final JavaMailSender mailSender;
    private final SchedulerJobRepository jobRepo;

    @Value("${scheduler.alerts.longRunningThresholdMinutes:60}")
    private int longRunningThresholdMinutes;

    @Value("${scheduler.alerts.notifyOnRetry:false}")
    private boolean notifyOnRetry;

    @Value("${scheduler.alerts.notifyOnTimeout:false}")
    private boolean notifyOnTimeout;

    @Value("${scheduler.alerts.notifyOnFailure:false}")
    private boolean notifyOnFailure;

    @Value("${scheduler.alerts.notifyOnLongRunning:false}")
    private boolean notifyOnLongRunning;


    /* ============================================================
       FAILURE
       ============================================================ */
    public void sendFailureAlert(SchedulerJob job, SchedulerJobExecution exec) {

        if (!notifyOnFailure) {
            log.debug("[EMAIL][FAILURE][SUPPRESSED] Alerts disabled. Job {} Exec {}",
                    job.getJobName(), exec.getJobExecPkId());
            return;
        }

        if (job.getAlertEmail() == null) {
            log.warn("[EMAIL][FAILURE][NO-RECIPIENT] Job {} has no alertEmail configured", job.getJobName());
            return;
        }
        if (isInCooldown(job, "FAILURE")) {
            return;
        }

        log.info("[EMAIL][FAILURE][PREPARE] Job={} Exec={} To={}",
                job.getJobName(), exec.getJobExecPkId(), job.getAlertEmail());

        String subject = String.format(EmailTemplates.SUBJECT_FAILURE, job.getJobName());
        String body = String.format(
                EmailTemplates.BODY_FAILURE,
                job.getJobName(),
                exec.getJobExecPkId(),
                exec.getStartedBy(),
                exec.getStartedAt(),
                exec.getErrorMessage(),
                job.getRetryCount() - exec.getRetryNumber()
        );

        send(job, job.getAlertEmail(), subject, body, "FAILURE");
    }


    /* ============================================================
       TIMEOUT
       ============================================================ */
    public void sendTimeoutAlert(SchedulerJob job, SchedulerJobExecution exec) {

        if (!notifyOnTimeout) {
            log.debug("[EMAIL][TIMEOUT][SUPPRESSED] Alerts disabled. Job {} Exec {}",
                    job.getJobName(), exec.getJobExecPkId());
            return;
        }

        if (job.getAlertEmail() == null) {
            log.warn("[EMAIL][TIMEOUT][NO-RECIPIENT] Job {} has no alertEmail configured", job.getJobName());
            return;
        }
        if (isInCooldown(job, "TIMEOUT")) {
            return;
        }
        log.info("[EMAIL][TIMEOUT][PREPARE] Job={} Exec={} DurationLimit={}ms",
                job.getJobName(), exec.getJobExecPkId(), job.getMaxAllowedDurationMs());

        String subject = String.format(EmailTemplates.SUBJECT_TIMEOUT, job.getJobName());
        String body = String.format(
                EmailTemplates.BODY_TIMEOUT,
                job.getJobName(),
                exec.getJobExecPkId(),
                exec.getStartedAt(),
                job.getMaxAllowedDurationMs()
        );

        send(job, job.getAlertEmail(), subject, body, "TIMEOUT");
    }


    /* ============================================================
       RETRY SCHEDULED
       ============================================================ */
    public void sendRetryScheduledAlert(SchedulerJob job, SchedulerJobExecution exec) {

        if (!notifyOnRetry) {
            log.debug("[EMAIL][RETRY][SUPPRESSED] Alerts disabled. Job {} Exec {}",
                    job.getJobName(), exec.getJobExecPkId());
            return;
        }

        if (job.getAlertEmail() == null) {
            log.warn("[EMAIL][RETRY][NO-RECIPIENT] Job {} has no alertEmail configured", job.getJobName());
            return;
        }
        if (isInCooldown(job, "RETRY")) {
            return;
        }
        log.info("[EMAIL][RETRY][PREPARE] Job={} Exec={} Retry#={} NextRun={}",
                job.getJobName(), exec.getJobExecPkId(), exec.getRetryNumber(), exec.getQueuedAt());

        String subject = String.format(EmailTemplates.SUBJECT_RETRY, job.getJobName());
        String body = String.format(
                EmailTemplates.BODY_RETRY,
                job.getJobName(),
                exec.getJobExecPkId(),
                exec.getRetryNumber(),
                exec.getQueuedAt()
        );

        send(job, job.getAlertEmail(), subject, body, "RETRY");
    }


    /* ============================================================
       RETRIES EXHAUSTED
       ============================================================ */
    public void sendRetriesExhaustedAlert(SchedulerJob job, SchedulerJobExecution exec) {

        if (!notifyOnFailure) {
            log.debug("[EMAIL][RETRIES-EXHAUSTED][SUPPRESSED] Alerts disabled.");
            return;
        }

        if (job.getAlertEmail() == null) {
            log.warn("[EMAIL][RETRIES-EXHAUSTED][NO-RECIPIENT] Job {} missing alertEmail", job.getJobName());
            return;
        }

        if (isInCooldown(job, "RETRIES-EXHAUSTED")) {
            return;
        }
        log.info("[EMAIL][RETRIES-EXHAUSTED][PREPARE] Job={} Exec={}",
                job.getJobName(), exec.getJobExecPkId());

        String subject = String.format(EmailTemplates.SUBJECT_RETRIES_EXHAUSTED, job.getJobName());
        String body = String.format(
                EmailTemplates.BODY_RETRIES_EXHAUSTED,
                job.getJobName(),
                exec.getJobExecPkId(),
                exec.getStartedAt(),
                exec.getErrorMessage()
        );

        send(job, job.getAlertEmail(), subject, body, "RETRIES-EXHAUSTED");
    }


    /* ============================================================
       LONG-RUNNING
       ============================================================ */
    public void sendLongRunningAlert(SchedulerJob job, SchedulerJobExecution exec) {

        if (!notifyOnLongRunning) {
            log.debug("[EMAIL][LONG-RUNNING][SUPPRESSED] Alerts disabled.");
            return;
        }

        if (job.getAlertEmail() == null) {
            log.warn("[EMAIL][LONG-RUNNING][NO-RECIPIENT] Job {} missing alertEmail", job.getJobName());
            return;
        }
        if (isInCooldown(job, "LONG-RUNNING")) {
            return;
        }
        log.info("[EMAIL][LONG-RUNNING][PREPARE] Job={} Exec={} Threshold={}m",
                job.getJobName(), exec.getJobExecPkId(), longRunningThresholdMinutes);

        String subject = String.format(EmailTemplates.SUBJECT_LONG_RUNNING, job.getJobName());
        String body = String.format(
                EmailTemplates.BODY_LONG_RUNNING,
                job.getJobName(),
                exec.getJobExecPkId(),
                exec.getStartedAt(),
                longRunningThresholdMinutes
        );

        send(job, job.getAlertEmail(), subject, body, "LONG-RUNNING");
    }


    /* ============================================================
       SEND WRAPPER
       ============================================================ */
    private void send(SchedulerJob job, String to, String subject, String body, String type) {

        log.info("[EMAIL][SEND][{}] To={} Subject=\"{}\"",
                type, to, subject);
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);

            mailSender.send(msg);
            markAlertSent(job);
            log.info("[EMAIL][SUCCESS][{}] Sent to {}", type, to);

        } catch (Exception e) {
            log.error("[EMAIL][FAILED][{}] Error sending to {}: {}",
                    type, to, e.getMessage(), e);
        }
    }
    private boolean isInCooldown(SchedulerJob job, String type) {
        Integer cooldownMinutes = job.getAlertCooldownMinutes();

        if (cooldownMinutes == null || cooldownMinutes <= 0 || job.getLastAlertSentAt() == null) {
            return false;
        }

        LocalDateTime nextAllowedAt = job.getLastAlertSentAt().plusMinutes(cooldownMinutes);

        if (nextAllowedAt.isAfter(LocalDateTime.now())) {
            log.info("[EMAIL][{}][COOLDOWN] Suppressed for job {} until {}",
                    type,
                    job.getJobName(),
                    nextAllowedAt);
            return true;
        }

        return false;
    }

    private void markAlertSent(SchedulerJob job) {
        job.setLastAlertSentAt(LocalDateTime.now());
        jobRepo.save(job);
    }

}
