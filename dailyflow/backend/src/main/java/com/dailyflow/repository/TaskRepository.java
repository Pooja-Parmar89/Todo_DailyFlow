package com.dailyflow.repository;

import com.dailyflow.domain.Priority;
import com.dailyflow.domain.Task;
import com.dailyflow.domain.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /** Ownership is part of every lookup, so another user's task id simply does not exist for the caller. */
    @EntityGraph(attributePaths = "category")
    Optional<Task> findByIdAndUserId(Long id, Long userId);

    /** Fetches the category in the same query to avoid N+1 when mapping a page of tasks. */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, TaskStatus status);

    long countByUserIdAndCategoryId(Long userId, Long categoryId);

    @Query("""
            select count(t) from Task t
            where t.user.id = :userId
              and t.recurrenceType = com.dailyflow.domain.RecurrenceType.NONE
              and t.status <> com.dailyflow.domain.TaskStatus.COMPLETED
              and t.dueAt < :now
            """)
    long countOverdue(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = "category")
    List<Task> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "category")
    List<Task> findTop5ByUserIdOrderByUpdatedAtDesc(Long userId);

    /** One-off (non recurring) tasks due in [from, toExclusive). */
    @Query("""
            select t from Task t left join fetch t.category
            where t.user.id = :userId
              and t.recurrenceType = com.dailyflow.domain.RecurrenceType.NONE
              and t.dueAt >= :from and t.dueAt < :toExclusive
            order by t.dueAt asc, t.id asc
            """)
    List<Task> findOneOffDueBetween(@Param("userId") Long userId,
                                    @Param("from") LocalDateTime from,
                                    @Param("toExclusive") LocalDateTime toExclusive);

    /** Recurring series that can have an occurrence inside the window and have not been ended. */
    @Query("""
            select t from Task t left join fetch t.category
            where t.user.id = :userId
              and t.recurrenceType <> com.dailyflow.domain.RecurrenceType.NONE
              and t.status <> com.dailyflow.domain.TaskStatus.COMPLETED
              and t.dueAt < :toExclusive
              and (t.recurrenceEndDate is null or t.recurrenceEndDate >= :fromDate)
            """)
    List<Task> findActiveRecurring(@Param("userId") Long userId,
                                   @Param("toExclusive") LocalDateTime toExclusive,
                                   @Param("fromDate") LocalDate fromDate);

    @Query("""
            select t from Task t left join fetch t.category
            where t.user.id = :userId
              and t.recurrenceType = com.dailyflow.domain.RecurrenceType.NONE
              and t.status <> com.dailyflow.domain.TaskStatus.COMPLETED
              and t.reminderSentAt is null
              and t.dueAt > :from and t.dueAt <= :to
            order by t.dueAt asc
            """)
    List<Task> findDueForReminder(@Param("userId") Long userId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    @Query("""
            select t.category.id as categoryId, count(t) as total
            from Task t
            where t.user.id = :userId and t.category is not null
            group by t.category.id
            """)
    List<CategoryCount> countByCategory(@Param("userId") Long userId);

    @Query("""
            select t.completedAt as completedAt, c.name as categoryName, t.priority as priority
            from Task t left join t.category c
            where t.user.id = :userId and t.completedAt >= :since
            """)
    List<CompletionRow> findCompletionsSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("select t.createdAt from Task t where t.user.id = :userId and t.createdAt >= :since")
    List<LocalDateTime> findCreationTimesSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    interface CategoryCount {
        Long getCategoryId();

        Long getTotal();
    }

    interface CompletionRow {
        LocalDateTime getCompletedAt();

        String getCategoryName();

        Priority getPriority();
    }
}
