package com.dailyflow.service;

import com.dailyflow.domain.TaskStatus;
import com.dailyflow.domain.User;
import com.dailyflow.dto.InsightDtos.CalendarItem;
import com.dailyflow.dto.InsightDtos.ReminderSummary;
import com.dailyflow.exception.NotFoundException;
import com.dailyflow.repository.TaskRepository;
import com.dailyflow.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app reminders. This is a pull model: the browser asks while the app is open. Nothing is delivered
 * when the backend is down or the page is closed (see README, "Reminders and notifications").
 */
@Service
public class ReminderService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final CalendarService calendarService;
    private final Clock clock;

    public ReminderService(UserRepository userRepository, TaskRepository taskRepository,
                           CalendarService calendarService, Clock clock) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.calendarService = calendarService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReminderSummary summary(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime until = now.plusMinutes(user.getReminderLeadMinutes());

        List<CalendarItem> dueSoon = calendarService.items(userId, now.toLocalDate(), until.toLocalDate()).stream()
                .filter(i -> i.status() != TaskStatus.COMPLETED)
                .filter(i -> !"SKIPPED".equals(i.occurrenceStatus()))
                .filter(i -> !i.dueAt().isBefore(now) && !i.dueAt().isAfter(until))
                .toList();

        return new ReminderSummary(taskRepository.countOverdue(userId, now), dueSoon, user.getReminderLeadMinutes());
    }
}
