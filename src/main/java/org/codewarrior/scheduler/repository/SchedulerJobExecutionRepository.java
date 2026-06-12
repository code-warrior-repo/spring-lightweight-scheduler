package org.codewarrior.scheduler.repository;

import org.codewarrior.scheduler.domain.SchedulerJobExecution;
import org.codewarrior.scheduler.dto.JobExecutionStatus;
import org.codewarrior.scheduler.dto.JobResponseDto;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SchedulerJobExecutionRepository extends JpaRepository<SchedulerJobExecution, Long> {


    //  @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(readOnly = true)
    @Query("""
                SELECT CASE WHEN COUNT(e) > 0 THEN TRUE ELSE FALSE END
                FROM SchedulerJobExecution e
                WHERE e.job.jobPkId = :jobId
                  AND e.status = 'RUNNING'
            """)
    boolean existsRunningExecutionWithLock(@Param("jobId") Long jobId);

    @Query("""
                select new org.codewarrior.scheduler.dto.JobResponseDto(
                    j.jobPkId,
                    j.jobName,
                    j.jobType,
                    j.jobDescription,
                    j.jobEnabled,
                    j.cronExpression,
                    j.jobParameters,
                    CAST(e.status AS string),
                    CAST(e.startedAt AS string),
                    CAST(e.completedAt AS string),
                    e.durationMs,
                    e.errorMessage
                )
                from SchedulerJobExecution e
                join e.job j
                where e.status = 'RUNNING'
                order by e.startedAt desc
            """)
    List<JobResponseDto> findRunningJobs();


    @Query("""
                select new org.codewarrior.scheduler.dto.JobResponseDto(
                    j.jobPkId,
                    j.jobName,
                    j.jobType,
                    j.jobDescription,
                    j.jobEnabled,
                    j.cronExpression,
                    j.jobParameters,
                    CAST(e.status AS string),
                    CAST(e.startedAt AS string),
                    CAST(e.completedAt AS string),
                    e.durationMs,
                    e.errorMessage
                )
                from SchedulerJobExecution e
                join e.job j
                where j.jobPkId = :jobId
                  and e.completedAt is not null
                order by e.completedAt desc
            """)
    List<JobResponseDto> findTopNCompletedRuns(@Param("jobId") Long jobId, Pageable pageable);

    default List<JobResponseDto> findTopNCompletedRuns(Long jobId, int limit) {
        return findTopNCompletedRuns(jobId, PageRequest.of(0, limit));
    }


    List<SchedulerJobExecution>
    findByJob_JobPkIdAndStatus(Long jobId, JobExecutionStatus status);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SchedulerJobExecution>
    findTopByJob_JobPkIdAndStatusOrderByQueuedAtAsc(
            Long jobId,
            JobExecutionStatus status
    );


    @Query("""
                select distinct e.job.jobPkId
                from SchedulerJobExecution e
                where e.status = 'WAITING'
                  and (e.queuedAt is null or e.queuedAt <= :now)
                order by e.job.jobPkId
            """)
    List<Long> findJobIdsWithDueWaitingExecutions(@Param("now") LocalDateTime now);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                select e
                from SchedulerJobExecution e
                where e.job.jobPkId = :jobId
                  and e.status = 'WAITING'
                order by e.queuedAt asc
            """)
    List<SchedulerJobExecution> findWaitingExecutionsForUpdate(
            @Param("jobId") Long jobId,
            Pageable pageable
    );


    default Optional<SchedulerJobExecution> findNextWaitingForUpdate(Long jobId) {
        return findWaitingExecutionsForUpdate(jobId, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }
    long countByJob_JobPkIdAndStatus(Long jobId, JobExecutionStatus status);


    List<SchedulerJobExecution> findByStatus(JobExecutionStatus jobExecutionStatus);
}
