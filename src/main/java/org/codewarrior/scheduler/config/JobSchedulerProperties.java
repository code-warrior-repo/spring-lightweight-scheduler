package org.codewarrior.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "scheduler")
public class JobSchedulerProperties {

    // Supports multiple packages
    private List<String> jobScanPackages;

    public List<String> getJobScanPackages() {
        return jobScanPackages;
    }

    public void setJobScanPackages(List<String> jobScanPackages) {
        this.jobScanPackages = jobScanPackages;
    }
}