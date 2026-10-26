package com.dailyflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(
            @NotBlank(message = "Name is required") @Size(max = 60, message = "Name must be at most 60 characters")
            String name,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Color must be a hex value like #4f46e5")
            String color,
            @Size(max = 40, message = "Icon must be at most 40 characters")
            @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Icon may only contain letters, digits, - and _")
            String icon) {
    }

    public record CategoryDto(Long id, String name, String color, String icon, boolean defaultCategory,
                              long taskCount) {
    }

    /** Compact category info embedded in task payloads. */
    public record CategorySummary(Long id, String name, String color, String icon) {
    }
}
