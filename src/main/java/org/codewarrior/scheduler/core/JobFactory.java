package org.codewarrior.scheduler.core;

import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;

public interface JobFactory {
    Runnable create(SchedulerJob job, SchedulerJobExecution exec);
}
