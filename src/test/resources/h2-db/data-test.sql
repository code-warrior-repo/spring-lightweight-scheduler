INSERT INTO SCHEDULER.SCHEDULER_JOB (
    JOB_PK_ID,
    JOB_NAME,
    JOB_TYPE,
    JOB_PARAMETERS,
    CRON_EXPRESSION,
    JOB_ENABLED,
    MANUAL_TRIGGER_ALLOWED,
    RETRY_COUNT,
    RETRY_DELAY_SECONDS,
    MAX_WAITING_QUEUE_SIZE,
    MAX_ALLOWED_DURATION_MS,
    ALERT_EMAIL,
    EXECUTION_COUNT,
    ADD_TS,
    UPDATE_TS
)
VALUES (
           SCHEDULER.SCHEDULER_JOB_SEQUENCE.NEXTVAL,
           'Load Users Security Manager Job',
           'LoadUsersSecurityManagerJob',
           '{}',                          -- empty JSON parameters
           '0 */1 * * * *',               -- runs every 5 minutes
           1,                             -- enabled
           1,                             -- manual trigger allowed
           2,                             -- retry 2 times
           30,                            -- retry delay 30 seconds
           10,                            -- max waiting queue
           600,                         -- max duration 60 seconds
           'ops@example.com',             -- email list
           0,                             -- executionCount
           SYSDATE,
           SYSDATE
       );
INSERT INTO SCHEDULER.SCHEDULER_JOB (
    JOB_PK_ID,
    JOB_NAME,
    JOB_TYPE,
    JOB_PARAMETERS,
    CRON_EXPRESSION,
    JOB_ENABLED,
    MANUAL_TRIGGER_ALLOWED,
    RETRY_COUNT,
    RETRY_DELAY_SECONDS,
    MAX_WAITING_QUEUE_SIZE,
    MAX_ALLOWED_DURATION_MS,
    ALERT_EMAIL,
    EXECUTION_COUNT,
    ADD_TS,
    UPDATE_TS,
    VERSION
)
VALUES (
           SCHEDULER.SCHEDULER_JOB_SEQUENCE.NEXTVAL,
           'Load Winners Job',
           'LoadWinnersJob',
           '{}',                          -- no parameters
           '0 0/2 * * * *',              -- every 10 minutes
           1,                             -- enabled
           0,                             -- manual trigger NOT allowed
           0,                             -- no retries
           0,                             -- retry delay
           10,                            -- waiting queue
           1200,                        -- max duration 120 seconds
           NULL,                          -- no alert email
           0,                             -- executionCount
           SYSDATE,
           SYSDATE,
            0
       );
