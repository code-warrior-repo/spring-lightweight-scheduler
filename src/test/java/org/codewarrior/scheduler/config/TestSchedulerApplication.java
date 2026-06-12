package org.codewarrior.scheduler.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "org.codewarrior.scheduler")
public class TestSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestSchedulerApplication.class, args);
    }

}
