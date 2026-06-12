package org.codewarrior.scheduler.constants;

public class JobConstants {
    public static final String JOB_NOT_FOUND = "Job Not Found";
    public static final String KEY_SESSION_USER_ID = "sessionUserId";
    public static final String JOB_EXECUTION_STATUS_SUCCESS = "SUCCESS";
    public static final String JOB_EXECUTION_STATUS_FAILED = "FAILED";
    public static final String JOB_EXECUTION_STATUS_TIMEOUT = "TIMEOUT";
    public static final String JOB_EXECUTION_STATUS_SYSTEM_ERROR = "SYSTEM_ERROR";
    public static final String JOB_EXECUTION_CRON = "CRON";
    public static final String JOB_EXECUTION_RETRY = "RETRY";
    public static final String JOB_EXECUTION_MANUAL = "MANUAL";
    public static final String JOB_EXECUTION_API = "API";
    public static final String JOB_EXECUTION_SKIP = "SKIP";
    public static final String JOB_EXECUTION_QUEUE = "QUEUE";
    public static final String JOB_EXECUTION_PARALLEL = "PARALLEL";
    private JobConstants() {
        throw new IllegalStateException("JobConstants class");
    }

}
