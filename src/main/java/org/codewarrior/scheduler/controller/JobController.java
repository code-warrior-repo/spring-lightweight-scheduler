package org.codewarrior.scheduler.controller;

import org.codewarrior.scheduler.constants.JobConstants;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.dto.JobRequestDto;
import org.codewarrior.scheduler.dto.JobResponseDto;
import org.codewarrior.scheduler.service.JobService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<JobResponseDto>> getAllJobs() {
        return ResponseEntity.ok(jobService.findAllJobs());
    }

    @GetMapping(value = "/running", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<JobResponseDto>> getRunningJobs() {
        return ResponseEntity.ok(jobService.findRunningJobs());
    }

    @GetMapping(value = "/completed", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<JobResponseDto>> getLastCompletedRuns() {
        return ResponseEntity.ok(jobService.findTopNCompletedRuns());
    }

    // ADD A JOB
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SchedulerJob> addJob(@Valid @RequestBody JobRequestDto job) {

        return ResponseEntity.ok(jobService.addJob(job));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SchedulerJob> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getJob(id));
    }

    // EDIT A JOB (update cron or params)
    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SchedulerJob> updateJob(
            @PathVariable Long id,
            @Valid @RequestBody JobRequestDto dto) {

        return ResponseEntity.ok(jobService.updateJob(id, dto));
    }

    // DELETE A JOB
    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteJob(@PathVariable Long id) {
        jobService.deleteJob(id);// remove from DB
        return ResponseEntity.ok("Job deleted successfully!!");
    }

    @PostMapping(value = "/{id}/run", produces = MediaType.APPLICATION_JSON_VALUE)
    public String runNow(@PathVariable Long id, HttpServletRequest request) {
        Object userId = request.getSession().getAttribute(JobConstants.KEY_SESSION_USER_ID);
        if (userId == null) {
            throw new IllegalStateException("No user id found in session");
        }
        jobService.runNow(id, userId.toString());
        return "Job triggered successfully!!";
    }

    @PostMapping(value = "/{id}/pause", produces = MediaType.APPLICATION_JSON_VALUE)
    public String pauseJob(@PathVariable Long id) {
        jobService.pauseJob(id);
        return "Job paused successfully!!";
    }

    @PostMapping(value = "/{id}/enable", produces = MediaType.APPLICATION_JSON_VALUE)
    public String enableJob(@PathVariable Long id) {
        jobService.enableJob(id);
        return "Job enabled successfully!!";
    }
}
