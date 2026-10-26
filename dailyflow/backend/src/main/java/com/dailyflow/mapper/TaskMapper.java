package com.dailyflow.mapper;

import com.dailyflow.domain.ActivityAction;
import com.dailyflow.domain.Category;
import com.dailyflow.domain.Task;
import com.dailyflow.domain.TaskActivity;
import com.dailyflow.domain.TaskStatus;
import com.dailyflow.domain.User;
import com.dailyflow.dto.AuthDtos.UserDto;
import com.dailyflow.dto.CategoryDtos.CategoryDto;
import com.dailyflow.dto.CategoryDtos.CategorySummary;
import com.dailyflow.dto.CommonDtos.ActivityDto;
import com.dailyflow.dto.TaskDtos.TaskDto;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** Entity to DTO mapping. Entities are never serialized directly. */
@Component
public class TaskMapper {

    private final Clock clock;

    public TaskMapper(Clock clock) {
        this.clock = clock;
    }

    public TaskDto toDto(Task task) {
        return new TaskDto(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                toSummary(task.getCategory()),
                task.getPriority(),
                task.getStatus(),
                task.getStartAt(),
                task.getDueAt(),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                isOverdue(task),
                task.getRecurrenceType(),
                task.getRecurrenceInterval(),
                task.getRecurrenceEndDate());
    }

    /**
     * Overdue rule: a one-off task that is not completed and whose due date/time has passed.
     * Recurring series are judged per occurrence on the calendar instead.
     */
    public boolean isOverdue(Task task) {
        return !task.isRecurring()
                && task.getStatus() != TaskStatus.COMPLETED
                && task.getDueAt() != null
                && task.getDueAt().isBefore(LocalDateTime.now(clock));
    }

    public CategorySummary toSummary(Category category) {
        if (category == null) {
            return null;
        }
        return new CategorySummary(category.getId(), category.getName(), category.getColor(), category.getIcon());
    }

    public CategoryDto toDto(Category category, long taskCount) {
        return new CategoryDto(category.getId(), category.getName(), category.getColor(), category.getIcon(),
                category.isDefaultCategory(), taskCount);
    }

    public UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getDisplayName(),
                user.isEmailRemindersEnabled(), user.getReminderLeadMinutes());
    }

    public ActivityDto toDto(TaskActivity activity) {
        ActivityAction action = activity.getAction();
        return new ActivityDto(activity.getId(), activity.getTaskId(), activity.getTaskTitle(),
                action.name(), activity.getSummary(), activity.getCreatedAt());
    }
}
