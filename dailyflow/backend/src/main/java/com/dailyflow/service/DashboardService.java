package com.dailyflow.service;

import com.dailyflow.domain.TaskStatus;
import com.dailyflow.dto.InsightDtos.CalendarItem;
import com.dailyflow.dto.InsightDtos.DailyCount;
import com.dailyflow.dto.InsightDtos.DashboardDto;
import com.dailyflow.mapper.TaskMapper;
import com.dailyflow.repository.TaskRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard statistics, always computed from persisted data. Rules:
 * <ul>
 *   <li>total / completed / pending (TODO) / in progress count task rows (a recurring series counts once).</li>
 *   <li>overdue: one-off tasks that are not completed and whose due date/time is before now.</li>
 *   <li>dueToday: calendar items for today (one-off tasks and recurring occurrences) except skipped ones;
 *       dueTodayCompleted is how many of them are completed.</li>
 *   <li>completionPercentage = completed / total * 100, one decimal, 0 when there are no tasks.</li>
 *   <li>upcoming: not-completed calendar items due between now and 7 days from now, soonest first, max 5.</li>
 *   <li>weekly: for each of the last 7 days (including today) tasks completed and tasks created that day.</li>
 * </ul>
 */
@Service
public class DashboardService {

    private static final int UPCOMING_DAYS = 7;
    private static final int UPCOMING_LIMIT = 5;

    private final TaskRepository taskRepository;
    private final CalendarService calendarService;
    private final TaskMapper mapper;
    private final Clock clock;

    public DashboardService(TaskRepository taskRepository, CalendarService calendarService, TaskMapper mapper,
                            Clock clock) {
        this.taskRepository = taskRepository;
        this.calendarService = calendarService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        long total = taskRepository.countByUserId(userId);
        long completed = taskRepository.countByUserIdAndStatus(userId, TaskStatus.COMPLETED);
        long pending = taskRepository.countByUserIdAndStatus(userId, TaskStatus.TODO);
        long inProgress = taskRepository.countByUserIdAndStatus(userId, TaskStatus.IN_PROGRESS);
        long overdue = taskRepository.countOverdue(userId, now);
        double percentage = total == 0 ? 0.0 : Math.round(completed * 1000.0 / total) / 10.0;

        List<CalendarItem> todayItems = calendarService.items(userId, today, today).stream()
                .filter(i -> !"SKIPPED".equals(i.occurrenceStatus()))
                .toList();
        long dueToday = todayItems.size();
        long dueTodayCompleted = todayItems.stream().filter(i -> i.status() == TaskStatus.COMPLETED).count();

        List<CalendarItem> upcoming = calendarService.items(userId, today, today.plusDays(UPCOMING_DAYS)).stream()
                .filter(i -> i.status() != TaskStatus.COMPLETED)
                .filter(i -> !"SKIPPED".equals(i.occurrenceStatus()))
                .filter(i -> !i.dueAt().isBefore(now))
                .limit(UPCOMING_LIMIT)
                .toList();

        return new DashboardDto(total, completed, pending, inProgress, overdue, dueToday, dueTodayCompleted,
                percentage, upcoming,
                taskRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId).stream().map(mapper::toDto).toList(),
                taskRepository.findTop5ByUserIdOrderByUpdatedAtDesc(userId).stream().map(mapper::toDto).toList(),
                dailyCounts(userId, today.minusDays(6), today));
    }

    /** Per-day completed/created counts for [from, to], zero-filled so charts have no gaps. */
    List<DailyCount> dailyCounts(Long userId, LocalDate from, LocalDate to) {
        LocalDateTime since = from.atStartOfDay();
        Map<LocalDate, Long> completedByDay = taskRepository.findCompletionsSince(userId, since).stream()
                .map(r -> r.getCompletedAt().toLocalDate())
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()));
        Map<LocalDate, Long> createdByDay = taskRepository.findCreationTimesSince(userId, since).stream()
                .map(LocalDateTime::toLocalDate)
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()));

        Map<LocalDate, DailyCount> ordered = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            ordered.put(d, new DailyCount(d, completedByDay.getOrDefault(d, 0L), createdByDay.getOrDefault(d, 0L)));
        }
        return new ArrayList<>(ordered.values());
    }
}
