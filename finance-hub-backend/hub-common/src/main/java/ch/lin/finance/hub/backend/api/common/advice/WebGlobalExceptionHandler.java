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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import ch.lin.finance.hub.backend.api.common.exception.CsvRowsFailedException;
import ch.lin.finance.hub.backend.api.common.exception.CurrencyConversionException;
import ch.lin.finance.hub.backend.api.common.exception.HtmlParsingException;
import ch.lin.finance.hub.backend.api.common.exception.InvalidEducationTimelineException;
import ch.lin.finance.hub.backend.api.common.exception.ItemAlreadyExistsException;
import ch.lin.finance.hub.backend.api.common.exception.ItemNotFoundException;
import ch.lin.finance.hub.backend.api.common.exception.OptimizationNotSupportedException;
import ch.lin.finance.hub.backend.api.common.exception.ReadOnlyFamilyRecordException;
import ch.lin.finance.hub.backend.api.common.exception.TaskProcessingException;
import ch.lin.finance.hub.backend.api.common.exception.UnsupportedCsvFormatException;
import ch.lin.platform.api.ApiError;
import ch.lin.platform.api.ApiResponse;
import ch.lin.platform.web.advice.BaseGlobalExceptionHandler;

/**
 * Global exception handler for the web layer.
 * <p>
 * This class uses {@link ControllerAdvice} to intercept exceptions thrown by
 * controllers and maps them to a standardized {@link ApiResponse} format. This
 * ensures that clients receive consistent and structured error responses for
 * different failure scenarios.
 */
