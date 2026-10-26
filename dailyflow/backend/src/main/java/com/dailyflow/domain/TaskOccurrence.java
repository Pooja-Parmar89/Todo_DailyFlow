package com.dailyflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Exception row for one occurrence of a recurring task. Unique per (task, occurrence date). */
@Entity
@Table(name = "task_occurrences")
public class TaskOccurrence extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, updatable = false)
    private Long taskId;

    @Column(name = "occurrence_date", nullable = false, updatable = false)
    private LocalDate occurrenceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OccurrenceStatus status;

    @Column(name = "rescheduled_to")
    private LocalDate rescheduledTo;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected TaskOccurrence() {
    }

    public TaskOccurrence(Long taskId, LocalDate occurrenceDate) {
        this.taskId = taskId;
        this.occurrenceDate = occurrenceDate;
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public OccurrenceStatus getStatus() {
        return status;
    }

    public void setStatus(OccurrenceStatus status) {
        this.status = status;
    }

    public LocalDate getRescheduledTo() {
        return rescheduledTo;
    }

    public void setRescheduledTo(LocalDate rescheduledTo) {
        this.rescheduledTo = rescheduledTo;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
