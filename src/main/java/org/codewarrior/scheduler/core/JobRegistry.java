package org.codewarrior.scheduler.core;

import org.codewarrior.scheduler.config.JobSchedulerProperties;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class JobRegistry {

    private final Map<String, JobFactory> registry = new HashMap<>();

    public JobRegistry(JobSchedulerProperties props) {
        log.info("Initializing JobRegistry…");
        scanPackages(props.getJobScanPackages());
    }

    /* ==========================================================
       PACKAGE SCANNER
       ========================================================== */

    private void scanPackages(java.util.List<String> packages) {

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);

        scanner.addIncludeFilter(new AnnotationTypeFilter(ScheduledJob.class));

        for (String basePackage : packages) {
            log.info("Scanning for @ScheduledJob classes in: {}", basePackage);

            for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
                registerClass(bd.getBeanClassName());
            }
        }
    }

    /* ==========================================================
       CLASS REGISTRATION
       ========================================================== */

    private void registerClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);

            ScheduledJob ann = clazz.getAnnotation(ScheduledJob.class);
            if (ann == null) {
                log.warn("Class {} was scanned but missing @ScheduledJob", className);
                return;
            }

            String jobType = ann.value();

            /* ------------------------------------------------------
               PRIMARY: constructor(SchedulerJob, SchedulerJobExecution)
               ------------------------------------------------------ */
            try {
                Constructor<?> primaryCtor =
                        clazz.getConstructor(SchedulerJob.class, SchedulerJobExecution.class);

                registry.put(jobType, (job, exec) -> {
                    try {
                        return (Runnable) primaryCtor.newInstance(job, exec);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to instantiate job " + jobType, e);
                    }
                });

                log.info("Registered jobType={} using (SchedulerJob, SchedulerJobExecution) constructor → {}",
                        jobType, clazz.getSimpleName());
                return;

            } catch (NoSuchMethodException ex) {
                // Continue to fallback
            }

            /* ------------------------------------------------------
               FALLBACK: no-arg constructor
               ------------------------------------------------------ */
            try {
                Constructor<?> noArgCtor = clazz.getConstructor();

                registry.put(jobType, (job, exec) -> {
                    try {
                        return (Runnable) noArgCtor.newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to instantiate job (no-arg) " + jobType, e);
                    }
                });

                log.warn("Registered jobType={} using NO-ARG constructor → {} (job/exec not injected!)",
                        jobType, clazz.getSimpleName());
                return;

            } catch (NoSuchMethodException ex) {
                // No fallback available
            }

            /* ------------------------------------------------------
               ERROR: No supported constructors
               ------------------------------------------------------ */
            log.error("Class {} must have either:" +
                            "\n 1) A constructor (SchedulerJob, SchedulerJobExecution)" +
                            "\n OR" +
                            "\n 2) A no-arg constructor",
                    clazz.getName());

        } catch (Exception e) {
            log.error("Failed to register job class {}", className, e);
        }
    }

    /* ==========================================================
       MANUAL OVERRIDE (used by tests)
       ========================================================== */

    public void register(String jobType, JobFactory factory) {
        registry.put(jobType, factory);
        log.info("Dynamically registered jobType={} via override", jobType);
    }

    /* ==========================================================
       FACTORY LOOKUP
       ========================================================== */

    public Runnable create(String jobType, SchedulerJob job, SchedulerJobExecution exec) {
        JobFactory factory = registry.get(jobType);

        if (factory == null) {
            throw new IllegalArgumentException("Unknown job type " + jobType);
        }

        return factory.create(job, exec);
    }

    public boolean isValidJobType(String jobType) {
        return registry.containsKey(jobType);
    }

    public Set<String> getValidJobTypes() {
        return registry.keySet();
    }
}