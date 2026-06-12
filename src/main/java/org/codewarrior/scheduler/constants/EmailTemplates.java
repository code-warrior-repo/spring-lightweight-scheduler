package org.codewarrior.scheduler.constants;

public final class EmailTemplates {

    // ============================
    // FAILURE ALERT
    // ============================
    public static final String SUBJECT_FAILURE =
            "[Scheduler] Job FAILED: %s";
    public static final String BODY_FAILURE = """
            Job Name: %s
            Execution ID: %s
            Started By: %s
            Started At: %s
            Error: %s
            Retries Remaining: %s
            """;
    // ============================
    // TIMEOUT ALERT
    // ============================
    public static final String SUBJECT_TIMEOUT =
            "[Scheduler] Job TIMEOUT: %s";
    public static final String BODY_TIMEOUT = """
            Job Name: %s
            Execution ID: %s
            Started At: %s
            Exceeded Max Duration (ms): %s
            """;
    // ============================
    // RETRY SCHEDULED
    // ============================
    public static final String SUBJECT_RETRY =
            "[Scheduler] Retry Scheduled: %s";
    public static final String BODY_RETRY = """
            Job Name: %s
            Execution ID: %s
            Retry Number: %s
            Next Attempt At: %s
            """;
    // ============================
    // RETRIES EXHAUSTED
    // ============================
    public static final String SUBJECT_RETRIES_EXHAUSTED =
            "[Scheduler] Job FAILED (Retries Exhausted): %s";
    public static final String BODY_RETRIES_EXHAUSTED = """
            Job Name: %s
            Final Execution ID: %s
            Started At: %s
            Error: %s
            """;
    // ============================
    // LONG RUNNING JOB ALERT
    // ============================
    public static final String SUBJECT_LONG_RUNNING =
            "[Scheduler] Job Running Too Long: %s";
    public static final String BODY_LONG_RUNNING = """
            Job Name: %s
            Execution ID: %s
            Started At: %s
            Has been running for more than threshold: %s minutes.
            """;

    private EmailTemplates() {
    }
}