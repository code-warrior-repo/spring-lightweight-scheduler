package org.codewarrior.scheduler.domain;

import org.codewarrior.scheduler.dto.JobExecutionStatus;
import org.codewarrior.scheduler.util.BooleanToIntegerConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "SCHEDULER_JOB_EXECUTION",
        schema = "SCHEDULER",
        indexes = {
                @Index(name = "IDX_JOBEXEC_JOBID", columnList = "JOB_PK_ID"),
                @Index(name = "IDX_JOBEXEC_STATUS", columnList = "JOB_STATUS"),
                @Index(name = "IDX_JOBEXEC_QUEUEDAT", columnList = "QUEUED_AT"),
                @Index(name = "IDX_JOBEXEC_STARTEDAT", columnList = "STARTED_AT")
        }
)
@SequenceGenerator(
        name = "SCHEDULER_JOB_EXEC_SEQ_GEN",
        sequenceName = "SCHEDULER_JOB_EXECUTION_SEQUENCE",
        schema = "SCHEDULER",
        allocationSize = 1
)
public class SchedulerJobExecution {

    @Id
    @Column(name = "JOB_EXEC_PK_ID", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SCHEDULER_JOB_EXEC_SEQ_GEN")
    private Long jobExecPkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "JOB_PK_ID", nullable = false)
    private SchedulerJob job;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "COMPLETED_AT")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "JOB_STATUS", length = 30)
    private JobExecutionStatus status;

    @Lob
    @Column(name = "ERROR_MSG")
    private String errorMessage;    // full stack trace

    @Column(name = "FAILURE_TYPE", length = 50)
    private String failureType;     // SYSTEM_ERROR, BUSINESS_ERROR, TIMEOUT

    @Column(name = "DURATION_MS")
    private Long durationMs;

    @Column(name = "QUEUED_AT")
    private LocalDateTime queuedAt;

    @Size(max = 20)
    @Column(name = "STARTED_BY")
    private String startedBy;

    @ColumnDefault("0")
    @Column(name = "RETRY_NUMBER")
    private Integer retryNumber;

    @Convert(converter = BooleanToIntegerConverter.class)
    @Column(name = "TIMEOUT_OCCURRED")
    private Boolean timeoutOccurred;

    @Size(max = 255)
    @Column(name = "WAIT_REASON")
    private String waitReason;

    @Convert(converter = BooleanToIntegerConverter.class)
    @Column(name = "LONG_RUNNING_ALERT_SENT")
    private Boolean longRunningAlertSent;

    @Column(name = "TRIGGER_TYPE", length = 30)
    private String triggerType;   // CRON, RETRY, MANUAL, API

    @Column(name = "RUNNING_ON_HOST", length = 100)
    private String runningOnHost;
}
