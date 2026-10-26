package com.dailyflow.service;

import com.dailyflow.domain.Category;
import com.dailyflow.domain.OccurrenceStatus;
import com.dailyflow.domain.Task;
import com.dailyflow.domain.TaskOccurrence;
import com.dailyflow.domain.TaskStatus;
import com.dailyflow.dto.InsightDtos.CalendarItem;
import com.dailyflow.exception.BadRequestException;
import com.dailyflow.repository.TaskOccurrenceRepository;
import com.dailyflow.repository.TaskRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the calendar: one-off tasks by due date plus occurrences of recurring tasks expanded from their rule. */
@Service
public class CalendarService {

    public static final int MAX_RANGE_DAYS = 100;
    public static final String PENDING = "PENDING";

    private final TaskRepository taskRepository;
    private final TaskOccurrenceRepository occurrenceRepository;
    private final RecurrenceService recurrenceService;
    private final Clock clock;

    public CalendarService(TaskRepository taskRepository, TaskOccurrenceRepository occurrenceRepository,
                           RecurrenceService recurrenceService, Clock clock) {
        this.taskRepository = taskRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.recurrenceService = recurrenceService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CalendarItem> items(Long userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BadRequestException("'to' must not be before 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BadRequestException("The date range may span at most " + MAX_RANGE_DAYS + " days");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();

        List<CalendarItem> items = new ArrayList<>();

        for (Task task : taskRepository.findOneOffDueBetween(userId, start, endExclusive)) {
            boolean overdue = task.getStatus() != TaskStatus.COMPLETED && task.getDueAt().isBefore(now);
            items.add(item(task, task.getStatus(), task.getDueAt(), false, null, null, null, overdue));
        }

        List<Task> recurring = taskRepository.findActiveRecurring(userId, endExclusive, from);
        if (!recurring.isEmpty()) {
            List<Long> ids = recurring.stream().map(Task::getId).toList();
            Map<Long, List<TaskOccurrence>> exceptions = occurrenceRepository.findForWindow(ids, from, to).stream()
                    .collect(Collectors.groupingBy(TaskOccurrence::getTaskId));

            for (Task task : recurring) {
                Map<LocalDate, TaskOccurrence> byDate = new HashMap<>();
                List<TaskOccurrence> rows = exceptions.getOrDefault(task.getId(), List.of());
                rows.forEach(o -> byDate.put(o.getOccurrenceDate(), o));

                for (LocalDate date : recurrenceService.occurrenceDates(task, from, to)) {
                    TaskOccurrence exception = byDate.get(date);
                    LocalDateTime due = date.atTime(task.getDueAt().toLocalTime());
                    if (exception == null) {
                        items.add(item(task, task.getStatus(), due, true, date, PENDING, null, due.isBefore(now)));
                    } else if (exception.getStatus() == OccurrenceStatus.COMPLETED) {
                        items.add(item(task, TaskStatus.COMPLETED, due, true, date, "COMPLETED", null, false));
                    } else if (exception.getStatus() == OccurrenceStatus.SKIPPED) {
                        items.add(item(task, task.getStatus(), due, true, date, "SKIPPED", null, false));
                    }
                    // RESCHEDULED occurrences are shown on their new date below, not on the original one.
                }
                for (TaskOccurrence o : rows) {
                    LocalDate target = o.getRescheduledTo();
                    if (o.getStatus() == OccurrenceStatus.RESCHEDULED && target != null
                            && !target.isBefore(from) && !target.isAfter(to)) {
                        LocalDateTime due = target.atTime(task.getDueAt().toLocalTime());
                        items.add(item(task, task.getStatus(), due, true, target, "RESCHEDULED",
                                o.getOccurrenceDate(), due.isBefore(now)));
                    }
                }
            }
        }

        items.sort(Comparator.comparing(CalendarItem::dueAt).thenComparing(CalendarItem::title)
                .thenComparing(CalendarItem::taskId));
        return items;
    }

    private CalendarItem item(Task task, TaskStatus status, LocalDateTime dueAt, boolean recurring,
                              LocalDate occurrenceDate, String occurrenceStatus, LocalDate originalDate,
                              boolean overdue) {
        Category category = task.getCategory();
        return new CalendarItem(
                recurring ? "t" + task.getId() + "@" + occurrenceDate + (originalDate != null ? "<" + originalDate : "")
                        : "t" + task.getId(),
                task.getId(),
                task.getTitle(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                category == null ? null : category.getColor(),
                task.getPriority(),
                status,
                dueAt,
                recurring,
                occurrenceDate,
                occurrenceStatus,
                originalDate,
                overdue);
    }
}
