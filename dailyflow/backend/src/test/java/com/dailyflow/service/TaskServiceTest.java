package com.dailyflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dailyflow.domain.ActivityAction;
import com.dailyflow.domain.Category;
import com.dailyflow.domain.Priority;
import com.dailyflow.domain.RecurrenceType;
import com.dailyflow.domain.Task;
import com.dailyflow.domain.TaskStatus;
import com.dailyflow.domain.User;
import com.dailyflow.dto.TaskDtos.TaskDto;
import com.dailyflow.dto.TaskDtos.TaskRequest;
import com.dailyflow.exception.FieldValidationException;
import com.dailyflow.exception.NotFoundException;
import com.dailyflow.mapper.TaskMapper;
import com.dailyflow.repository.CategoryRepository;
import com.dailyflow.repository.TaskOccurrenceRepository;
import com.dailyflow.repository.TaskRepository;
import com.dailyflow.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TaskOccurrenceRepository occurrenceRepository;
    @Mock
    private ActivityService activityService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-21T06:00:00Z"), ZoneId.of("UTC"));
    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, categoryRepository, userRepository, occurrenceRepository,
                new TaskMapper(clock), activityService, clock);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static TaskRequest request(String title, LocalDateTime start, LocalDateTime due) {
        return new TaskRequest(title, "details", null, Priority.HIGH, null, start, due,
                RecurrenceType.NONE, null, null);
    }

    private Task existingTask(TaskStatus status) {
        Task task = new Task(new User("a@example.com", "hash", "A"));
        task.setTitle("Existing");
        task.setPriority(Priority.MEDIUM);
        task.setStatus(status);
        if (status == TaskStatus.COMPLETED) {
            task.setCompletedAt(LocalDateTime.of(2026, 10, 20, 9, 0));
        }
        when(taskRepository.findByIdAndUserId(10L, USER)).thenReturn(Optional.of(task));
        return task;
    }

    @Test
    void createPersistsTaskWithDefaultsAndWritesAuditEntry() {
        TaskDto dto = service.create(USER, request("  Write report  ", null, LocalDateTime.of(2026, 10, 25, 10, 0)));

        assertThat(dto.title()).isEqualTo("Write report");
        assertThat(dto.status()).isEqualTo(TaskStatus.TODO);
        assertThat(dto.priority()).isEqualTo(Priority.HIGH);
        assertThat(dto.completedAt()).isNull();
        verify(activityService).record(eq(USER), any(), eq("Write report"), eq(ActivityAction.TASK_CREATED), any());
    }

    @Test
    void createWithCompletedStatusSetsCompletedAt() {
        TaskRequest req = new TaskRequest("Done already", null, null, Priority.LOW, TaskStatus.COMPLETED,
                null, null, RecurrenceType.NONE, null, null);

        TaskDto dto = service.create(USER, req);

        assertThat(dto.completedAt()).isEqualTo(LocalDateTime.of(2026, 10, 21, 6, 0));
    }

    @Test
    void createRejectsDueDateBeforeStartDate() {
        TaskRequest req = request("Bad dates", LocalDateTime.of(2026, 10, 22, 9, 0), LocalDateTime.of(2026, 10, 21, 9, 0));

        assertThatThrownBy(() -> service.create(USER, req))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(ex -> assertThat(((FieldValidationException) ex).getFieldErrors()).containsKey("dueAt"));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void createRejectsRecurringTaskWithoutDueDate() {
        TaskRequest req = new TaskRequest("Daily", null, null, Priority.LOW, null, null, null,
                RecurrenceType.DAILY, null, null);

        assertThatThrownBy(() -> service.create(USER, req))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(ex -> assertThat(((FieldValidationException) ex).getFieldErrors()).containsKey("dueAt"));
    }

    @Test
    void createRejectsCustomRecurrenceWithoutInterval() {
        TaskRequest req = new TaskRequest("Custom", null, null, Priority.LOW, null, null,
                LocalDateTime.of(2026, 10, 25, 9, 0), RecurrenceType.CUSTOM, null, null);

        assertThatThrownBy(() -> service.create(USER, req))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(ex -> assertThat(((FieldValidationException) ex).getFieldErrors())
                        .containsKey("recurrenceInterval"));
    }

    @Test
    void createRejectsCategoryOfAnotherUser() {
        when(categoryRepository.findByIdAndUserId(99L, USER)).thenReturn(Optional.empty());
        TaskRequest req = new TaskRequest("Task", null, 99L, Priority.LOW, null, null, null,
                RecurrenceType.NONE, null, null);

        assertThatThrownBy(() -> service.create(USER, req))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(ex -> assertThat(((FieldValidationException) ex).getFieldErrors())
                        .containsKey("categoryId"));
    }

    @Test
    void createNormalizesWeeklyRecurrenceToSevenDays() {
        TaskRequest req = new TaskRequest("Weekly review", null, null, Priority.MEDIUM, null, null,
                LocalDateTime.of(2026, 10, 25, 9, 0), RecurrenceType.WEEKLY, 99, LocalDate.of(2026, 12, 31));

        TaskDto dto = service.create(USER, req);

        assertThat(dto.recurrenceType()).isEqualTo(RecurrenceType.WEEKLY);
        assertThat(dto.recurrenceInterval()).isEqualTo(7);
        assertThat(dto.recurrenceEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void getReturnsNotFoundForTaskOfAnotherUser() {
        existingTask(TaskStatus.TODO); // stubbed only for USER

        assertThatThrownBy(() -> service.get(OTHER_USER, 10L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteOfAnotherUsersTaskIsNotFoundAndDeletesNothing() {
        existingTask(TaskStatus.TODO);

        assertThatThrownBy(() -> service.delete(OTHER_USER, 10L)).isInstanceOf(NotFoundException.class);
        verify(taskRepository, never()).delete(any(Task.class));
    }

    @Test
    void deleteRemovesTaskAndKeepsAuditTrail() {
        Task task = existingTask(TaskStatus.TODO);

        service.delete(USER, 10L);

        verify(activityService).record(eq(USER), any(), eq("Existing"), eq(ActivityAction.TASK_DELETED), any());
        verify(taskRepository).delete(task);
    }

    @Test
    void completingATaskSetsCompletedAtAndLogsCompletion() {
        Task task = existingTask(TaskStatus.IN_PROGRESS);

        TaskDto dto = service.changeStatus(USER, 10L, TaskStatus.COMPLETED);

        assertThat(dto.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 10, 21, 6, 0));
        verify(activityService).record(eq(USER), any(), any(), eq(ActivityAction.TASK_COMPLETED), any());
    }

    @Test
    void reopeningACompletedTaskClearsCompletedAt() {
        Task task = existingTask(TaskStatus.COMPLETED);

        TaskDto dto = service.reopen(USER, 10L);

        assertThat(dto.status()).isEqualTo(TaskStatus.TODO);
        assertThat(task.getCompletedAt()).isNull();
        verify(activityService).record(eq(USER), any(), any(), eq(ActivityAction.TASK_REOPENED), any());
    }

    @Test
    void sameStatusChangeIsIdempotentAndWritesNoAudit() {
        existingTask(TaskStatus.COMPLETED);

        service.changeStatus(USER, 10L, TaskStatus.COMPLETED);

        verify(activityService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void updateKeepsUnchangedValuesAndRecordsOnlyRealChanges() {
        Task task = existingTask(TaskStatus.TODO);
        task.setDescription("details");
        TaskRequest req = new TaskRequest("Existing", "details", null, Priority.HIGH, null, null, null,
                RecurrenceType.NONE, null, null);

        TaskDto dto = service.update(USER, 10L, req);

        assertThat(dto.priority()).isEqualTo(Priority.HIGH);
        assertThat(dto.status()).isEqualTo(TaskStatus.TODO); // null status in the request keeps the current one
        verify(activityService).record(eq(USER), any(), eq("Existing"), eq(ActivityAction.TASK_UPDATED),
                eq("priority: MEDIUM → HIGH"));
    }

    @Test
    void updateWithoutChangesWritesNoAudit() {
        Task task = existingTask(TaskStatus.TODO);
        task.setPriority(Priority.HIGH);
        TaskRequest req = new TaskRequest("Existing", null, null, Priority.HIGH, null, null, null,
                RecurrenceType.NONE, null, null);

        service.update(USER, 10L, req);

        verify(activityService, never()).record(any(), any(), any(), any(), any());
    }
}
