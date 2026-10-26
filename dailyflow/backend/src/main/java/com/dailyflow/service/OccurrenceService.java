package com.dailyflow.service;

import com.dailyflow.domain.ActivityAction;
import com.dailyflow.domain.OccurrenceStatus;
import com.dailyflow.domain.Task;
import com.dailyflow.domain.TaskOccurrence;
import com.dailyflow.dto.InsightDtos.OccurrenceView;
import com.dailyflow.exception.BadRequestException;
import com.dailyflow.exception.FieldValidationException;
import com.dailyflow.exception.NotFoundException;
import com.dailyflow.repository.TaskOccurrenceRepository;
import com.dailyflow.repository.TaskRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Complete / skip / reschedule / reset a single occurrence of a recurring task.
 * Every operation is an upsert on the unique key (task_id, occurrence_date), so repeating a request
 * (for example after a network retry) rewrites the same row instead of creating another one. If two
 * identical requests race, the unique constraint rejects the loser with HTTP 409 and a retry succeeds.
 */
@Service
public class OccurrenceService {

    private final TaskRepository taskRepository;
    private final TaskOccurrenceRepository occurrenceRepository;
    private final RecurrenceService recurrenceService;
    private final ActivityService activityService;
    private final Clock clock;

    public OccurrenceService(TaskRepository taskRepository, TaskOccurrenceRepository occurrenceRepository,
                             RecurrenceService recurrenceService, ActivityService activityService, Clock clock) {
        this.taskRepository = taskRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.recurrenceService = recurrenceService;
        this.activityService = activityService;
        this.clock = clock;
    }

    @Transactional
    public OccurrenceView complete(Long userId, Long taskId, LocalDate date) {
        Task task = loadRecurring(userId, taskId, date);
        TaskOccurrence occurrence = findOrNew(taskId, date);
        occurrence.setStatus(OccurrenceStatus.COMPLETED);
        occurrence.setRescheduledTo(null);
        occurrence.setCompletedAt(LocalDateTime.now(clock));
        occurrenceRepository.save(occurrence);
        activityService.record(userId, taskId, task.getTitle(), ActivityAction.OCCURRENCE_COMPLETED,
                "Occurrence " + date + " completed");
        return view(occurrence);
    }

    @Transactional
    public OccurrenceView skip(Long userId, Long taskId, LocalDate date) {
        Task task = loadRecurring(userId, taskId, date);
        TaskOccurrence occurrence = findOrNew(taskId, date);
        occurrence.setStatus(OccurrenceStatus.SKIPPED);
        occurrence.setRescheduledTo(null);
        occurrence.setCompletedAt(null);
        occurrenceRepository.save(occurrence);
        activityService.record(userId, taskId, task.getTitle(), ActivityAction.OCCURRENCE_SKIPPED,
                "Occurrence " + date + " skipped");
        return view(occurrence);
    }

    @Transactional
    public OccurrenceView reschedule(Long userId, Long taskId, LocalDate date, LocalDate newDate) {
        Task task = loadRecurring(userId, taskId, date);
        if (newDate.equals(date)) {
            throw new FieldValidationException("newDate", "Choose a different date than the original one");
        }
        TaskOccurrence occurrence = findOrNew(taskId, date);
        occurrence.setStatus(OccurrenceStatus.RESCHEDULED);
        occurrence.setRescheduledTo(newDate);
        occurrence.setCompletedAt(null);
        occurrenceRepository.save(occurrence);
        activityService.record(userId, taskId, task.getTitle(), ActivityAction.OCCURRENCE_RESCHEDULED,
                "Occurrence " + date + " moved to " + newDate);
        return view(occurrence);
    }

    /** Back to the normal scheduled state (removes the exception row; no-op if there is none). */
    @Transactional
    public void reset(Long userId, Long taskId, LocalDate date) {
        Task task = loadRecurring(userId, taskId, date);
        occurrenceRepository.findByTaskIdAndOccurrenceDate(taskId, date).ifPresent(existing -> {
            occurrenceRepository.delete(existing);
            activityService.record(userId, taskId, task.getTitle(), ActivityAction.OCCURRENCE_RESET,
                    "Occurrence " + date + " restored");
        });
    }

    private Task loadRecurring(Long userId, Long taskId, LocalDate date) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (!task.isRecurring()) {
            throw new BadRequestException("This task is not a recurring task");
        }
        if (!recurrenceService.isOccurrenceDate(task, date)) {
            throw new FieldValidationException("date", "The task has no occurrence on " + date);
        }
        return task;
    }

    private TaskOccurrence findOrNew(Long taskId, LocalDate date) {
        return occurrenceRepository.findByTaskIdAndOccurrenceDate(taskId, date)
                .orElseGet(() -> new TaskOccurrence(taskId, date));
    }

    private OccurrenceView view(TaskOccurrence o) {
        return new OccurrenceView(o.getTaskId(), o.getOccurrenceDate(), o.getStatus(), o.getRescheduledTo());
    }
}
