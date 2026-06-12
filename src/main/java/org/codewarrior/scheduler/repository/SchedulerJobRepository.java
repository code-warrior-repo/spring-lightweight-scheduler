package org.codewarrior.scheduler.repository;

import org.codewarrior.scheduler.domain.SchedulerJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchedulerJobRepository extends JpaRepository<SchedulerJob, Long> {

    List<SchedulerJob> findByJobEnabled(Boolean jobEnabled);

    boolean existsByJobName(String jobName);

    @Query("select j.jobPkId from SchedulerJob j")
    List<Long> findAllJobPkIds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                select j
                from SchedulerJob j
                where j.jobPkId = :jobId
            """)
    Optional<SchedulerJob> findByIdForUpdate(@Param("jobId") Long jobId);

}