@ControllerAdvice
public class WebGlobalExceptionHandler extends BaseGlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebGlobalExceptionHandler.class);

    /**
     * Handles exceptions for resources that could not be found.
     * <p>
     * Catches various "Not Found" exceptions (e.g.,
     * {@link ChannelNotFoundException}) and returns an {@link ApiResponse} with
     * a "NOT_FOUND" error code. The HTTP status is expected to be set to 404
     * (Not Found) via the {@code @ResponseStatus} annotation on the exception
     * classes.
     *
     * @param ex The caught exception (e.g., {@link ChannelNotFoundException}).
     * @return An {@link ApiResponse} containing the error details.
     */
    @ExceptionHandler({
        ItemNotFoundException.class,})
    public ResponseEntity<ApiResponse<ApiError>> handleNotFoundExceptions(RuntimeException ex) {
        logger.error("Resource not found: {}", ex.getMessage(), ex);
        ApiError apiError = new ApiError("NOT_FOUND", ex.getMessage());
        ApiResponse<ApiError> response = ApiResponse.failure(apiError);
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles exceptions for resources that already exist.
     * <p>
     * Catches various "Already Exists" exceptions (e.g.,
     * {@link ChannelAlreadyExistsException}) and returns an {@link ApiResponse}
     * with a "CONFLICT" error code. The HTTP status is expected to be set to
     * 409 (Conflict) via the {@code @ResponseStatus} annotation on the
     * exception classes.
     *
     * @param ex The caught exception (e.g.,
     * {@link ChannelAlreadyExistsException}).
     * @return An {@link ApiResponse} containing the error details.
     */
    @ExceptionHandler({
        ItemAlreadyExistsException.class,})
    public ResponseEntity<ApiResponse<ApiError>> handleConflictExceptions(RuntimeException ex) {
        logger.error("Conflict detected: {}", ex.getMessage(), ex);
        ApiError apiError = new ApiError("CONFLICT", ex.getMessage());
        ApiResponse<ApiError> response = ApiResponse.failure(apiError);
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /**
     * Handles exceptions when trying to modify a family member's read-only
     * record.
     *
     * @param ex The caught exception
     * @return An {@link ApiResponse} with 400 Bad Request status.
     */
    @ExceptionHandler(ReadOnlyFamilyRecordException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleReadOnlyFamilyRecordException(ReadOnlyFamilyRecordException ex) {
        logger.warn("Attempt to modify read-only family record: {}", ex.getMessage());
        ApiError apiError = new ApiError("BAD_REQUEST", ex.getMessage());
        return new ResponseEntity<>(ApiResponse.failure(apiError), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles exceptions related to specific transaction source tasks.
     *
     * @param ex The caught exception
     */
    @ExceptionHandler(TaskProcessingException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleTaskProcessingException(TaskProcessingException ex) {
        logger.error("Task processing failed for transaction source {}: {}", ex.getTransactionSource(), ex.getMessage(),
                ex);
        ApiError apiError = new ApiError(ex.getErrorCode(), ex.getMessage());

        HttpStatus status = "TIMEOUT".equals(ex.getErrorCode())
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.INTERNAL_SERVER_ERROR;

        return new ResponseEntity<>(ApiResponse.failure(apiError), status);
    }

    /**
     * Handles cases where an uploaded or processed file has an unsupported
     * format.
     *
     * @param ex The caught exception.
     * @return An {@link ApiResponse} with 400 status indicating the format is
     * unsupported.
     */
    @ExceptionHandler(UnsupportedCsvFormatException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleUnsupportedCsvFormatException(UnsupportedCsvFormatException ex) {
        logger.warn("Unsupported CSV format: {}", ex.getMessage(), ex);
        ApiError apiError = new ApiError("UNSUPPORTED_FORMAT", ex.getMessage());
        ApiResponse<ApiError> response = ApiResponse.failure(apiError);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles cases where a CSV file contains rows that failed to parse
     * correctly.
     *
     * @param ex The caught exception.
     * @return An {@link ApiResponse} with 400 status indicating the parsing
     * failure.
     */
    @ExceptionHandler(CsvRowsFailedException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleCsvRowsFailedException(CsvRowsFailedException ex) {
        logger.warn("CSV rows parsing failed: {}", ex.getMessage(), ex);
        ApiError apiError = new ApiError("CSV_PARSING_FAILED", ex.getMessage());
        ApiResponse<ApiError> response = ApiResponse.failure(apiError);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles validation exceptions for invalid request payloads or query
     * inputs.
     *
     * @param ex The caught exception.
     * @return An {@link ApiResponse} with 400 status indicating validation
     * failure.
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<ApiError>> handleValidationExceptions(Exception ex) {
        BindingResult bindingResult = switch (ex) {
            case MethodArgumentNotValidException manvEx ->
                manvEx.getBindingResult();
            case BindException bindEx ->
                bindEx.getBindingResult();
            default ->
                null;
        };

        String message = "Invalid request payload or parameters";
        if (bindingResult != null) {
            message = bindingResult.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
        }
        logger.warn("Validation failed: {}", message, ex);
        ApiError apiError = new ApiError("VALIDATION_FAILED", message);
        return new ResponseEntity<>(ApiResponse.failure(apiError), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles ResponseStatusException to ensure the error is wrapped in the
     * standard ApiResponse format instead of the default Spring error response.
     *
     * @param ex The caught ResponseStatusException.
     * @return An {@link ApiResponse} with the corresponding HTTP status.
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleResponseStatusException(
            org.springframework.web.server.ResponseStatusException ex) {
        logger.warn("Response status exception: {}", ex.getReason(), ex);
        String errorCode = "ERROR";
        if (ex.getStatusCode() instanceof HttpStatus httpStatus) {
            errorCode = httpStatus.name();
        }
        ApiError apiError = new ApiError(errorCode, ex.getReason());
        return new ResponseEntity<>(ApiResponse.failure(apiError), ex.getStatusCode());
    }

    /**
     * Handles bad requests such as invalid enum values or illegal arguments.
     *
     * @param ex The caught exception.
     * @return An {@link ApiResponse} with 400 status.
     */
    @ExceptionHandler({
        IllegalArgumentException.class,
        MethodArgumentTypeMismatchException.class,
        DateTimeException.class
    })
    public ResponseEntity<ApiResponse<ApiError>> handleBadRequestExceptions(Exception ex) {
        Throwable cause = ex.getCause();
        String message = (ex instanceof MethodArgumentTypeMismatchException && cause != null)
                ? cause.getMessage()
                : ex.getMessage();
        logger.warn("Bad request parameter: {}", message, ex);
        ApiError apiError = new ApiError("BAD_REQUEST", message);
        ApiResponse<ApiError> response = ApiResponse.failure(apiError);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles exceptions for currency conversion failures.
     *
     * @param ex The caught exception.
     * @return An {@link ApiResponse} with 422 status.
     */
    @ExceptionHandler(CurrencyConversionException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleCurrencyConversionException(CurrencyConversionException ex) {
        logger.error("Currency conversion failed: {}", ex.getMessage(), ex);
        ApiError apiError = new ApiError("CURRENCY_CONVERSION_FAILED", ex.getMessage());
        return new ResponseEntity<>(ApiResponse.failure(apiError), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Handles exceptions when HTML parsing fails.
     *
     * @param ex The caught exception.
     * @return An {@link ApiResponse} with 400 status.
     */
    @ExceptionHandler(HtmlParsingException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleHtmlParsingException(HtmlParsingException ex) {
        logger.error("HTML parsing failed: {}", ex.getMessage(), ex);
        ApiError apiError = new ApiError("HTML_PARSING_FAILED", ex.getMessage());
        return new ResponseEntity<>(ApiResponse.failure(apiError), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles exceptions for unsupported optimization configurations or bounds
     * exceeded.
     *
     * @param ex The caught exception.
     * @return An {@link ApiResponse} with 400 status.
     */
    @ExceptionHandler(OptimizationNotSupportedException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleOptimizationNotSupportedException(
            OptimizationNotSupportedException ex) {
        logger.warn("Optimization not supported: {}", ex.getMessage(), ex);
        ApiError apiError = new ApiError("OPTIMIZATION_NOT_SUPPORTED", ex.getMessage());
        return new ResponseEntity<>(ApiResponse.failure(apiError), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles exceptions when child education settings produce an invalid
     * timeline.
     *
     * @param ex The caught exception.
     * @return An {@link ApiResponse} with 400 Bad Request status.
     */
    @ExceptionHandler(InvalidEducationTimelineException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleInvalidEducationTimelineException(
            InvalidEducationTimelineException ex) {
        logger.warn("Invalid education timeline: {}", ex.getMessage());
        ApiError apiError = new ApiError("INVALID_EDUCATION_TIMELINE", ex.getMessage());
        return new ResponseEntity<>(ApiResponse.failure(apiError), HttpStatus.BAD_REQUEST);
    }
}
