package org.codewarrior.scheduler.controller;

import org.codewarrior.scheduler.constants.JobConstants;
import org.codewarrior.scheduler.core.JobRegistry;
import org.codewarrior.scheduler.domain.SchedulerJob;
import org.codewarrior.scheduler.dto.JobRequestDto;
import org.codewarrior.scheduler.dto.JobResponseDto;
import org.codewarrior.scheduler.exception.JobExceptionHandler;
import org.codewarrior.scheduler.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@ActiveProfiles("integ-test")
@WebMvcTest(JobController.class)
@Import(JobExceptionHandler.class)
class JobControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private JobRegistry jobRegistry;
    @MockitoBean
    private JobService jobService;
    @Autowired
    private JobController jobController;

/*
    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(jobController)
                .setValidator(mock(Validator.class)) // disable validation
                .build();
    }
*/

    private JobRequestDto newJobRequest() {
        return new JobRequestDto(
                1L,
                "TestJob",
                "TestJob",
                "ACT",
                null,
                "0/5 * * * * *",
                "{}",
                true,
                false,
                0,
                0,
                0,
                0L,
                ""

        );
    }

    @Test
    void testGetAllJobs() throws Exception {
        when(jobService.findAllJobs())
                .thenReturn(List.of(new JobResponseDto(1L, "DONE_JOB", "TYPE",
                        null,
                        true, "CRON", null, "SUCCESS",
                        null, null, null, null)));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobName").value("DONE_JOB"));
    }

    @Test
    void testGetRunningJobs() throws Exception {


        when(jobService.findRunningJobs())
                .thenReturn(List.of(new JobResponseDto(1L, "RUNNING_JOB", "TYPE",
                        null,
                        true, "CRON", null, "SUCCESS",
                        null, null, null, null)));

        mockMvc.perform(get("/api/jobs/running"))

                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobName").value("RUNNING_JOB"));
    }

    @Test
    void testGetCompletedJobs() throws Exception {
        when(jobService.findTopNCompletedRuns())
                .thenReturn(List.of(new JobResponseDto(1L, "DONE_JOB", "TYPE",
                        null,
                        true, "CRON", null, "SUCCESS",
                        null, null, null, null)));

        mockMvc.perform(get("/api/jobs/completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobName").value("DONE_JOB"));
    }

    @Test
    void testAddJob() throws Exception {
        SchedulerJob saved = new SchedulerJob();
        saved.setJobPkId(10L);
        when(jobRegistry.isValidJobType("TestJob")).thenReturn(true);
        when(jobService.addJob(any())).thenReturn(saved);

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newJobRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobPkId").value(10L));
    }

    @Test
    void testUpdateJob() throws Exception {
        SchedulerJob updated = new SchedulerJob();
        updated.setJobPkId(20L);
        updated.setJobName("UPDATED_JOB");
        updated.setJobType("TestJob");

        when(jobRegistry.isValidJobType("TestJob")).thenReturn(true);
        when(jobService.updateJob(eq(20L), any(JobRequestDto.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/jobs/20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newJobRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobPkId").value(20L));
    }

    @Test
    void testDeleteJob() throws Exception {
        doNothing().when(jobService).deleteJob(5L);

        mockMvc.perform(delete("/api/jobs/5"))
                .andExpect(status().isOk())
                .andExpect(content().string("Job deleted successfully!!"));
    }

    @Test
    void testRunNow() throws Exception {

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(JobConstants.KEY_SESSION_USER_ID, "user123");

        mockMvc.perform(post("/api/jobs/7/run").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("Job triggered successfully!!"));

        verify(jobService).runNow(7L, "user123");
    }

    @Test
    void testRunNow_NoUserInSession() throws Exception {
        mockMvc.perform(post("/api/jobs/7/run"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"No user id found in session\"}"));
    }


    @Test
    void testPauseJob() throws Exception {
        mockMvc.perform(post("/api/jobs/8/pause"))
                .andExpect(status().isOk())
                .andExpect(content().string("Job paused successfully!!"));

        verify(jobService).pauseJob(8L);
    }

    @Test
    void testEnableJob() throws Exception {
        mockMvc.perform(post("/api/jobs/9/enable"))
                .andExpect(status().isOk())
                .andExpect(content().string("Job enabled successfully!!"));

        verify(jobService).enableJob(9L);
    }
}