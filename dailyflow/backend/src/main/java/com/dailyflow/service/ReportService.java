package com.dailyflow.service;

import com.dailyflow.dto.InsightDtos.DailyCount;
import com.dailyflow.dto.InsightDtos.NamedCount;
import com.dailyflow.dto.InsightDtos.ProductivityReport;
import com.dailyflow.repository.TaskRepository;
import com.dailyflow.repository.TaskRepository.CompletionRow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Productivity report over the last N days (7 to 90, default 30), all based on completedAt / createdAt.
 * The current streak is the number of consecutive days, ending today (or yesterday if nothing was completed
 * yet today), on which at least one task was completed.
 */
@Service
public class ReportService {

    private static final String UNCATEGORIZED = "Uncategorized";

    private final TaskRepository taskRepository;
    private final DashboardService dashboardService;
    private final Clock clock;

    public ReportService(TaskRepository taskRepository, DashboardService dashboardService, Clock clock) {
        this.taskRepository = taskRepository;
        this.dashboardService = dashboardService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProductivityReport report(Long userId, int days) {
        int window = Math.min(Math.max(days, 7), 90);
        LocalDate to = LocalDateTime.now(clock).toLocalDate();
        LocalDate from = to.minusDays(window - 1L);

        List<DailyCount> daily = dashboardService.dailyCounts(userId, from, to);
        List<CompletionRow> completions = taskRepository.findCompletionsSince(userId, from.atStartOfDay());

        long totalCompleted = daily.stream().mapToLong(DailyCount::completed).sum();
        long totalCreated = daily.stream().mapToLong(DailyCount::created).sum();
        double average = Math.round(totalCompleted * 10.0 / window) / 10.0;

        List<NamedCount> byCategory = count(completions.stream()
                .map(r -> r.getCategoryName() == null ? UNCATEGORIZED : r.getCategoryName()));
        List<NamedCount> byPriority = count(completions.stream().map(r -> r.getPriority().name()));

        return new ProductivityReport(from, to, totalCompleted, totalCreated, average, streak(daily),
                daily, byCategory, byPriority);
    }

    private static int streak(List<DailyCount> daily) {
        int index = daily.size() - 1;
        if (index >= 0 && daily.get(index).completed() == 0) {
            index--; // today has nothing yet: the streak may still be alive through yesterday
        }
        int streak = 0;
        while (index >= 0 && daily.get(index).completed() > 0) {
            streak++;
            index--;
        }
        return streak;
    }

    private static List<NamedCount> count(java.util.stream.Stream<String> names) {
        Map<String, Long> counts = names.collect(Collectors.groupingBy(n -> n, Collectors.counting()));
        return counts.entrySet().stream()
                .map(e -> new NamedCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(NamedCount::count).reversed().thenComparing(NamedCount::name))
                .toList();
    }
}
