package com.dailyflow.service;

import com.dailyflow.domain.ActivityAction;
import com.dailyflow.domain.TaskActivity;
import com.dailyflow.dto.CommonDtos.ActivityDto;
import com.dailyflow.dto.CommonDtos.PageResponse;
import com.dailyflow.mapper.TaskMapper;
import com.dailyflow.repository.TaskActivityRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit / activity history.
 * Retention policy: records are append-only and survive deletion of the task they refer to
 * (a title snapshot is kept). They are removed only when the user account is deleted (FK cascade).
 * Never pass passwords, tokens or other authentication data into {@code summary}.
 */
@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final TaskActivityRepository repository;
    private final TaskMapper mapper;
    private final Clock clock;

    public ActivityService(TaskActivityRepository repository, TaskMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public void record(Long userId, Long taskId, String taskTitle, ActivityAction action, String summary) {
        repository.save(new TaskActivity(userId, taskId, truncate(taskTitle, 200), action,
                truncate(summary, 1000), LocalDateTime.now(clock)));
        log.info("audit user={} action={} task={}", userId, action, taskId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityDto> list(Long userId, Long taskId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<TaskActivity> result = taskId == null
                ? repository.findByUserId(userId, pageable)
                : repository.findByUserIdAndTaskId(userId, taskId, pageable);
        return PageResponse.from(result.map(mapper::toDto));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
