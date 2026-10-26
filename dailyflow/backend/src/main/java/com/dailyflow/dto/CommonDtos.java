package com.dailyflow.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

public final class CommonDtos {

    private CommonDtos() {
    }

    /** Consistent error body for every non-2xx response. */
    public record ApiError(Instant timestamp, int status, String error, String message, String path,
                           Map<String, String> fieldErrors) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    public record ActivityDto(Long id, Long taskId, String taskTitle, String action, String summary,
                              LocalDateTime createdAt) {
    }
}
