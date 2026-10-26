package com.dailyflow.web;

import com.dailyflow.domain.Priority;
import com.dailyflow.domain.TaskStatus;
import com.dailyflow.dto.CommonDtos.PageResponse;
import com.dailyflow.dto.InsightDtos.OccurrenceView;
import com.dailyflow.dto.TaskDtos.RescheduleRequest;
import com.dailyflow.dto.TaskDtos.StatusRequest;
import com.dailyflow.dto.TaskDtos.TaskDto;
import com.dailyflow.dto.TaskDtos.TaskFilter;
import com.dailyflow.dto.TaskDtos.TaskRequest;
import com.dailyflow.security.AppUserDetails;
import com.dailyflow.service.OccurrenceService;
import com.dailyflow.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks")
public class TaskController {

    private final TaskService taskService;
    private final OccurrenceService occurrenceService;

    public TaskController(TaskService taskService, OccurrenceService occurrenceService) {
        this.taskService = taskService;
        this.occurrenceService = occurrenceService;
    }

    @GetMapping
    @Operation(summary = "Search, filter, sort and page through the caller's tasks",
            description = "sort: dueAt (default), createdAt, updatedAt, priority (ascending = HIGH first), title, status. "
                    + "priority and status accept comma separated values. dueFrom/dueTo are inclusive dates. "
                    + "overdue=true returns incomplete one-off tasks whose due time has passed.")
    public PageResponse<TaskDto> list(
            @AuthenticationPrincipal AppUserDetails me,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Priority> priority,
            @RequestParam(required = false) List<TaskStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dueAt") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        TaskFilter filter = new TaskFilter(q, categoryId, priority, status, dueFrom, dueTo, overdue);
        return taskService.search(me.getId(), filter, page, size, sort, direction);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDto create(@AuthenticationPrincipal AppUserDetails me, @Valid @RequestBody TaskRequest request) {
        return taskService.create(me.getId(), request);
    }

    @GetMapping("/{id}")
    public TaskDto get(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        return taskService.get(me.getId(), id);
    }

    @PutMapping("/{id}")
    public TaskDto update(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                          @Valid @RequestBody TaskRequest request) {
        return taskService.update(me.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        taskService.delete(me.getId(), id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move a task between TODO, IN_PROGRESS and COMPLETED")
    public TaskDto changeStatus(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                                @Valid @RequestBody StatusRequest request) {
        return taskService.changeStatus(me.getId(), id, request.status());
    }

    @PostMapping("/{id}/complete")
    public TaskDto complete(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        return taskService.changeStatus(me.getId(), id, TaskStatus.COMPLETED);
    }

    @PostMapping("/{id}/reopen")
    public TaskDto reopen(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        return taskService.reopen(me.getId(), id);
    }

    // ---- recurring task occurrences (date is the occurrence's original date, yyyy-MM-dd) ----

    @PostMapping("/{id}/occurrences/{date}/complete")
    @Operation(summary = "Complete one occurrence of a recurring task (idempotent)")
    public OccurrenceView completeOccurrence(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                                             @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return occurrenceService.complete(me.getId(), id, date);
    }

    @PostMapping("/{id}/occurrences/{date}/skip")
    @Operation(summary = "Skip one occurrence of a recurring task (idempotent)")
    public OccurrenceView skipOccurrence(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                                         @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return occurrenceService.skip(me.getId(), id, date);
    }

    @PostMapping("/{id}/occurrences/{date}/reschedule")
    @Operation(summary = "Move one occurrence of a recurring task to another date (idempotent)")
    public OccurrenceView rescheduleOccurrence(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                                               @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                               @Valid @RequestBody RescheduleRequest request) {
        return occurrenceService.reschedule(me.getId(), id, date, request.newDate());
    }

    @DeleteMapping("/{id}/occurrences/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Restore an occurrence to its normal scheduled state")
    public void resetOccurrence(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                                @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        occurrenceService.reset(me.getId(), id, date);
    }
}
