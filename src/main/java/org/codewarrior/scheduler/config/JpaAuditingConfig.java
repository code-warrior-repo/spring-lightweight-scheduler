package org.codewarrior.scheduler.config;

import org.codewarrior.scheduler.constants.JobConstants;
import jakarta.persistence.EntityListeners;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Configuration
@EntityListeners(AuditingEntityListener.class)
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            // Grab current request (if we're in a web context)
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attrs != null) {
                var request = attrs.getRequest();
                var session = request.getSession(false);
                String username = (session != null)
                        ? (String) session.getAttribute(JobConstants.KEY_SESSION_USER_ID)
                        : null;

                return Optional.ofNullable(username).or(() -> Optional.of("SYSTEM"));
            }

            return Optional.of("SYSTEM");
        };
    }
}