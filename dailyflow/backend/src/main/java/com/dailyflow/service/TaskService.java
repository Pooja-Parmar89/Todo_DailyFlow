package com.dailyflow.service;

import com.dailyflow.domain.ActivityAction;
import com.dailyflow.domain.Category;
import com.dailyflow.domain.RecurrenceType;
import com.dailyflow.domain.Task;
import com.dailyflow.domain.TaskStatus;
import com.dailyflow.dto.CommonDtos.PageResponse;
import com.dailyflow.dto.TaskDtos.TaskDto;
import com.dailyflow.dto.TaskDtos.TaskFilter;
import com.dailyflow.dto.TaskDtos.TaskRequest;
import com.dailyflow.exception.BadRequestException;
import com.dailyflow.exception.FieldValidationException;
import com.dailyflow.exception.NotFoundException;
import com.dailyflow.mapper.TaskMapper;
import com.dailyflow.repository.CategoryRepository;
import com.dailyflow.repository.TaskOccurrenceRepository;
import com.dailyflow.repository.TaskRepository;
import com.dailyflow.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    /** Public sort keys -> entity properties. "priority" sorts by rank, so ascending = HIGH first. */
    private static final Map<String, String> SORT_PROPERTIES = Map.of(
            "dueAt", "dueSortAt",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "priority", "priorityRank",
            "title", "title",
            "status", "status");

    private final TaskRepository taskRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TaskOccurrenceRepository occurrenceRepository;
    private final TaskMapper mapper;
    private final ActivityService activityService;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, CategoryRepository categoryRepository,
                       UserRepository userRepository, TaskOccurrenceRepository occurrenceRepository,
                       TaskMapper mapper, ActivityService activityService, Clock clock) {
        this.taskRepository = taskRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.mapper = mapper;
        this.activityService = activityService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public PageResponse<TaskDto> search(Long userId, TaskFilter filter, int page, int size,
                                        String sortBy, String direction) {
        String key = sortBy == null || sortBy.isBlank() ? "dueAt" : sortBy;
        String property = SORT_PROPERTIES.get(key);
        if (property == null) {
            throw new BadRequestException("Unsupported sort field '" + key + "'. Use one of: "
                    + String.join(", ", SORT_PROPERTIES.keySet().stream().sorted().toList()));
        }
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(dir, property).and(Sort.by(Sort.Direction.ASC, "id"));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);

        Page<Task> result = taskRepository.findAll(
                TaskSpecifications.matching(userId, filter, LocalDateTime.now(clock)), pageable);
        return PageResponse.from(result.map(mapper::toDto));
    }

    @Transactional(readOnly = true)
    public TaskDto get(Long userId, Long id) {
        return mapper.toDto(loadOwned(userId, id));
    }

    // --------------------------------------------------------------- write

    @Transactional
    public TaskDto create(Long userId, TaskRequest request) {
        Category category = validate(userId, request);
        Task task = new Task(userRepository.getReferenceById(userId));
        task.setTitle(request.title().trim());
        task.setDescription(blankToNull(request.description()));
        task.setCategory(category);
        task.setPriority(request.priority());
        task.setStartAt(request.startAt());
        task.setDueAt(request.dueAt());
        applyRecurrence(task, request);
        applyStatus(task, request.status() == null ? TaskStatus.TODO : request.status());

        Task saved = taskRepository.save(task);
        activityService.record(userId, saved.getId(), saved.getTitle(), ActivityAction.TASK_CREATED,
                "Created with priority " + saved.getPriority()
                        + (saved.getDueAt() != null ? ", due " + saved.getDueAt() : ""));
        return mapper.toDto(saved);
    }

    @Transactional
    public TaskDto update(Long userId, Long id, TaskRequest request) {
        Task task = loadOwned(userId, id);
        Category category = validate(userId, request);

        TaskStatus oldStatus = task.getStatus();
        boolean wasRecurring = task.isRecurring();
        RecurrenceType oldType = task.getRecurrenceType();
        Integer oldInterval = task.getRecurrenceInterval();
        LocalDate oldEnd = task.getRecurrenceEndDate();
        LocalDateTime oldDue = task.getDueAt();

        String newTitle = request.title().trim();
        String newDescription = blankToNull(request.description());
        TaskStatus newStatus = request.status() == null ? oldStatus : request.status();

        List<String> changes = new ArrayList<>();
        diff(changes, "title", task.getTitle(), newTitle);
        if (!Objects.equals(task.getDescription(), newDescription)) {
            changes.add("description changed");
        }
        diff(changes, "category", task.getCategory() == null ? null : task.getCategory().getName(),
                category == null ? null : category.getName());
        diff(changes, "priority", task.getPriority(), request.priority());
        diff(changes, "status", oldStatus, newStatus);
        diff(changes, "start", task.getStartAt(), request.startAt());
        diff(changes, "due", oldDue, request.dueAt());

        task.setTitle(newTitle);
        task.setDescription(newDescription);
        task.setCategory(category);
        task.setPriority(request.priority());
        task.setStartAt(request.startAt());
        task.setDueAt(request.dueAt());
        applyRecurrence(task, request);
        applyStatus(task, newStatus);

        if (!Objects.equals(oldType, task.getRecurrenceType())
                || !Objects.equals(oldInterval, task.getRecurrenceInterval())
                || !Objects.equals(oldEnd, task.getRecurrenceEndDate())) {
            changes.add("recurrence: " + describeRecurrence(oldType, oldInterval)
                    + " → " + describeRecurrence(task.getRecurrenceType(), task.getRecurrenceInterval()));
        }
        // Moving the schedule invalidates stored exceptions (they were tied to the old dates).
        boolean scheduleMoved = wasRecurring && (!Objects.equals(oldType, task.getRecurrenceType())
                || !Objects.equals(oldInterval, task.getRecurrenceInterval())
                || !Objects.equals(oldDue == null ? null : oldDue.toLocalDate(),
                task.getDueAt() == null ? null : task.getDueAt().toLocalDate()));
        if (scheduleMoved) {
            occurrenceRepository.deleteAllByTaskId(task.getId());
        }
        if (!Objects.equals(oldDue, task.getDueAt())) {
            task.setReminderSentAt(null);
        }

        if (!changes.isEmpty()) {
            activityService.record(userId, task.getId(), task.getTitle(), actionFor(oldStatus, newStatus),
                    String.join("; ", changes));
        }
        return mapper.toDto(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public TaskDto changeStatus(Long userId, Long id, TaskStatus newStatus) {
        Task task = loadOwned(userId, id);
        TaskStatus oldStatus = task.getStatus();
        if (oldStatus == newStatus) {
            return mapper.toDto(task); // idempotent: a retried request changes nothing and logs nothing
        }
        applyStatus(task, newStatus);
        activityService.record(userId, task.getId(), task.getTitle(), actionFor(oldStatus, newStatus),
                "status: " + oldStatus + " → " + newStatus);
        return mapper.toDto(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public TaskDto reopen(Long userId, Long id) {
        Task task = loadOwned(userId, id);
        if (task.getStatus() != TaskStatus.COMPLETED) {
            return mapper.toDto(task);
        }
        return changeStatus(userId, id, TaskStatus.TODO);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Task task = loadOwned(userId, id);
        // The audit row keeps a title snapshot; occurrences are removed by the FK cascade.
        activityService.record(userId, task.getId(), task.getTitle(), ActivityAction.TASK_DELETED,
                "Deleted (status was " + task.getStatus() + ")");
        taskRepository.delete(task);
    }

    // ------------------------------------------------------------- helpers

    /** Ownership check: a task that belongs to someone else is reported as not found (no id probing). */
    Task loadOwned(Long userId, Long id) {
        return taskRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
    }

    private Category validate(Long userId, TaskRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request.startAt() != null && request.dueAt() != null && request.dueAt().isBefore(request.startAt())) {
            errors.put("dueAt", "Due date must not be before the start date");
        }
        RecurrenceType type = request.recurrenceType() == null ? RecurrenceType.NONE : request.recurrenceType();
        if (type != RecurrenceType.NONE) {
            if (request.dueAt() == null) {
                errors.put("dueAt", "Recurring tasks need a due date/time to anchor the schedule");
            }
            if (type == RecurrenceType.CUSTOM && request.recurrenceInterval() == null) {
                errors.put("recurrenceInterval", "Enter how many days to wait between repeats");
            }
            if (request.recurrenceEndDate() != null && request.dueAt() != null
                    && request.recurrenceEndDate().isBefore(request.dueAt().toLocalDate())) {
                errors.put("recurrenceEndDate", "End date must not be before the first due date");
            }
        }
        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findByIdAndUserId(request.categoryId(), userId).orElse(null);
            if (category == null) {
                errors.put("categoryId", "Category not found");
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return category;
    }

    private void applyRecurrence(Task task, TaskRequest request) {
        RecurrenceType type = request.recurrenceType() == null ? RecurrenceType.NONE : request.recurrenceType();
        task.setRecurrenceType(type);
        switch (type) {
            case NONE -> {
                task.setRecurrenceInterval(null);
                task.setRecurrenceEndDate(null);
            }
            case DAILY -> {
                task.setRecurrenceInterval(1);
                task.setRecurrenceEndDate(request.recurrenceEndDate());
            }
            case WEEKLY -> {
                task.setRecurrenceInterval(7);
                task.setRecurrenceEndDate(request.recurrenceEndDate());
            }
            case CUSTOM -> {
                task.setRecurrenceInterval(request.recurrenceInterval());
                task.setRecurrenceEndDate(request.recurrenceEndDate());
            }
        }
    }

    /** completedAt is set when entering COMPLETED and cleared when leaving it. */
    private void applyStatus(Task task, TaskStatus newStatus) {
        TaskStatus old = task.getStatus();
        task.setStatus(newStatus);
        if (newStatus == TaskStatus.COMPLETED) {
            if (old != TaskStatus.COMPLETED || task.getCompletedAt() == null) {
                task.setCompletedAt(LocalDateTime.now(clock));
            }
        } else {
            task.setCompletedAt(null);
        }
    }

    private static ActivityAction actionFor(TaskStatus oldStatus, TaskStatus newStatus) {
        if (oldStatus != TaskStatus.COMPLETED && newStatus == TaskStatus.COMPLETED) {
            return ActivityAction.TASK_COMPLETED;
        }
        if (oldStatus == TaskStatus.COMPLETED && newStatus != TaskStatus.COMPLETED) {
            return ActivityAction.TASK_REOPENED;
        }
        if (oldStatus != newStatus) {
            return ActivityAction.TASK_STATUS_CHANGED;
        }
        return ActivityAction.TASK_UPDATED;
    }

    private static void diff(List<String> changes, String label, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(label + ": " + show(oldValue) + " → " + show(newValue));
        }
    }

    private static String show(Object value) {
        if (value == null) {
            return "none";
        }
        String text = value.toString();
        return text.length() > 60 ? text.substring(0, 59) + "…" : text;
    }

    private static String describeRecurrence(RecurrenceType type, Integer interval) {
        return type == RecurrenceType.NONE || type == null ? "none" : type + "/" + interval + "d";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
