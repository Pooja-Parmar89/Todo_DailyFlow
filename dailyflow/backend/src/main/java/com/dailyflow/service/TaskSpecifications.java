package com.dailyflow.service;

import com.dailyflow.domain.RecurrenceType;
import com.dailyflow.domain.Task;
import com.dailyflow.domain.TaskStatus;
import com.dailyflow.dto.TaskDtos.TaskFilter;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Builds the dynamic WHERE clause for task search. Always scoped to one owner. */
public final class TaskSpecifications {

    private static final char ESCAPE = '!';

    private TaskSpecifications() {
    }

    public static Specification<Task> matching(Long userId, TaskFilter filter, LocalDateTime now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (filter.q() != null && !filter.q().isBlank()) {
                String pattern = "%" + escapeLike(filter.q().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("title")), pattern, ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), pattern, ESCAPE)));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filter.categoryId()));
            }
            if (filter.priorities() != null && !filter.priorities().isEmpty()) {
                predicates.add(root.get("priority").in(filter.priorities()));
            }
            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }
            if (filter.dueFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("dueAt"),
                        filter.dueFrom().atStartOfDay()));
            }
            if (filter.dueTo() != null) {
                predicates.add(cb.lessThan(root.<LocalDateTime>get("dueAt"),
                        filter.dueTo().plusDays(1).atStartOfDay()));
            }
            if (filter.overdueOnly()) {
                predicates.add(cb.equal(root.get("recurrenceType"), RecurrenceType.NONE));
                predicates.add(cb.notEqual(root.get("status"), TaskStatus.COMPLETED));
                predicates.add(cb.lessThan(root.<LocalDateTime>get("dueAt"), now));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Makes user input literal inside LIKE (escape char is '!', which avoids backslash quirks across databases). */
    static String escapeLike(String input) {
        return input.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
