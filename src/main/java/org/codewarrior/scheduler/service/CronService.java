package org.codewarrior.scheduler.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Log4j2
@Component
public class CronService {

    public LocalDateTime computeNextRun(String cron, LocalDateTime from) {
        try {
            CronExpression exp = CronExpression.parse(cron);
            return exp.next(from);
        } catch (Exception e) {
            log.error("Failed to compute nextRun for cron={}", cron, e);
            return null;
        }
    }
}