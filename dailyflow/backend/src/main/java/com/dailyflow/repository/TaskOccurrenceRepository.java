package com.dailyflow.repository;

import com.dailyflow.domain.TaskOccurrence;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskOccurrenceRepository extends JpaRepository<TaskOccurrence, Long> {

    Optional<TaskOccurrence> findByTaskIdAndOccurrenceDate(Long taskId, LocalDate occurrenceDate);

    @Query("""
            select o from TaskOccurrence o
            where o.taskId in :taskIds
              and ((o.occurrenceDate between :from and :to) or (o.rescheduledTo between :from and :to))
            """)
    List<TaskOccurrence> findForWindow(@Param("taskIds") Collection<Long> taskIds,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    @Modifying
    @Query("delete from TaskOccurrence o where o.taskId = :taskId")
    void deleteAllByTaskId(@Param("taskId") Long taskId);
}
