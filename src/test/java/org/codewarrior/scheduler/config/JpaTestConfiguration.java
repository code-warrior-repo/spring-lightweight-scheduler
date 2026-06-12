package org.codewarrior.scheduler.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@Profile("integ-test")
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {
                "org.codewarrior.scheduler.repository"
        }
)
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@EntityScan(basePackages = {"org.codewarrior.scheduler.domain"})
public class JpaTestConfiguration {

}