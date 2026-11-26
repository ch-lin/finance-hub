/*
 * Copyright (c) 2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin. Unauthorized copying,
 * modification, distribution, reverse engineering, or use of the Software,
 * via any medium, is strictly prohibited without prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ch.lin.finance.hub.backend.api.common.advice;

import java.time.DateTimeException;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import ch.lin.finance.hub.backend.api.common.exception.CsvRowsFailedException;
import ch.lin.finance.hub.backend.api.common.exception.InvalidEducationTimelineException;
import ch.lin.finance.hub.backend.api.common.exception.ItemAlreadyExistsException;
import ch.lin.finance.hub.backend.api.common.exception.ItemNotFoundException;
import ch.lin.finance.hub.backend.api.common.exception.OptimizationNotSupportedException;
import ch.lin.finance.hub.backend.api.common.exception.TaskProcessingException;
import ch.lin.finance.hub.backend.api.common.exception.UnsupportedCsvFormatException;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.platform.api.ApiError;
import ch.lin.platform.api.ApiResponse;

class WebGlobalExceptionHandlerTest {

    private WebGlobalExceptionHandler handler;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        handler = new WebGlobalExceptionHandler();
    }

    @Test
    void handleNotFoundExceptions() {
        ItemNotFoundException ex = new ItemNotFoundException("Not found message");
        ResponseEntity<ApiResponse<ApiError>> response = handler.handleNotFoundExceptions(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("NOT_FOUND", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("Not found message", java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
    }

    @Test
    void handleConflictExceptions() {
        ItemAlreadyExistsException ex = new ItemAlreadyExistsException("Conflict message");
        ResponseEntity<ApiResponse<ApiError>> response = handler.handleConflictExceptions(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CONFLICT", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("Conflict message", java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
    }

    @Test
    void handleTaskProcessingException() {
        TaskProcessingException ex = new TaskProcessingException(TransactionSource.AMAZON, "TIMEOUT", "Timeout message",
                null);
        ResponseEntity<ApiResponse<ApiError>> response = handler.handleTaskProcessingException(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("TIMEOUT", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("Timeout message", java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());

        TaskProcessingException ex2 = new TaskProcessingException(TransactionSource.AMAZON, "ERROR", "Error message",
                null);
        ResponseEntity<ApiResponse<ApiError>> response2 = handler.handleTaskProcessingException(ex2);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response2.getStatusCode());
        assertNotNull(response2.getBody());
        assertEquals("ERROR", java.util.Objects.requireNonNull(response2.getBody()).getData().getCode());
        assertEquals("Error message", java.util.Objects.requireNonNull(response2.getBody()).getData().getMessage());
    }

    @Test
    void handleUnsupportedCsvFormatException() {
        UnsupportedCsvFormatException ex = new UnsupportedCsvFormatException("Unsupported CSV message");
        ResponseEntity<ApiResponse<ApiError>> response = handler.handleUnsupportedCsvFormatException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNSUPPORTED_FORMAT", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("Unsupported CSV message",
                java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
    }

    @Test
    void handleCsvRowsFailedException() {
        CsvRowsFailedException ex = new CsvRowsFailedException("CSV parse message", List.of("row1"));
        ResponseEntity<ApiResponse<ApiError>> response = handler.handleCsvRowsFailedException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CSV_PARSING_FAILED", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("CSV parse message", java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
    }

    @Test
    void handleValidationExceptions_MethodArgumentNotValid() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors())
                .thenReturn(List.of(new FieldError("objectName", "fieldName", "must not be null")));

        ResponseEntity<ApiResponse<ApiError>> response = handler.handleValidationExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_FAILED", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("fieldName: must not be null",
                java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
    }

    @Test
    void handleValidationExceptions_BindException() {
        BindingResult bindingResult = mock(BindingResult.class);
        BindException ex = new BindException(Objects.requireNonNull(bindingResult));
        when(bindingResult.getFieldErrors())
                .thenReturn(List.of(new FieldError("objectName", "fieldName", "must not be null")));

        ResponseEntity<ApiResponse<ApiError>> response = handler.handleValidationExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_FAILED", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("fieldName: must not be null",
                java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
    }

    @Test
    void handleValidationExceptions_OtherException() {
        Exception ex = new Exception("Other exception");

        ResponseEntity<ApiResponse<ApiError>> response = handler.handleValidationExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_FAILED", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("Invalid request payload or parameters",
                java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
    }

    @Test
    void handleBadRequestExceptions_IllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Illegal argument message");

        ResponseEntity<ApiResponse<ApiError>> response = handler.handleBadRequestExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("Illegal argument message",
                java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
    }

    @Test
    void handleBadRequestExceptions_MethodArgumentTypeMismatch() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getCause()).thenReturn(new RuntimeException("Mismatch cause"));

        ResponseEntity<ApiResponse<ApiError>> response = handler.handleBadRequestExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("Mismatch cause", java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
    }

    @Test
    void handleBadRequestExceptions_MethodArgumentTypeMismatch_NoCause() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getCause()).thenReturn(null);
        when(ex.getMessage()).thenReturn("Mismatch message");

        ResponseEntity<ApiResponse<ApiError>> response = handler.handleBadRequestExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("Mismatch message", java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
    }

    @Test
    void handleBadRequestExceptions_DateTimeException() {
        DateTimeException ex = new DateTimeException("Invalid value for MonthOfYear: 15");

        ResponseEntity<ApiResponse<ApiError>> response = handler.handleBadRequestExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("Invalid value for MonthOfYear: 15",
                java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
    }

    @Test
    void handleResponseStatusException_ReturnsStandardFormat() {
        org.springframework.web.server.ResponseStatusException ex = new org.springframework.web.server.ResponseStatusException(
                HttpStatus.FORBIDDEN, "Forbidden reason");
        ResponseEntity<ApiResponse<ApiError>> response = handler.handleResponseStatusException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FORBIDDEN", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("Forbidden reason", java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
    }

    @Test
    void handleOptimizationNotSupportedException() {
        OptimizationNotSupportedException ex = new OptimizationNotSupportedException("Search space exceeds limit");
        ResponseEntity<ApiResponse<ApiError>> response = handler.handleOptimizationNotSupportedException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("OPTIMIZATION_NOT_SUPPORTED", Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("Search space exceeds limit", Objects.requireNonNull(response.getBody()).getData().getMessage());
    }

    @Test
    void handleInvalidEducationTimelineException() {
        InvalidEducationTimelineException ex = new InvalidEducationTimelineException("Timeline overlap detected");
        ResponseEntity<ApiResponse<ApiError>> response = handler.handleInvalidEducationTimelineException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_EDUCATION_TIMELINE", Objects.requireNonNull(response.getBody()).getData().getCode());
        assertEquals("Timeline overlap detected", Objects.requireNonNull(response.getBody()).getData().getMessage());
    }
}
