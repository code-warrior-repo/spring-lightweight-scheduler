package org.codewarrior.scheduler.domain;

import org.codewarrior.scheduler.util.BooleanToIntegerConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

import static jakarta.persistence.GenerationType.SEQUENCE;

@Getter
@Setter
@Entity
@Table(
        name = "SCHEDULER_JOB",
        schema = "SCHEDULER",
        uniqueConstraints = @UniqueConstraint(name = "UK_SCHEDULER_JOB_NAME", columnNames = "JOB_NAME")
)
@SequenceGenerator(
        name = "SCHEDULER_JOB_id_gen",
        sequenceName = "SCHEDULER_JOB_SEQUENCE",
        schema = "SCHEDULER",
        allocationSize = 1
)
public class SchedulerJob {

    @Id
    @GeneratedValue(strategy = SEQUENCE, generator = "SCHEDULER_JOB_id_gen")
    @Column(name = "JOB_PK_ID")
    private Long jobPkId;

    @Column(name = "JOB_NAME", nullable = false, length = 200)
    private String jobName;

    @Column(name = "JOB_TYPE", nullable = false, length = 200)
    private String jobType;

    @Lob
    @Column(name = "JOB_PARAMETERS")
    private String jobParameters;

    @Column(name = "JOB_DESCRIPTION", length = 500)
    private String jobDescription;

    @Column(name = "CRON_EXPRESSION")
    private String cronExpression;

    @Column(name = "LAST_RUN_STARTED_AT")
    private LocalDateTime lastRunStartedAt;

    @Column(name = "LAST_RUN_COMPLETED_AT")
    private LocalDateTime lastRunCompletedAt;

    @Column(name = "LAST_RUN_STATUS")
    private String lastRunStatus;

    @Lob
    @Column(name = "LAST_RUN_ERROR")
    private String lastRunError;

    @Column(name = "LAST_FAILURE_AT")
    private LocalDateTime lastFailureAt;

    @Column(name = "NEXT_RUN")
    private LocalDateTime nextRun;

    @ColumnDefault("0")
    @Column(name = "EXECUTION_COUNT", nullable = false)
    private Integer executionCount = 0;

    @Convert(converter = BooleanToIntegerConverter.class)
    @Column(name = "JOB_ENABLED", columnDefinition = "NUMBER(1) DEFAULT 0", nullable = false)
    private Boolean jobEnabled = false;

    @Column(name = "VERSION")
    @Version
    private Integer version;

    @CreatedBy
    @Size(max = 20)
    @Column(name = "ADDED_BY")
    private String addedBy;

    @CreatedDate
    @Column(name = "ADD_TS")
    private LocalDateTime addTs;

    @LastModifiedBy
    @Size(max = 20)
    @Column(name = "UPDATED_BY")
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "UPDATE_TS")
    private LocalDateTime updateTs;

    @ColumnDefault("10")
    @Column(name = "MAX_WAITING_QUEUE_SIZE")
    private Integer maxWaitingQueueSize = 10;

    @Column(name = "MAX_ALLOWED_DURATION_MS")
    private Long maxAllowedDurationMs;

    @ColumnDefault("0")
    @Column(name = "RETRY_COUNT", nullable = false)
    private Integer retryCount = 0;

    @ColumnDefault("0")
    @Column(name = "RETRY_DELAY_SECONDS", nullable = false)
    private Integer retryDelaySeconds = 0;

    @Column(name = "CIRCUIT_OPEN_UNTIL")
    private LocalDateTime circuitOpenUntil;

    @Column(name = "CONCURRENCY_POLICY")
    private String concurrencyPolicy; // PARALLEL, QUEUE, SKIP

    @Column(name = "ALERT_EMAIL")
    private String alertEmail;

    @Column(name = "LAST_ALERT_SENT_AT")
    private LocalDateTime lastAlertSentAt;

    @Column(name = "ALERT_COOLDOWN_MINUTES")
    private Integer alertCooldownMinutes;

    @Column(name = "ALERT_FAILURE_COUNT")
    private Integer alertFailureCount = 0;

    @Convert(converter = BooleanToIntegerConverter.class)
    @Column(name = "MANUAL_TRIGGER_ALLOWED", columnDefinition = "NUMBER(1) DEFAULT 0", nullable = false)
    private Boolean manualTriggerAllowed = false;
}