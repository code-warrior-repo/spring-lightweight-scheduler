package org.codewarrior.scheduler.core;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ScheduledJob {
    String value();  // jobType
}
