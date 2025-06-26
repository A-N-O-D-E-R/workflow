package com.anode.workflow.exceptions;

import com.anode.workflow.service.ErrorHandler;
import java.text.MessageFormat;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class WorkflowRuntimeException extends RuntimeException {

    private ErrorHandler errorHandler = new ErrorHandler();
    private Throwable cause = null;

    private String getMessage(Integer code, String... vargs) {
        String msg = ErrorMap.getErrorMessage(code);
        if (msg == null) {
            log.error("Error code {} not found in ErrorMap", code);
            msg = "";
        } else {
            if (vargs.length != 0) {
                msg = MessageFormat.format(msg, (Object[]) vargs);
            }
        }

        return msg;
    }

    // constructor for creating from an error code
    public WorkflowRuntimeException(Integer code, String... vargs) {
        this.errorHandler.setErrorCode(code);
        this.errorHandler.setErrorMessage(getMessage(code, vargs));
        this.errorHandler.setErrorDetails("");
    }

    public WorkflowRuntimeException(String message) {
        this.errorHandler.setErrorCode(0);
        this.errorHandler.setErrorMessage(message);
        this.errorHandler.setErrorDetails("");
    }

    // constructor for creating Unify Exception from an Error Tuple
    public WorkflowRuntimeException(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;

        String s = errorHandler.getErrorMessage();
        if (s.isEmpty()) {
            // we try and get value from error map
            s = ErrorMap.getErrorMessage(errorHandler.getErrorCode());
            if (s == null) {
                s = "";
            }
        }
        this.errorHandler.setErrorMessage(s);
    }

    // constructor for wrapping up an exception in Unify Exception
    public WorkflowRuntimeException(Integer code, Throwable cause, String... vargs) {
        super(cause);
        this.errorHandler.setErrorCode(code);
        this.errorHandler.setErrorMessage(
                getMessage(code, vargs) + ". Cause -> " + cause.getMessage());
        this.errorHandler.setErrorDetails(getStackTrace(cause, 12));
        this.cause = cause;
    }

    public WorkflowRuntimeException(String message, Throwable cause) {
        super(cause);
        this.errorHandler.setErrorCode(0);
        this.errorHandler.setErrorMessage(message + ". Cause -> " + cause.getMessage());
        this.errorHandler.setErrorDetails(getStackTrace(cause, 12));
        this.cause = cause;
    }

    @Override
    public String getMessage() {
        return errorHandler.getErrorMessage();
    }

    public Integer getErrorCode() {
        return errorHandler.getErrorCode();
    }

    public String getDetails() {
        return errorHandler.getErrorDetails();
    }

    public boolean isRetryable() {
        return errorHandler.isRetryable();
    }

    private static String getStackTrace(Throwable e, int levels) {
        Throwable e1 = null;
        if (e instanceof WorkflowRuntimeException) {
            e1 = e.getCause();
            if (e1 != null) {
                e = e1;
            }
        }

        StackTraceElement[] se = e.getStackTrace();
        if (levels > se.length) {
            levels = se.length;
        }

        String s = "";
        for (int i = 0; i < levels; i++) {
            s =
                    s
                            + "at "
                            + se[i].getClassName()
                            + "("
                            + se[i].getFileName()
                            + ":"
                            + se[i].getLineNumber()
                            + ")";
            if (i != (levels - 1)) {
                s = s + System.lineSeparator();
            }
        }
        return s;
    }
}
