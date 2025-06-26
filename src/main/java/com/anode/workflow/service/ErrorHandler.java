package com.anode.workflow.service;

import com.anode.workflow.exceptions.WorkflowRuntimeException;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Embeddable
public class ErrorHandler {
    private static final String NEW_LINE = System.lineSeparator();
    private Integer errorCode = 0;
    private String errorMessage = "";
    private String errorDetails = "";
    private boolean isRetryable = false;

    public ErrorHandler(Integer code, String message) {
        this.errorCode = code;
        this.errorMessage = message;
    }

    public ErrorHandler(Integer errorCode, Exception e) {
        set(errorCode, e);
    }

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

    public void setRetryable(boolean retryable) {
        this.isRetryable = retryable;
    }

    public boolean isRetryable() {
        return isRetryable;
    }

    public void setErrorDetails(String errorDetails) {
        if (errorDetails == null) {
            errorDetails = "";
        }
        this.errorDetails = errorDetails;
    }

    public void setErrorCode(Integer errorCode) {
        if (errorCode == null) {
            errorCode = 0;
        }
        this.errorCode = errorCode;
    }

    public void setErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            errorMessage = "";
        }
        this.errorMessage = errorMessage;
    }

    public String getErrorString() {
        String s = "Error code -> " + errorCode + NEW_LINE;
        s = s + "Error message -> " + errorMessage + NEW_LINE;
        s = s + "Error details -> " + errorDetails + NEW_LINE;
        return s;
    }

    public String getErrorCodeAsString() {
        return errorCode.toString();
    }
}
