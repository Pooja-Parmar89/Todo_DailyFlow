package com.dailyflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyflow.dto.CommonDtos.ApiError;
import com.dailyflow.exception.BadRequestException;
import com.dailyflow.exception.ConflictException;
import com.dailyflow.exception.FieldValidationException;
import com.dailyflow.exception.NotFoundException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks/5");

    @Test
    void notFoundMapsTo404WithConsistentBody() {
        ResponseEntity<ApiError> response = handler.handleNotFound(new NotFoundException("Task not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.message()).isEqualTo("Task not found");
        assertThat(body.path()).isEqualTo("/api/tasks/5");
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.fieldErrors()).isEmpty();
    }

    @Test
    void fieldValidationCarriesFieldErrorsAnd400() {
        ResponseEntity<ApiError> response = handler.handleFieldValidation(
                new FieldValidationException(Map.of("dueAt", "Due date must not be before the start date")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsEntry("dueAt",
                "Due date must not be before the start date");
    }

    @Test
    void badRequestAndConflictUseTheirStatusCodes() {
        assertThat(handler.handleBadRequest(new BadRequestException("x"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleConflict(new ConflictException("x"), request).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleIntegrity(new DataIntegrityViolationException("dup"), request).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void badCredentialsGivesGenericUnauthorizedMessage() {
        ResponseEntity<ApiError> response = handler.handleBadCredentials(
                new BadCredentialsException("User not found: secret@example.com"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).isEqualTo("Invalid email or password");
    }

    @Test
    void unexpectedErrorsNeverLeakInternalDetails() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new IllegalStateException("jdbc:mysql://secret-host password=hunter2"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().message()).doesNotContain("hunter2");
    }
}
