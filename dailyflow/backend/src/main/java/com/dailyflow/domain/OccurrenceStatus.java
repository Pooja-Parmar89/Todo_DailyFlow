package com.dailyflow.domain;

/** Stored exception states of a recurring occurrence. Untouched occurrences have no row (they are PENDING). */
public enum OccurrenceStatus {
    COMPLETED, SKIPPED, RESCHEDULED
}
