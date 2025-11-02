package com.anode.workflow.service;

import com.anode.workflow.exceptions.WorkflowRuntimeException;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Encapsulates error information from workflow execution failures.
 *
 * <p>The {@code ErrorHandler} captures and stores error details when a workflow step or process
 * fails. It provides structured error information including error codes, messages, detailed
 * descriptions, and retry indicators. This class is designed to be embedded in workflow entities
 * for persistence and can distinguish between retryable transient errors and permanent failures.
 *
 * <h2>Error Information</h2>
 * The error handler captures:
 * <ul>
 *   <li><b>Error Code</b> - Numeric identifier for the error type (default: 0)</li>
 *   <li><b>Error Message</b> - Brief description of what went wrong</li>
 *   <li><b>Error Details</b> - Additional context and stack trace information</li>
 *   <li><b>Retryable Flag</b> - Indicates if the operation should be retried</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create from an exception
 * try {
 *     processPayment(orderId);
 * } catch (Exception e) {
 *     ErrorHandler errorHandler = new ErrorHandler(e);
 *     errorHandler.setRetryable(isTransientError(e));
 *     context.setPendErrorHandler(errorHandler);
 * }
 *
 * // Create with explicit code and message
 * ErrorHandler errorHandler = new ErrorHandler(1001, "Payment gateway timeout");
 * errorHandler.setErrorDetails("Connection timeout after 30 seconds");
 * errorHandler.setRetryable(true);
 *
 * // Check if error is retryable
 * if (errorHandler.isRetryable()) {
 *     scheduleRetry();
 * } else {
 *     sendFailureNotification();
 * }
 * }</pre>
 *
 * <h2>Integration with Workflow</h2>
 * Error handlers are automatically created and managed by the workflow engine when errors occur:
 * <pre>{@code
 * {@literal @}Override
 * public void invoke(EventType event, WorkflowContext context) {
 *     if (event == EventType.ON_PROCESS_PEND) {
 *         ErrorHandler error = context.getPendErrorTuple();
 *         if (error.getErrorCode() > 0) {
 *             log.error("Workflow paused due to error: {}",
 *                 error.getErrorString());
 *             alertOps(context.getCaseId(), error);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Error Classification</h2>
 * Use the retryable flag to distinguish between error types:
 * <ul>
 *   <li><b>Transient Errors</b> (retryable=true): Network timeouts, temporary resource unavailability</li>
 *   <li><b>Permanent Errors</b> (retryable=false): Validation failures, business rule violations</li>
 * </ul>
 *
 * @see WorkflowRuntimeException
 * @see WorkflowContext#getPendErrorTuple()
 * @since 0.0.1
 */
@Getter
@NoArgsConstructor
@Embeddable
public class ErrorHandler {
    private static final String NEW_LINE = System.lineSeparator();
    private Integer errorCode = 0;
    private String errorMessage = "";
    private String errorDetails = "";
    private boolean isRetryable = false;

    /**
     * Constructs an error handler with an explicit error code and message.
     *
     * @param code the error code identifying the type of error (use 0 for generic errors)
     * @param message a brief description of the error
     */
    public ErrorHandler(Integer code, String message) {
        this.errorCode = code;
        this.errorMessage = message;
    }

    /**
     * Constructs an error handler from an exception with a specific error code.
     *
     * <p>If the exception is a {@link WorkflowRuntimeException}, its embedded error information
     * is extracted. Otherwise, the exception message is used as the error message.
     *
     * @param errorCode the error code to associate with this error
     * @param e the exception that occurred
     */
    public ErrorHandler(Integer errorCode, Exception e) {
        set(errorCode, e);
    }

    /**
     * Constructs an error handler from an exception with error code 0.
     *
     * <p>If the exception is a {@link WorkflowRuntimeException}, its embedded error information
     * is extracted. Otherwise, the exception message is used as the error message.
     *
     * @param e the exception that occurred
     */
    public ErrorHandler(Exception e) {
        set(0, e);
    }

    private void set(Integer errorCode, Exception e) {
        if (e instanceof WorkflowRuntimeException ue) {
            ErrorHandler et = ue.getErrorHandler();
            this.errorCode = Objects.nonNull(et.getErrorCode()) ? et.getErrorCode() : 0;
            errorMessage = Objects.nonNull(et.getErrorMessage()) ? et.getErrorMessage() : "";
            errorDetails = Objects.nonNull(et.getErrorDetails()) ? et.getErrorDetails() : "";
            isRetryable = et.isRetryable();
        } else {
            this.errorCode = Objects.nonNull(errorCode) ? errorCode : 0;
            errorMessage = Objects.nonNull(e.getMessage()) ? e.getMessage() : "";
        }
    }

    /**
     * Sets whether this error should trigger a retry attempt.
     *
     * <p>Use this to distinguish between transient errors (network timeouts, temporary unavailability)
     * that should be retried, and permanent errors (validation failures, business rule violations)
     * that should not.
     *
     * @param retryable {@code true} if the failed operation should be retried, {@code false} otherwise
     */
    public void setRetryable(boolean retryable) {
        this.isRetryable = retryable;
    }

    /**
     * Checks if this error indicates a retryable failure.
     *
     * @return {@code true} if the operation should be retried, {@code false} if it's a permanent failure
     */
    public boolean isRetryable() {
        return isRetryable;
    }

    /**
     * Sets additional details about the error such as stack traces or context information.
     *
     * @param errorDetails detailed error information; null values are converted to empty strings
     */
    public void setErrorDetails(String errorDetails) {
        if (errorDetails == null) {
            errorDetails = "";
        }
        this.errorDetails = errorDetails;
    }

    /**
     * Sets the numeric error code.
     *
     * @param errorCode the error code identifying the error type; null values are converted to 0
     */
    public void setErrorCode(Integer errorCode) {
        if (errorCode == null) {
            errorCode = 0;
        }
        this.errorCode = errorCode;
    }

    /**
     * Sets the error message.
     *
     * @param errorMessage a brief description of the error; null values are converted to empty strings
     */
    public void setErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            errorMessage = "";
        }
        this.errorMessage = errorMessage;
    }

    /**
     * Returns a formatted string representation of all error information.
     *
     * <p>The string includes error code, message, and details separated by newlines,
     * useful for logging and debugging.
     *
     * @return a multi-line string containing all error information
     */
    public String getErrorString() {
        String s = "Error code -> " + errorCode + NEW_LINE;
        s = s + "Error message -> " + errorMessage + NEW_LINE;
        s = s + "Error details -> " + errorDetails + NEW_LINE;
        return s;
    }

    /**
     * Returns the error code as a string.
     *
     * @return the error code converted to a string
     */
    public String getErrorCodeAsString() {
        return errorCode.toString();
    }
}
