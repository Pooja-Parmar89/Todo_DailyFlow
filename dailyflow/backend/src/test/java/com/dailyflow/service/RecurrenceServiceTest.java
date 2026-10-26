package com.dailyflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyflow.domain.RecurrenceType;
import com.dailyflow.domain.Task;
import com.dailyflow.domain.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecurrenceServiceTest {

    private final RecurrenceService service = new RecurrenceService();

    private Task task(RecurrenceType type, int interval, LocalDate firstDue, LocalDate end) {
        Task t = new Task(new User("a@example.com", "hash", "A"));
        t.setTitle("Recurring");
        t.setDueAt(firstDue.atTime(9, 0));
        t.setRecurrenceType(type);
        t.setRecurrenceInterval(interval);
        t.setRecurrenceEndDate(end);
        return t;
    }

    @Test
    void dailyTaskProducesOneOccurrencePerDay() {
        Task t = task(RecurrenceType.DAILY, 1, LocalDate.of(2026, 10, 20), null);

        List<LocalDate> dates = service.occurrenceDates(t, LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 24));

        assertThat(dates).containsExactly(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22),
                LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24));
    }

    @Test
    void weeklyTaskStaysOnItsWeekdayWhenWindowStartsMidCycle() {
        Task t = task(RecurrenceType.WEEKLY, 7, LocalDate.of(2026, 10, 5), null); // a Monday

        List<LocalDate> dates = service.occurrenceDates(t, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 11, 2));

        assertThat(dates).containsExactly(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2));
    }

    @Test
    void customIntervalIsAlignedToTheAnchorDate() {
        Task t = task(RecurrenceType.CUSTOM, 3, LocalDate.of(2026, 10, 1), null);

        List<LocalDate> dates = service.occurrenceDates(t, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 20));

        assertThat(dates).containsExactly(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 13),
                LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 19));
    }

    @Test
    void endDateLimitsOccurrencesInclusively() {
        Task t = task(RecurrenceType.DAILY, 1, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));

        List<LocalDate> dates = service.occurrenceDates(t, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(dates).containsExactly(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21),
                LocalDate.of(2026, 10, 22));
    }

    @Test
    void windowBeforeTheAnchorHasNoOccurrences() {
        Task t = task(RecurrenceType.DAILY, 1, LocalDate.of(2026, 10, 20), null);

        assertThat(service.occurrenceDates(t, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 19))).isEmpty();
    }

    @Test
    void nonRecurringTaskHasNoOccurrences() {
        Task t = new Task(new User("a@example.com", "hash", "A"));
        t.setDueAt(LocalDateTime.of(2026, 10, 20, 9, 0));

        assertThat(service.occurrenceDates(t, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).isEmpty();
        assertThat(service.isOccurrenceDate(t, LocalDate.of(2026, 10, 20))).isFalse();
    }

    @Test
    void repeatedExpansionIsDeterministicAndHasNoDuplicates() {
        Task t = task(RecurrenceType.DAILY, 1, LocalDate.of(2026, 10, 1), null);
        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate to = LocalDate.of(2026, 10, 30);

        List<LocalDate> first = service.occurrenceDates(t, from, to);
        List<LocalDate> second = service.occurrenceDates(t, from, to);

        assertThat(second).isEqualTo(first);
        assertThat(first).doesNotHaveDuplicates();
    }

    @Test
    void isOccurrenceDateChecksAlignmentAnchorAndEnd() {
        Task t = task(RecurrenceType.CUSTOM, 3, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));

        assertThat(service.isOccurrenceDate(t, LocalDate.of(2026, 10, 1))).isTrue();
        assertThat(service.isOccurrenceDate(t, LocalDate.of(2026, 10, 7))).isTrue();
        assertThat(service.isOccurrenceDate(t, LocalDate.of(2026, 10, 8))).isFalse(); // not aligned
        assertThat(service.isOccurrenceDate(t, LocalDate.of(2026, 10, 13))).isFalse(); // after end date
        assertThat(service.isOccurrenceDate(t, LocalDate.of(2026, 9, 28))).isFalse(); // before anchor
    }
}
