package com.dailyflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Append-only audit record. taskId has no foreign key on purpose: the history of a deleted
 * task is retained (with a title snapshot) until the owning user account is deleted.
 */
@Entity
@Table(name = "task_activities")
public class TaskActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "task_id", updatable = false)
    private Long taskId;

    @Column(name = "task_title", length = 200, updatable = false)
    private String taskTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private ActivityAction action;

    @Column(length = 1000, updatable = false)
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TaskActivity() {
    }

    public TaskActivity(Long userId, Long taskId, String taskTitle, ActivityAction action,
                        String summary, LocalDateTime createdAt) {
        this.userId = userId;
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.action = action;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public ActivityAction getAction() {
        return action;
    }

    public String getSummary() {
        return summary;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
