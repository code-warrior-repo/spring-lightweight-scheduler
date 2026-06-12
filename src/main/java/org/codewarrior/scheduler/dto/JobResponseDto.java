package org.codewarrior.scheduler.dto;

public record JobResponseDto(
        Long jobId,
        String jobName,
        String jobType,
        String jobDescription,
        Boolean jobEnabled,
        String cronExpression,
        String jobParameters,
        String lastRunStatus,
        String lastRunStartedAt,
        String lastRunCompletedAt,
        Long durationMs,
        String lastRunError
) {
}

