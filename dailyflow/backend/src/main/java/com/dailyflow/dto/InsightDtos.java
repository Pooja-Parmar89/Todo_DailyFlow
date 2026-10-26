package com.dailyflow.dto;

import com.dailyflow.domain.OccurrenceStatus;
import com.dailyflow.domain.Priority;
import com.dailyflow.domain.TaskStatus;
import com.dailyflow.dto.TaskDtos.TaskDto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only views computed from persisted data: calendar, dashboard, reports, reminders. */
public final class InsightDtos {

    private InsightDtos() {
    }

    /** One entry on the calendar: a one-off task or one occurrence of a recurring task. */
    public record CalendarItem(
            String key,
            Long taskId,
            String title,
            Long categoryId,
            String categoryName,
            String categoryColor,
            Priority priority,
            /** Task status for one-off tasks; COMPLETED/TODO derived from the occurrence for recurring ones. */
            TaskStatus status,
            LocalDateTime dueAt,
            boolean recurring,
            LocalDate occurrenceDate,
            /** PENDING, COMPLETED, SKIPPED or RESCHEDULED for recurring items; null for one-off tasks. */
            String occurrenceStatus,
            LocalDate originalDate,
            boolean overdue) {
    }

    public record DailyCount(LocalDate date, long completed, long created) {
    }

    public record DashboardDto(
            long total,
            long completed,
            long pending,
            long inProgress,
            long overdue,
            long dueToday,
            long dueTodayCompleted,
            double completionPercentage,
            List<CalendarItem> upcoming,
            List<TaskDto> recentlyCreated,
            List<TaskDto> recentlyUpdated,
            List<DailyCount> weekly) {
    }

    public record NamedCount(String name, long count) {
    }

    public record ProductivityReport(
            LocalDate from,
            LocalDate to,
            long totalCompleted,
            long totalCreated,
            double averageCompletedPerDay,
            int currentStreakDays,
            List<DailyCount> daily,
            List<NamedCount> byCategory,
            List<NamedCount> byPriority) {
    }

    public record ReminderSummary(long overdueCount, List<CalendarItem> dueSoon, int leadMinutes) {
    }

    public record OccurrenceView(Long taskId, LocalDate occurrenceDate, OccurrenceStatus status,
                                 LocalDate rescheduledTo) {
    }
}
