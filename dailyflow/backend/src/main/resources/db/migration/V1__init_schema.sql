-- DailyFlow initial schema (MySQL 8+, also runs on H2 in MySQL mode for tests)

CREATE TABLE users (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    email                   VARCHAR(190) NOT NULL,
    password_hash           VARCHAR(100) NOT NULL,
    display_name            VARCHAR(100) NOT NULL,
    email_reminders_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    reminder_lead_minutes   INT          NOT NULL DEFAULT 60,
    token_version           INT          NOT NULL DEFAULT 0,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE categories (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    name        VARCHAR(60) NOT NULL,
    color       VARCHAR(7)  NOT NULL,
    icon        VARCHAR(40) NOT NULL,
    is_default  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE tasks (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    user_id              BIGINT        NOT NULL,
    category_id          BIGINT        NULL,
    title                VARCHAR(200)  NOT NULL,
    description          VARCHAR(5000) NULL,
    priority             VARCHAR(10)   NOT NULL,
    priority_rank        INT           NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    start_at             DATETIME(6)   NULL,
    due_at               DATETIME(6)   NULL,
    -- due_at, or 9999-12-31 when there is no due date, so "no due date" always sorts last (ascending).
    due_sort_at          DATETIME(6)   NOT NULL,
    completed_at         DATETIME(6)   NULL,
    recurrence_type      VARCHAR(10)   NOT NULL DEFAULT 'NONE',
    recurrence_interval  INT           NULL,
    recurrence_end_date  DATE          NULL,
    reminder_sent_at     DATETIME(6)   NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_tasks_recurrence CHECK (recurrence_type IN ('NONE', 'DAILY', 'WEEKLY', 'CUSTOM')),
    CONSTRAINT ck_tasks_dates CHECK (start_at IS NULL OR due_at IS NULL OR due_at >= start_at)
);

CREATE INDEX idx_tasks_user_status    ON tasks (user_id, status);
CREATE INDEX idx_tasks_user_due       ON tasks (user_id, due_at);
CREATE INDEX idx_tasks_user_due_sort  ON tasks (user_id, due_sort_at);
CREATE INDEX idx_tasks_user_category  ON tasks (user_id, category_id);
CREATE INDEX idx_tasks_user_created   ON tasks (user_id, created_at);
CREATE INDEX idx_tasks_user_updated   ON tasks (user_id, updated_at);
CREATE INDEX idx_tasks_user_completed ON tasks (user_id, completed_at);
CREATE INDEX idx_tasks_category       ON tasks (category_id);

-- Exceptions to a recurrence rule. A row exists ONLY when an occurrence was completed,
-- skipped or rescheduled. Occurrences that nobody touched are computed from the rule.
CREATE TABLE task_occurrences (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    task_id         BIGINT      NOT NULL,
    occurrence_date DATE        NOT NULL,
    status          VARCHAR(20) NOT NULL,
    rescheduled_to  DATE        NULL,
    completed_at    DATETIME(6) NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    CONSTRAINT pk_task_occurrences PRIMARY KEY (id),
    CONSTRAINT uk_task_occurrence UNIQUE (task_id, occurrence_date),
    CONSTRAINT fk_occurrences_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_occurrence_status CHECK (status IN ('COMPLETED', 'SKIPPED', 'RESCHEDULED'))
);

CREATE INDEX idx_occurrences_resched ON task_occurrences (task_id, rescheduled_to);

-- Audit trail. task_id deliberately has NO foreign key so history survives task deletion.
-- Rows are removed only when the owning user is deleted.
CREATE TABLE task_activities (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    user_id     BIGINT        NOT NULL,
    task_id     BIGINT        NULL,
    task_title  VARCHAR(200)  NULL,
    action      VARCHAR(30)   NOT NULL,
    summary     VARCHAR(1000) NULL,
    created_at  DATETIME(6)   NOT NULL,
    CONSTRAINT pk_task_activities PRIMARY KEY (id),
    CONSTRAINT fk_activities_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_activities_user_created ON task_activities (user_id, created_at);
CREATE INDEX idx_activities_user_task    ON task_activities (user_id, task_id);
