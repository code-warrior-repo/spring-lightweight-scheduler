package org.codewarrior.scheduler.dto;

public enum JobExecutionStatus {
    WAITING,      // queued, waiting for previous run to finish
    RUNNING,      // actively executing
    SUCCESS,      // completed without errors
    FAILED,       // execution threw an exception
    TIMEOUT,      // exceeded max allowed duration
    SKIPPED,      // queue full or job disabled at runtime
    CANCELLED     // manually cancelled before start
}
