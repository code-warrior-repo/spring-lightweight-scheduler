package org.codewarrior.scheduler.jobs;

import org.codewarrior.scheduler.core.ScheduledJob;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ScheduledJob("LoadWinnersJob")
public class LoadWinnersJob implements Runnable {


    @Override
    public void run() {

        log.info("LoadWinnersJob is running");
        try {
            Thread.sleep(1000);
            log.info("LoadWinnersJob is finished");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
