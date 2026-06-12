CREATE SCHEMA IF NOT EXISTS SCHEDULER;
CREATE SEQUENCE IF NOT EXISTS SCHEDULER.SCHEDULER_JOB_SEQUENCE START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SCHEDULER.SCHEDULER_JOB_EXECUTION_SEQUENCE START WITH 1 INCREMENT BY 1;

CREATE TABLE SCHEDULER.SCHEDULER_JOB (
                                        JOB_PK_ID                  NUMBER(19)         PRIMARY KEY,
                                        JOB_NAME                   VARCHAR2(200)      NOT NULL,
                                        JOB_TYPE                   VARCHAR2(200)      NOT NULL,
                                        JOB_PARAMETERS             CLOB,
                                        JOB_DESCRIPTION            VARCHAR2(500),
                                        CRON_EXPRESSION            VARCHAR2(200),

                                        LAST_RUN_STARTED_AT        TIMESTAMP,
                                        LAST_RUN_COMPLETED_AT      TIMESTAMP,
                                        LAST_RUN_STATUS            VARCHAR2(50),
                                        LAST_RUN_ERROR             CLOB,
                                        LAST_FAILURE_AT            TIMESTAMP,

                                        NEXT_RUN                   TIMESTAMP,

                                        EXECUTION_COUNT            NUMBER(10) DEFAULT 0 NOT NULL,

                                        JOB_ENABLED                NUMBER(1) DEFAULT 0 NOT NULL,
                                        MANUAL_TRIGGER_ALLOWED     NUMBER(1) DEFAULT 0 NOT NULL,

                                        VERSION                    NUMBER(10) DEFAULT 0 NOT NULL,

                                        ADDED_BY                  VARCHAR2(20),
                                        ADD_TS                    TIMESTAMP,
                                        UPDATED_BY                VARCHAR2(20),
                                        UPDATE_TS                 TIMESTAMP,

                                        MAX_WAITING_QUEUE_SIZE    NUMBER(10) DEFAULT 10 NOT NULL,
                                        MAX_ALLOWED_DURATION_MS   NUMBER(19) DEFAULT 3600000 NOT NULL,

                                        RETRY_COUNT               NUMBER(10) DEFAULT 0 NOT NULL,
                                        RETRY_DELAY_SECONDS       NUMBER(10) DEFAULT 30 NOT NULL,

                                        CIRCUIT_OPEN_UNTIL        TIMESTAMP,
                                        CONCURRENCY_POLICY        VARCHAR2(20),

                                        ALERT_EMAIL               VARCHAR2(200),
                                        LAST_ALERT_SENT_AT        TIMESTAMP,
                                        ALERT_COOLDOWN_MINUTES    NUMBER(10),
                                        ALERT_FAILURE_COUNT       NUMBER(10) DEFAULT 0,

                                        CONSTRAINT UK_SCHEDULER_JOB_NAME UNIQUE (JOB_NAME)
);


CREATE TABLE SCHEDULER.SCHEDULER_JOB_EXECUTION (
                                                  JOB_EXEC_PK_ID           NUMBER(19) PRIMARY KEY,
                                                  JOB_PK_ID                NUMBER(19) NOT NULL,

                                                  STARTED_AT               TIMESTAMP,
                                                  COMPLETED_AT             TIMESTAMP,

                                                  JOB_STATUS               VARCHAR2(30),

                                                  ERROR_MSG                CLOB,
                                                  FAILURE_TYPE             VARCHAR2(50),

                                                  DURATION_MS              NUMBER(19),
                                                  QUEUED_AT                TIMESTAMP,

                                                  STARTED_BY               VARCHAR2(20),
                                                  RETRY_NUMBER             NUMBER(10) DEFAULT 0,

                                                  TIMEOUT_OCCURRED         NUMBER(1),
                                                  WAIT_REASON              VARCHAR2(255),
                                                  LONG_RUNNING_ALERT_SENT  NUMBER(1),

                                                  TRIGGER_TYPE             VARCHAR2(30),
                                                  RUNNING_ON_HOST          VARCHAR2(100),

                                                  CONSTRAINT FK_JOBEXEC_JOB
                                                      FOREIGN KEY (JOB_PK_ID)
                                                          REFERENCES SCHEDULER.SCHEDULER_JOB(JOB_PK_ID)
);