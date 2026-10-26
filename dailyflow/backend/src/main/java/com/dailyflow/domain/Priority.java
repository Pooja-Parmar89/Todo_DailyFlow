package com.dailyflow.domain;

public enum Priority {
    HIGH(1), MEDIUM(2), LOW(3);

    private final int rank;

    Priority(int rank) {
        this.rank = rank;
    }

    /** 1 = most important. Stored in tasks.priority_rank so priority sorts correctly. */
    public int rank() {
        return rank;
    }
}
