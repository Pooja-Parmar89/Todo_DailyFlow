package com.dailyflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskSpecificationsTest {

    @Test
    void likeWildcardsInUserInputAreEscaped() {
        assertThat(TaskSpecifications.escapeLike("100%_done!")).isEqualTo("100!%!_done!!");
    }

    @Test
    void plainTextIsLeftUntouched() {
        assertThat(TaskSpecifications.escapeLike("write report")).isEqualTo("write report");
    }
}
