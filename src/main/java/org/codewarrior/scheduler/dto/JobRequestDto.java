package org.codewarrior.scheduler.dto;

import org.codewarrior.scheduler.validation.ValidCron;
import org.codewarrior.scheduler.validation.ValidEmailList;
import org.codewarrior.scheduler.validation.ValidJobType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobRequestDto(
        Long jobId,

        @NotBlank(message = "Job name is required")
        String jobName,

        @NotBlank(message = "Job type is required")
        @ValidJobType
        String jobType,

        @NotBlank(message = "Job Status is required")
        String jobStatus,

        String jobDescription,

        @NotBlank(message = "Cron expression is required")
        @ValidCron
        String cronExpression,

        String jobParameters,

        @NotNull(message = "Job Enabled/Disabled is required")
        Boolean jobEnabled,

        @NotNull(message = "Manual trigger allowed is required")
        Boolean manualTriggerAllowed,

        @NotNull(message = "Retry Count is required")
        Integer retryCount,

        @NotNull(message = "Retry Delay is required")
        Integer retryDelaySeconds,

        @NotNull(message = "Max Waiting Queue Size is required")
        Integer maxWaitingQueueSize,

        @NotNull(message = "Max Allowed Duration is required")
        Long maxAllowedDurationMs,

        @ValidEmailList
        String alertEmail
) {
    public JobRequestDto {
        if (alertEmail != null) {
            alertEmail = alertEmail.replaceAll(",+$", "").trim();
        }
    }
}
