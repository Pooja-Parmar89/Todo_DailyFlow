package com.dailyflow.service;

import com.dailyflow.domain.Task;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Recurrence rules.
 *
 * Storage model: a recurring task is ONE row in {@code tasks}. Its due date is the anchor (first occurrence) and
 * {@code recurrence_interval} is the number of days between occurrences (DAILY=1, WEEKLY=7, CUSTOM=n), optionally
 * limited by {@code recurrence_end_date}. Occurrences are never pre-generated: they are computed from the rule for
 * the requested window. Only exceptions (completed / skipped / rescheduled) are stored, in {@code task_occurrences},
 * with a unique key on (task_id, occurrence_date). Because there is nothing to generate, a retried request cannot
 * create duplicate occurrences, and repeating an exception operation just rewrites the same row.
 */
@Service
public class RecurrenceService {

    public int intervalDays(Task task) {
        Integer interval = task.getRecurrenceInterval();
        return interval == null || interval < 1 ? 1 : interval;
    }

    /** Occurrence dates of the rule inside [from, to], inclusive, in ascending order. */
    public List<LocalDate> occurrenceDates(Task task, LocalDate from, LocalDate to) {
        if (!task.isRecurring() || task.getDueAt() == null || to.isBefore(from)) {
            return List.of();
        }
        int step = intervalDays(task);
        LocalDate anchor = task.getDueAt().toLocalDate();
        LocalDate last = task.getRecurrenceEndDate() != null && task.getRecurrenceEndDate().isBefore(to)
                ? task.getRecurrenceEndDate() : to;

        LocalDate first = anchor;
        if (from.isAfter(anchor)) {
            long gap = ChronoUnit.DAYS.between(anchor, from);
            long steps = (gap + step - 1) / step; // round up to the next occurrence on or after 'from'
            first = anchor.plusDays(steps * step);
        }
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(step)) {
            dates.add(d);
        }
        return dates;
    }

    public boolean isOccurrenceDate(Task task, LocalDate date) {
        if (!task.isRecurring() || task.getDueAt() == null) {
            return false;
        }
        LocalDate anchor = task.getDueAt().toLocalDate();
        if (date.isBefore(anchor)) {
            return false;
        }
        if (task.getRecurrenceEndDate() != null && date.isAfter(task.getRecurrenceEndDate())) {
            return false;
        }
        return ChronoUnit.DAYS.between(anchor, date) % intervalDays(task) == 0;
    }
}
