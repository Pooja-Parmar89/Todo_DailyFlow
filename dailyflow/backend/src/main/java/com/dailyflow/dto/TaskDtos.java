package com.dailyflow.dto;

import com.dailyflow.domain.Priority;
import com.dailyflow.domain.RecurrenceType;
import com.dailyflow.domain.TaskStatus;
import com.dailyflow.dto.CategoryDtos.CategorySummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class TaskDtos {

    private TaskDtos() {
    }

    /**
     * Create / update payload. Cross-field rules (due >= start, recurrence needs a due date, category
     * ownership) are checked in TaskService and reported as field errors.
     */
    public record TaskRequest(
            @NotBlank(message = "Title is required") @Size(max = 200, message = "Title must be at most 200 characters")
            String title,
            @Size(max = 5000, message = "Description must be at most 5000 characters")
            String description,
            Long categoryId,
            @NotNull(message = "Priority is required") Priority priority,
            TaskStatus status,
            LocalDateTime startAt,
            LocalDateTime dueAt,
            RecurrenceType recurrenceType,
            @Min(value = 1, message = "Interval must be at least 1 day")
            @Max(value = 365, message = "Interval must be at most 365 days")
            Integer recurrenceInterval,
            LocalDate recurrenceEndDate) {
    }

    public record StatusRequest(@NotNull(message = "Status is required") TaskStatus status) {
    }

    public record TaskDto(
            Long id,
            String title,
            String description,
            CategorySummary category,
            Priority priority,
            TaskStatus status,
            LocalDateTime startAt,
            LocalDateTime dueAt,
            LocalDateTime completedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean overdue,
            RecurrenceType recurrenceType,
            Integer recurrenceInterval,
            LocalDate recurrenceEndDate) {
    }

    /** Query filters for the task list. All optional. */
    public record TaskFilter(
            String q,
            Long categoryId,
            List<Priority> priorities,
            List<TaskStatus> statuses,
            LocalDate dueFrom,
            LocalDate dueTo,
            boolean overdueOnly) {
    }

    public record RescheduleRequest(@NotNull(message = "New date is required") LocalDate newDate) {
    }
}
