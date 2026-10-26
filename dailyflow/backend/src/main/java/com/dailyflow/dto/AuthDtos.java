package com.dailyflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    private static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    private static final String PASSWORD_MESSAGE = "Password must contain at least one letter and one number";

    public record RegisterRequest(
            @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters")
            String displayName,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address")
            @Size(max = 190, message = "Email must be at most 190 characters")
            String email,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
            @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE)
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password) {
    }

    public record UserDto(Long id, String email, String displayName, boolean emailRemindersEnabled,
                          int reminderLeadMinutes) {
    }

    public record AuthResponse(String token, String tokenType, long expiresInSeconds, UserDto user) {
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters")
            String displayName,
            boolean emailRemindersEnabled,
            @Min(value = 5, message = "Lead time must be at least 5 minutes")
            @Max(value = 10080, message = "Lead time must be at most 7 days")
            int reminderLeadMinutes) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
            @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE)
            String newPassword) {
    }
}
