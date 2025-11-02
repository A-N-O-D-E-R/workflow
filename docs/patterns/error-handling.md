<artifact identifier="error-handling-patterns-doc" type="text/markdown" title="Error Handling Patterns Documentation">
# Error Handling Patterns

## Overview

Effective error handling is critical for building robust, maintainable workflows. This guide covers patterns, best practices, and strategies for handling errors in Simple Workflow applications.

## Table of Contents
- [Error Handling Philosophy](#error-handling-philosophy)
- [Error Types and Classification](#error-types-and-classification)
- [Core Error Handling Mechanisms](#core-error-handling-mechanisms)
- [Error Handling Patterns](#error-handling-patterns)
- [ErrorMap Usage](#errormap-usage)
- [Recovery Strategies](#recovery-strategies)
- [Error Monitoring and Logging](#error-monitoring-and-logging)
- [Testing Error Scenarios](#testing-error-scenarios)
- [Best Practices](#best-practices)
- [Real-World Examples](#real-world-examples)

## Error Handling Philosophy

### Design Principles

1. **Fail Fast, Recover Gracefully**: Detect errors early but provide recovery paths
2. **Explicit Error States**: Make error conditions visible in workflow state
3. **Meaningful Error Messages**: Provide context for debugging and user communication
4. **Separation of Concerns**: Technical errors vs business errors
5. **Idempotent Error Handling**: Error recovery operations should be safe to retry

### Error Handling Layers

```
┌─────────────────────────────────────────────┐
│          Application Layer                  │
│  (Business Logic, Validation)               │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│          Workflow Engine Layer              │
│  (Step Execution, Route Processing)         │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│          Infrastructure Layer               │
│  (Database, Network, External Services)     │
└─────────────────────────────────────────────┘
```

## Error Types and Classification

### 1. Business Errors

**Definition**: Expected errors that are part of normal business logic

**Characteristics**:
- Predictable and expected
- Often recoverable
- Require business decision or user intervention
- Not true "failures"

**Examples**:
```java
// Insufficient inventory
// Credit card declined
// Document requires approval
// Customer not eligible for discount
```

**Handling Strategy**: Model as workflow branches or pend for resolution

### 2. Technical Errors

**Definition**: Unexpected system failures or infrastructure issues

**Characteristics**:
- Unexpected and unpredictable
- May be transient (retry-able)
- May require technical intervention
- True system failures

**Examples**:
```java
// Database connection timeout
// Network failure
// Service unavailable
// Out of memory
// JSON parsing error
```

**Handling Strategy**: Pend for retry or escalation

### 3. Validation Errors

**Definition**: Invalid input or state that violates business rules

**Characteristics**:
- Preventable with proper validation
- Should be caught early
- Often indicate programming errors
- Not retry-able

**Examples**:
```java
// Missing required field
// Invalid format
// Out of range value
// Inconsistent state
```

**Handling Strategy**: Validate early, reject immediately

### 4. Fatal Errors

**Definition**: Unrecoverable errors that prevent workflow continuation

**Characteristics**:
- Cannot be automatically resolved
- Require manual intervention
- Indicate serious system or data issues
- Workflow cannot proceed

**Examples**:
```java
// Corrupted workflow definition
// Missing critical configuration
// Data inconsistency
// Security violation
```

**Handling Strategy**: Pend to error queue, alert operations

## Core Error Handling Mechanisms

### 1. StepResponseType.ERROR_PEND

The primary mechanism for error handling:

```java
public class PaymentProcessingStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        try {
            processPayment();
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (PaymentServiceUnavailableException e) {
            // Service temporarily down - pend for retry
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                "Payment service unavailable: " + e.getMessage(),
                "payment_retry_queue"
            );
            
        } catch (InsufficientFundsException e) {
            // Business error - different handling
            context.getProcessVariables().setValue(
                "payment_declined_reason",
                WorkflowVariableType.STRING,
                "Insufficient funds"
            );
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
    }
}
```

**When to use**:
- Transient errors that may resolve on retry
- Errors requiring human intervention
- External service failures
- Any error where workflow should pause

**Effect**:
- Sets execution path status to STARTED
- Sets `unit_response_type` to ERROR_PEND
- Assigns to specified work basket
- Workflow can be resumed later

### 2. Error Codes via ErrorMap

```java
public class OrderValidationStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        Order order = getOrder();
        
        if (order.getItems().isEmpty()) {
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                ErrorMap.getErrorDesc(1001, "Order has no items"),
                "validation_error_queue",
                1001  // Error code
            );
        }
        
        if (order.getTotalAmount() <= 0) {
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                ErrorMap.getErrorDesc(1002, "Invalid order total: " + order.getTotalAmount()),
                "validation_error_queue",
                1002
            );
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### 3. Throwing WorkflowRuntimeException

For fatal errors that should stop execution immediately:

```java
public class CriticalStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowDefinition def = context.getWorkflowDefinition();
        
        if (def == null) {
            throw new WorkflowRuntimeException(
                "Workflow definition not found for case: " + context.getCaseId()
            );
        }
        
        // Validate critical configuration
        if (!isConfigurationValid()) {
            throw new WorkflowRuntimeException(
                "Invalid workflow configuration"
            );
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

**When to use**:
- Fatal errors that cannot be recovered
- Configuration errors
- Programming errors
- Data corruption

**Effect**:
- Stops workflow execution immediately
- Exception propagates to caller
- Workflow remains in inconsistent state
- Requires manual intervention

### 4. Ticket Mechanism

Jump to error handler step:

```java
public class DataProcessingStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        try {
            processData();
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (DataCorruptionException e) {
            log.error("Data corruption detected: {}", e.getMessage());
            
            // Store error details
            context.getProcessVariables().setValue(
                "error_details",
                WorkflowVariableType.STRING,
                e.getMessage()
            );
            
            // Jump to error handler
            return new TaskResponse(
                StepResponseType.OK_PROCEED,
                "",
                "",
                "error_handler_step"  // Ticket destination
            );
        }
    }
}
```

**When to use**:
- Need to jump to error handling flow
- Want to centralize error handling
- Error requires specific recovery steps
- Skip remaining normal flow

**Effect**:
- Immediately jumps to ticket destination
- Bypasses remaining steps in current flow
- Useful for centralized error handling

## Error Handling Patterns

### Pattern 1: Retry with Exponential Backoff

For transient errors that are likely to resolve:

```java
public class RetryableStep implements InvokableTask {
    
    private static final int MAX_RETRIES = 3;
    private static final String RETRY_COUNT_KEY = "retry_count";
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Get retry count
        Integer retryCount = vars.getInteger(RETRY_COUNT_KEY);
        if (retryCount == null) {
            retryCount = 0;
        }
        
        try {
            // Attempt operation
            performOperation();
            
            // Success - clear retry count
            vars.setValue(RETRY_COUNT_KEY, WorkflowVariableType.INTEGER, 0);
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (TransientException e) {
            retryCount++;
            vars.setValue(RETRY_COUNT_KEY, WorkflowVariableType.INTEGER, retryCount);
            
            if (retryCount >= MAX_RETRIES) {
                // Max retries exceeded - escalate
                log.error("Max retries exceeded for case {}", context.getCaseId());
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    ErrorMap.getErrorDesc(2001, "Operation failed after " + MAX_RETRIES + " retries"),
                    "error_escalation_queue",
                    2001
                );
            }
            
            // Calculate backoff delay (exponential: 1s, 2s, 4s, 8s...)
            long delaySeconds = (long) Math.pow(2, retryCount - 1);
            vars.setValue("retry_delay_seconds", WorkflowVariableType.LONG, delaySeconds);
            
            log.warn("Retry {} of {} after {}s for case {}", 
                retryCount, MAX_RETRIES, delaySeconds, context.getCaseId());
            
            // Pend for retry (external scheduler or manual resume after delay)
            return new TaskResponse(
                StepResponseType.OK_PEND_EOR,  // Re-execute this step
                "Retrying after " + delaySeconds + " seconds",
                "retry_queue"
            );
        }
    }
}
```

**Usage Scenario**: API calls, database operations, network requests

### Pattern 2: Circuit Breaker

Prevent cascading failures by stopping attempts after repeated failures:

```java
public class CircuitBreakerStep implements InvokableTask {
    
    private static final String CIRCUIT_STATE_KEY = "circuit_breaker_state";
    private static final String FAILURE_COUNT_KEY = "circuit_breaker_failures";
    private static final String LAST_FAILURE_TIME_KEY = "circuit_breaker_last_failure";
    
    private static final int FAILURE_THRESHOLD = 5;
    private static final long CIRCUIT_OPEN_DURATION_MS = 60000; // 1 minute
    
    enum CircuitState {
        CLOSED,  // Normal operation
        OPEN,    // Failing, don't attempt
        HALF_OPEN  // Testing if recovered
    }
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Get circuit state
        String stateStr = vars.getString(CIRCUIT_STATE_KEY);
        CircuitState state = stateStr != null 
            ? CircuitState.valueOf(stateStr) 
            : CircuitState.CLOSED;
        
        Integer failureCount = vars.getInteger(FAILURE_COUNT_KEY);
        if (failureCount == null) failureCount = 0;
        
        Long lastFailureTime = vars.getLong(LAST_FAILURE_TIME_KEY);
        
        // Check if circuit should transition from OPEN to HALF_OPEN
        if (state == CircuitState.OPEN && lastFailureTime != null) {
            if (System.currentTimeMillis() - lastFailureTime > CIRCUIT_OPEN_DURATION_MS) {
                state = CircuitState.HALF_OPEN;
                vars.setValue(CIRCUIT_STATE_KEY, WorkflowVariableType.STRING, state.name());
                log.info("Circuit breaker transitioning to HALF_OPEN");
            }
        }
        
        // If circuit is OPEN, fail fast
        if (state == CircuitState.OPEN) {
            log.warn("Circuit breaker is OPEN, failing fast");
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                "Circuit breaker is open, service unavailable",
                "circuit_breaker_queue"
            );
        }
        
        try {
            // Attempt operation
            performOperation();
            
            // Success - reset circuit
            if (state == CircuitState.HALF_OPEN) {
                log.info("Circuit breaker transitioning to CLOSED (recovered)");
            }
            vars.setValue(CIRCUIT_STATE_KEY, WorkflowVariableType.STRING, CircuitState.CLOSED.name());
            vars.setValue(FAILURE_COUNT_KEY, WorkflowVariableType.INTEGER, 0);
            
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            failureCount++;
            vars.setValue(FAILURE_COUNT_KEY, WorkflowVariableType.INTEGER, failureCount);
            vars.setValue(LAST_FAILURE_TIME_KEY, WorkflowVariableType.LONG, System.currentTimeMillis());
            
            if (failureCount >= FAILURE_THRESHOLD) {
                // Open circuit
                vars.setValue(CIRCUIT_STATE_KEY, WorkflowVariableType.STRING, CircuitState.OPEN.name());
                log.error("Circuit breaker OPENED after {} failures", failureCount);
                
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    "Circuit breaker opened due to repeated failures",
                    "circuit_breaker_queue"
                );
            }
            
            // Circuit still closed, retry
            return new TaskResponse(
                StepResponseType.OK_PEND_EOR,
                "Operation failed, retrying",
                "retry_queue"
            );
        }
    }
}
```

**Usage Scenario**: External service calls, protecting downstream services

### Pattern 3: Fallback Strategy

Provide alternative behavior when primary operation fails:

```java
public class FallbackStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        try {
            // Try primary operation
            String result = performPrimaryOperation();
            context.getProcessVariables().setValue(
                "result",
                WorkflowVariableType.STRING,
                result
            );
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (PrimaryServiceException e) {
            log.warn("Primary service failed, trying fallback: {}", e.getMessage());
            
            try {
                // Try fallback operation
                String fallbackResult = performFallbackOperation();
                context.getProcessVariables().setValue(
                    "result",
                    WorkflowVariableType.STRING,
                    fallbackResult
                );
                context.getProcessVariables().setValue(
                    "used_fallback",
                    WorkflowVariableType.BOOLEAN,
                    true
                );
                return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
                
            } catch (FallbackServiceException fe) {
                log.error("Both primary and fallback failed");
                
                // Both failed - use default or pend
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    "All services unavailable",
                    "service_error_queue"
                );
            }
        }
    }
    
    private String performPrimaryOperation() throws PrimaryServiceException {
        // Call primary service
        return primaryService.getData();
    }
    
    private String performFallbackOperation() throws FallbackServiceException {
        // Call fallback service or use cached data
        return fallbackService.getCachedData();
    }
}
```

**Usage Scenario**: API calls with backup services, cached data fallback

### Pattern 4: Compensating Transaction

Undo previous operations when error occurs:

```java
public class CompensatingStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        List<String> completedOperations = getCompletedOperations(vars);
        
        try {
            // Perform operation
            performOperation();
            
            // Track for potential compensation
            completedOperations.add("operation_xyz");
            vars.setValue(
                "completed_operations",
                WorkflowVariableType.LIST_OF_OBJECT,
                completedOperations
            );
            
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            log.error("Operation failed, compensating previous operations");
            
            // Compensate all previous operations in reverse order
            for (int i = completedOperations.size() - 1; i >= 0; i--) {
                String operation = completedOperations.get(i);
                try {
                    compensateOperation(operation);
                    log.info("Compensated operation: {}", operation);
                } catch (Exception ce) {
                    log.error("Failed to compensate operation {}: {}", 
                        operation, ce.getMessage());
                }
            }
            
            // Clear completed operations
            vars.setValue(
                "completed_operations",
                WorkflowVariableType.LIST_OF_OBJECT,
                new ArrayList<>()
            );
            
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                "Operation failed, compensating transactions completed",
                "compensation_review_queue"
            );
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<String> getCompletedOperations(WorkflowVariables vars) {
        List<String> ops = (List<String>) vars.getValue(
            "completed_operations",
            WorkflowVariableType.LIST_OF_OBJECT
        );
        return ops != null ? new ArrayList<>(ops) : new ArrayList<>();
    }
    
    private void compensateOperation(String operation) {
        switch (operation) {
            case "reserve_inventory":
                inventoryService.releaseReservation(context.getCaseId());
                break;
            case "charge_payment":
                paymentService.refund(context.getCaseId());
                break;
            case "send_confirmation":
                notificationService.sendCancellation(context.getCaseId());
                break;
            default:
                log.warn("No compensation handler for operation: {}", operation);
        }
    }
}
```

**Usage Scenario**: Multi-step transactions, distributed operations

### Pattern 5: Error Aggregation in Parallel Routes

Handle errors from multiple parallel branches:

```java
public class ParallelErrorAggregator implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        List<String> errors = new ArrayList<>();
        
        // Check for errors from each parallel branch
        Boolean branch1Error = vars.getBoolean("branch_1_error");
        if (Boolean.TRUE.equals(branch1Error)) {
            String errorMsg = vars.getString("branch_1_error_msg");
            errors.add("Branch 1: " + errorMsg);
        }
        
        Boolean branch2Error = vars.getBoolean("branch_2_error");
        if (Boolean.TRUE.equals(branch2Error)) {
            String errorMsg = vars.getString("branch_2_error_msg");
            errors.add("Branch 2: " + errorMsg);
        }
        
        Boolean branch3Error = vars.getBoolean("branch_3_error");
        if (Boolean.TRUE.equals(branch3Error)) {
            String errorMsg = vars.getString("branch_3_error_msg");
            errors.add("Branch 3: " + errorMsg);
        }
        
        if (!errors.isEmpty()) {
            // At least one branch had error
            String aggregatedError = String.join("; ", errors);
            log.error("Parallel processing errors: {}", aggregatedError);
            
            vars.setValue(
                "parallel_errors",
                WorkflowVariableType.STRING,
                aggregatedError
            );
            
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                "Multiple branches failed: " + aggregatedError,
                "parallel_error_queue"
            );
        }
        
        // All branches succeeded
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

**Usage in parallel branch**:
```java
public class ParallelBranchStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        String branchName = extractBranchName(context.getExecPathName());
        
        try {
            processBranch();
            
            // Mark success
            context.getProcessVariables().setValue(
                branchName + "_error",
                WorkflowVariableType.BOOLEAN,
                false
            );
            
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            log.error("Branch {} failed: {}", branchName, e.getMessage());
            
            // Mark error for aggregation
            context.getProcessVariables().setValue(
                branchName + "_error",
                WorkflowVariableType.BOOLEAN,
                true
            );
            context.getProcessVariables().setValue(
                branchName + "_error_msg",
                WorkflowVariableType.STRING,
                e.getMessage()
            );
            
            // Continue to join point
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
    }
}
```

### Pattern 6: Graceful Degradation

Continue with reduced functionality when errors occur:

```java
public class GracefulDegradationStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        int successfulOperations = 0;
        List<String> failedOperations = new ArrayList<>();
        
        // Try operation 1 (critical)
        try {
            performCriticalOperation();
            successfulOperations++;
        } catch (Exception e) {
            log.error("Critical operation failed: {}", e.getMessage());
            // Critical failure - cannot continue
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                "Critical operation failed",
                "critical_error_queue"
            );
        }
        
        // Try operation 2 (important but not critical)
        try {
            performImportantOperation();
            successfulOperations++;
        } catch (Exception e) {
            log.warn("Important operation failed: {}", e.getMessage());
            failedOperations.add("Important operation");
            // Continue with degraded service
        }
        
        // Try operation 3 (nice to have)
        try {
            performOptionalOperation();
            successfulOperations++;
        } catch (Exception e) {
            log.warn("Optional operation failed: {}", e.getMessage());
            failedOperations.add("Optional operation");
            // Continue
        }
        
        // Store degradation info
        vars.setValue(
            "service_degraded",
            WorkflowVariableType.BOOLEAN,
            !failedOperations.isEmpty()
        );
        
        if (!failedOperations.isEmpty()) {
            vars.setValue(
                "failed_operations",
                WorkflowVariableType.STRING,
                String.join(", ", failedOperations)
            );
            log.info("Continuing with degraded service. Failed: {}", failedOperations);
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### Pattern 7: Error Context Enrichment

Add context to errors for better debugging:

```java
public class ContextEnrichedStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Build error context
        Map<String, Object> errorContext = new HashMap<>();
        errorContext.put("case_id", context.getCaseId());
        errorContext.put("step_name", getCurrentStepName());
        errorContext.put("execution_path", context.getExecPathName());
        errorContext.put("timestamp", System.currentTimeMillis());
        errorContext.put("user_id", vars.getString("user_id"));
        errorContext.put("order_id", vars.getString("order_id"));
        errorContext.put("amount", vars.getDouble("order_amount"));
        
        try {
            performOperation();
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            // Enrich error with context
            errorContext.put("error_type", e.getClass().getSimpleName());
            errorContext.put("error_message", e.getMessage());
            errorContext.put("stack_trace", getStackTraceAsString(e));
            
            // Store enriched context
            vars.setValue(
                "error_context",
                WorkflowVariableType.OBJECT,
                errorContext
            );
            
            // Log with full context
            log.error("Operation failed with context: {}", errorContext);
            
            // Send to monitoring/alerting
            sendToMonitoring(errorContext);
            
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                formatErrorMessage(e, errorContext),
                "enriched_error_queue"
            );
        }
    }
    
    private String formatErrorMessage(Exception e, Map<String, Object> context) {
        return String.format(
            "Error in step %s for case %s: %s",
            context.get("step_name"),
            context.get("case_id"),
            e.getMessage()
        );
    }
    
    private String getStackTraceAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
```

## ErrorMap Usage

### Defining Error Codes

```java
public class ApplicationErrorCodes {
    
    public static void initialize() {
        // Validation errors (1000-1999)
        ErrorMap.errors.put(1001, "Order validation failed: {0}");
        ErrorMap.errors.put(1002, "Invalid customer data: {0}");
        ErrorMap.errors.put(1003, "Missing required field: {0}");
        
        // Business logic errors (2000-2999)
        ErrorMap.errors.put(2001, "Insufficient inventory for item: {0}");
        ErrorMap.errors.put(2002, "Payment declined: {0}");
        ErrorMap.errors.put(2003, "Customer not eligible: {0}");
        
        // External service errors (3000-3999)
        ErrorMap.errors.put(3001, "Payment service unavailable");
        ErrorMap.errors.put(3002, "Inventory service timeout");
        ErrorMap.errors.put(3003, "Shipping service error: {0}");
        
        // System errors (4000-4999)
        ErrorMap.errors.put(4001, "Database connection failed");
        ErrorMap.errors.put(4002, "Configuration error: {0}");
        ErrorMap.errors.put(4003, "Internal system error");
    }
}

// Initialize at application startup
ApplicationErrorCodes.initialize();
```

### Using ErrorMap in Steps

```java
public class ValidationStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        Order order = getOrder();
        
        // Validate with error codes
        if (order.getCustomerId() == null) {
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                ErrorMap.getErrorDesc(1003, "customer_id"),
                "validation_queue",
                1003
            );
        }
        
        if (order.getItems().isEmpty()) {
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                ErrorMap.getErrorDesc(1001, "No items in order"),
                "validation_queue",
                1001
            );
        }
        
        // Check inventory
        for (OrderItem item : order.getItems()) {
            if (!inventoryService.isAvailable(item.getSku(), item.getQuantity())) {
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    ErrorMap.getErrorDesc(2001, item.getSku()),
                    "inventory_error_queue",
                    2001
                );
            }
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### Error Code Hierarchy

```java
public class ErrorCodeHierarchy {
    
    // Error code ranges
    public static final int VALIDATION_ERROR_BASE = 1000;
    public static final int BUSINESS_ERROR_BASE = 2000;
    public static final int EXTERNAL_SERVICE_ERROR_BASE = 3000;
    public static final int SYSTEM_ERROR_BASE = 4000;
    public static final int FATAL_ERROR_BASE = 9000;
    
    public static ErrorCategory getCategory(int errorCode) {
        if (errorCode >= 1000 && errorCode < 2000) {
            return ErrorCategory.VALIDATION;
        } else if (errorCode >= 2000 && errorCode < 3000) {
            return ErrorCategory.BUSINESS;
        } else if (errorCode >= 3000 && errorCode < 4000) {
            return ErrorCategory.EXTERNAL_SERVICE;
        } else if (errorCode >= 4000 && errorCode < 9000) {
            return ErrorCategory.SYSTEM;
        } else if (errorCode >= 9000) {
            return ErrorCategory.FATAL;
        }
        return ErrorCategory.UNKNOWN;
    }
    
    public static boolean isRetryable(int errorCode) {
        ErrorCategory category = getCategory(errorCode);
        // External service and some system errors are retryable
        return category == ErrorCategory.EXTERNAL_SERVICE ||
               (category == ErrorCategory.SYSTEM && errorCode != 4002);
    }
    
    public static boolean requiresEscalation(int errorCode) {
        return errorCode >= 9000; // Fatal errors
    }
    
    enum ErrorCategory {
        VALIDATION,
        BUSINESS,
        EXTERNAL_SERVICE,
        SYSTEM,
        FATAL,
        UNKNOWN
    }
}
```

### Using Error Categories for Routing

```java
public class ErrorRoutingStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        Integer errorCode = vars.getInteger("error_code");
        
        if (errorCode == null) {
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
        
        ErrorCodeHierarchy.ErrorCategory category = 
            ErrorCodeHierarchy.getCategory(errorCode);
        
        String workbasket;
        switch (category) {
            case VALIDATION:
                workbasket = "validation_review_queue";
                break;
            case BUSINESS:
                workbasket = "business_review_queue";
                break;
            case EXTERNAL_SERVICE:
                workbasket = "service_retry_queue";
                break;
            case SYSTEM:
                workbasket = "technical_support_queue";
                break;
            case FATAL:
                workbasket = "escalation_queue";
                break;
            default:
                workbasket = "general_error_queue";
        }
        
        return new TaskResponse(
            StepResponseType.ERROR_PEND,
            ErrorMap.getErrorDesc(errorCode),
            workbasket,
            errorCode
        );
    }}
```

## Recovery Strategies

### Strategy 1: Automatic Retry with Delay

```java
public class AutoRetryStep implements InvokableTask {
    
    private static final String RETRY_SCHEDULE_KEY = "auto_retry_schedule";
    private static final String NEXT_RETRY_TIME_KEY = "next_retry_time";
    private static final int[] RETRY_DELAYS_SECONDS = {30, 60, 300, 900, 3600}; // 30s, 1m, 5m, 15m, 1h
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Check if this is a retry attempt
        Long nextRetryTime = vars.getLong(NEXT_RETRY_TIME_KEY);
        if (nextRetryTime != null && System.currentTimeMillis() < nextRetryTime) {
            // Too early to retry - pend again
            long remainingSeconds = (nextRetryTime - System.currentTimeMillis()) / 1000;
            return new TaskResponse(
                StepResponseType.OK_PEND_EOR,
                "Waiting for retry schedule. Retry in " + remainingSeconds + " seconds",
                "scheduled_retry_queue"
            );
        }
        
        try {
            // Attempt operation
            performOperation();
            
            // Success - clear retry state
            vars.setValue(RETRY_SCHEDULE_KEY, WorkflowVariableType.INTEGER, null);
            vars.setValue(NEXT_RETRY_TIME_KEY, WorkflowVariableType.LONG, null);
            
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (RetryableException e) {
            // Get current retry attempt
            Integer retryAttempt = vars.getInteger(RETRY_SCHEDULE_KEY);
            if (retryAttempt == null) {
                retryAttempt = 0;
            }
            
            if (retryAttempt >= RETRY_DELAYS_SECONDS.length) {
                // Max retries exceeded
                log.error("Max auto-retries exceeded for case {}", context.getCaseId());
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    "Maximum retry attempts exceeded",
                    "max_retry_exceeded_queue",
                    5001
                );
            }
            
            // Schedule next retry
            int delaySeconds = RETRY_DELAYS_SECONDS[retryAttempt];
            long nextRetry = System.currentTimeMillis() + (delaySeconds * 1000L);
            
            vars.setValue(RETRY_SCHEDULE_KEY, WorkflowVariableType.INTEGER, retryAttempt + 1);
            vars.setValue(NEXT_RETRY_TIME_KEY, WorkflowVariableType.LONG, nextRetry);
            
            log.info("Scheduling retry {} of {} after {}s for case {}",
                retryAttempt + 1,
                RETRY_DELAYS_SECONDS.length,
                delaySeconds,
                context.getCaseId()
            );
            
            return new TaskResponse(
                StepResponseType.OK_PEND_EOR,
                "Retry scheduled in " + delaySeconds + " seconds",
                "scheduled_retry_queue"
            );
        }
    }
}
```

**Scheduler Component**:
```java
@Component
public class RetryScheduler {
    
    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private CommonService dao;
    
    @Scheduled(fixedDelay = 10000) // Check every 10 seconds
    public void processScheduledRetries() {
        List<String> retryQueue = getWorkbasketItems("scheduled_retry_queue");
        long now = System.currentTimeMillis();
        
        for (String caseId : retryQueue) {
            WorkflowInfo info = dao.get(
                WorkflowInfo.class,
                "workflow_process_info-" + caseId
            );
            
            if (info != null) {
                Document doc = loadWorkflowInfoDocument(caseId);
                Long nextRetryTime = doc.getLong("$.process_info.process_variables[?(@.name=='next_retry_time')].value_long");
                
                if (nextRetryTime != null && now >= nextRetryTime) {
                    log.info("Resuming scheduled retry for case {}", caseId);
                    try {
                        runtimeService.resumeCase(caseId);
                    } catch (Exception e) {
                        log.error("Failed to resume case {}: {}", caseId, e.getMessage());
                    }
                }
            }
        }
    }
}
```

### Strategy 2: Manual Intervention with Resolution Options

```java
public class ManualInterventionStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Check if this is after manual intervention
        String resolution = vars.getString("manual_resolution");
        
        if (resolution != null) {
            switch (resolution) {
                case "RETRY":
                    return handleRetry(vars);
                case "SKIP":
                    return handleSkip(vars);
                case "ABORT":
                    return handleAbort(vars);
                case "ESCALATE":
                    return handleEscalate(vars);
                default:
                    log.warn("Unknown resolution: {}", resolution);
            }
        }
        
        try {
            performOperation();
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            log.error("Operation failed, requiring manual intervention: {}", e.getMessage());
            
            // Store error details for operator
            vars.setValue("error_message", WorkflowVariableType.STRING, e.getMessage());
            vars.setValue("error_timestamp", WorkflowVariableType.LONG, System.currentTimeMillis());
            vars.setValue("error_details", WorkflowVariableType.OBJECT, buildErrorDetails(e));
            
            // Present resolution options to operator
            vars.setValue("resolution_options", WorkflowVariableType.STRING, 
                "RETRY,SKIP,ABORT,ESCALATE");
            
            return new TaskResponse(
                StepResponseType.OK_PEND_EOR,
                "Manual intervention required: " + e.getMessage(),
                "manual_intervention_queue"
            );
        }
    }
    
    private TaskResponse handleRetry(WorkflowVariables vars) {
        log.info("Manual resolution: RETRY");
        vars.setValue("manual_resolution", WorkflowVariableType.STRING, null);
        
        try {
            performOperation();
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        } catch (Exception e) {
            vars.setValue("error_message", WorkflowVariableType.STRING, 
                "Retry failed: " + e.getMessage());
            return new TaskResponse(
                StepResponseType.OK_PEND_EOR,
                "Retry failed, awaiting further instruction",
                "manual_intervention_queue"
            );
        }
    }
    
    private TaskResponse handleSkip(WorkflowVariables vars) {
        log.info("Manual resolution: SKIP");
        vars.setValue("manual_resolution", WorkflowVariableType.STRING, null);
        vars.setValue("step_skipped", WorkflowVariableType.BOOLEAN, true);
        vars.setValue("skip_reason", WorkflowVariableType.STRING, 
            vars.getString("skip_comment"));
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
    
    private TaskResponse handleAbort(WorkflowVariables vars) {
        log.info("Manual resolution: ABORT");
        vars.setValue("manual_resolution", WorkflowVariableType.STRING, null);
        vars.setValue("manually_aborted", WorkflowVariableType.BOOLEAN, true);
        vars.setValue("abort_reason", WorkflowVariableType.STRING, 
            vars.getString("abort_comment"));
        
        // Jump to cleanup/cancellation flow
        return new TaskResponse(
            StepResponseType.OK_PROCEED,
            "",
            "",
            "cancellation_step"
        );
    }
    
    private TaskResponse handleEscalate(WorkflowVariables vars) {
        log.info("Manual resolution: ESCALATE");
        vars.setValue("manual_resolution", WorkflowVariableType.STRING, null);
        vars.setValue("escalated", WorkflowVariableType.BOOLEAN, true);
        vars.setValue("escalation_reason", WorkflowVariableType.STRING, 
            vars.getString("escalation_comment"));
        
        return new TaskResponse(
            StepResponseType.ERROR_PEND,
            "Escalated by operator",
            "escalation_queue"
        );
    }
    
    private Map<String, Object> buildErrorDetails(Exception e) {
        Map<String, Object> details = new HashMap<>();
        details.put("exception_type", e.getClass().getName());
        details.put("message", e.getMessage());
        details.put("cause", e.getCause() != null ? e.getCause().toString() : null);
        return details;
    }
}
```

### Strategy 3: Dead Letter Queue Pattern

```java
public class DeadLetterQueueHandler implements InvokableTask {
    
    private static final int MAX_PROCESSING_ATTEMPTS = 5;
    private static final String PROCESSING_ATTEMPTS_KEY = "dlq_processing_attempts";
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        Integer attempts = vars.getInteger(PROCESSING_ATTEMPTS_KEY);
        if (attempts == null) {
            attempts = 0;
        }
        
        try {
            // Attempt to process
            performOperation();
            
            // Success - clear DLQ state
            vars.setValue(PROCESSING_ATTEMPTS_KEY, WorkflowVariableType.INTEGER, null);
            vars.setValue("in_dlq", WorkflowVariableType.BOOLEAN, false);
            
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            attempts++;
            vars.setValue(PROCESSING_ATTEMPTS_KEY, WorkflowVariableType.INTEGER, attempts);
            
            if (attempts >= MAX_PROCESSING_ATTEMPTS) {
                // Move to dead letter queue
                log.error("Moving case {} to dead letter queue after {} attempts",
                    context.getCaseId(), attempts);
                
                vars.setValue("in_dlq", WorkflowVariableType.BOOLEAN, true);
                vars.setValue("dlq_timestamp", WorkflowVariableType.LONG, 
                    System.currentTimeMillis());
                vars.setValue("dlq_reason", WorkflowVariableType.STRING, 
                    "Max processing attempts exceeded: " + e.getMessage());
                
                // Alert operations team
                sendDLQAlert(context.getCaseId(), e);
                
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    "Moved to dead letter queue",
                    "dead_letter_queue",
                    9001
                );
            }
            
            // Retry
            log.warn("Processing attempt {} of {} failed for case {}",
                attempts, MAX_PROCESSING_ATTEMPTS, context.getCaseId());
            
            return new TaskResponse(
                StepResponseType.OK_PEND_EOR,
                "Processing failed, attempt " + attempts,
                "retry_queue"
            );
        }
    }
    
    private void sendDLQAlert(String caseId, Exception e) {
        // Send alert to operations team
        alertService.sendAlert(
            "Case moved to DLQ",
            String.format("Case %s moved to dead letter queue. Error: %s", 
                caseId, e.getMessage())
        );
    }
}
```

### Strategy 4: Saga Pattern for Distributed Transactions

```java
public class SagaCoordinator implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Define saga steps
        List<SagaStep> sagaSteps = Arrays.asList(
            new SagaStep("reserve_inventory", this::reserveInventory, this::releaseInventory),
            new SagaStep("charge_payment", this::chargePayment, this::refundPayment),
            new SagaStep("reserve_shipping", this::reserveShipping, this::cancelShipping),
            new SagaStep("send_confirmation", this::sendConfirmation, this::sendCancellation)
        );
        
        // Track completed steps for compensation
        @SuppressWarnings("unchecked")
        List<String> completedSteps = (List<String>) vars.getValue(
            "saga_completed_steps",
            WorkflowVariableType.LIST_OF_OBJECT
        );
        if (completedSteps == null) {
            completedSteps = new ArrayList<>();
        }
        
        // Execute saga steps
        for (SagaStep step : sagaSteps) {
            if (completedSteps.contains(step.getName())) {
                // Already completed
                continue;
            }
            
            try {
                log.info("Executing saga step: {}", step.getName());
                step.execute();
                
                // Mark as completed
                completedSteps.add(step.getName());
                vars.setValue("saga_completed_steps", 
                    WorkflowVariableType.LIST_OF_OBJECT, completedSteps);
                
            } catch (Exception e) {
                log.error("Saga step {} failed, compensating", step.getName());
                
                // Compensate all completed steps in reverse order
                compensateSaga(sagaSteps, completedSteps);
                
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    "Saga failed at step: " + step.getName() + ", compensated",
                    "saga_failure_queue",
                    6001
                );
            }
        }
        
        // All steps completed successfully
        vars.setValue("saga_completed_steps", WorkflowVariableType.LIST_OF_OBJECT, null);
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
    
    private void compensateSaga(List<SagaStep> allSteps, List<String> completedSteps) {
        // Compensate in reverse order
        for (int i = completedSteps.size() - 1; i >= 0; i--) {
            String stepName = completedSteps.get(i);
            
            SagaStep step = allSteps.stream()
                .filter(s -> s.getName().equals(stepName))
                .findFirst()
                .orElse(null);
            
            if (step != null) {
                try {
                    log.info("Compensating saga step: {}", stepName);
                    step.compensate();
                } catch (Exception e) {
                    log.error("Failed to compensate step {}: {}", stepName, e.getMessage());
                    // Continue compensating other steps
                }
            }
        }
    }
    
    // Business operations
    private void reserveInventory() throws Exception {
        inventoryService.reserve(context.getCaseId());
    }
    
    private void releaseInventory() throws Exception {
        inventoryService.release(context.getCaseId());
    }
    
    private void chargePayment() throws Exception {
        paymentService.charge(context.getCaseId());
    }
    
    private void refundPayment() throws Exception {
        paymentService.refund(context.getCaseId());
    }
    
    private void reserveShipping() throws Exception {
        shippingService.reserve(context.getCaseId());
    }
    
    private void cancelShipping() throws Exception {
        shippingService.cancel(context.getCaseId());
    }
    
    private void sendConfirmation() throws Exception {
        notificationService.sendConfirmation(context.getCaseId());
    }
    
    private void sendCancellation() throws Exception {
        notificationService.sendCancellation(context.getCaseId());
    }
    
    // Helper class
    private static class SagaStep {
        private final String name;
        private final Executable executeFunc;
        private final Executable compensateFunc;
        
        public SagaStep(String name, Executable executeFunc, Executable compensateFunc) {
            this.name = name;
            this.executeFunc = executeFunc;
            this.compensateFunc = compensateFunc;
        }
        
        public String getName() { return name; }
        public void execute() throws Exception { executeFunc.execute(); }
        public void compensate() throws Exception { compensateFunc.execute(); }
    }
    
    @FunctionalInterface
    private interface Executable {
        void execute() throws Exception;
    }
}
```

## Error Monitoring and Logging

### Structured Logging

```java
public class StructuredErrorLogger {
    
    private static final Logger log = LoggerFactory.getLogger(StructuredErrorLogger.class);
    
    public static void logError(
        WorkflowContext context,
        Exception e,
        String errorCategory,
        Map<String, Object> additionalContext
    ) {
        Map<String, Object> logContext = new HashMap<>();
        
        // Workflow context
        logContext.put("case_id", context.getCaseId());
        logContext.put("execution_path", context.getExecPathName());
        logContext.put("workflow_definition", context.getWorkflowDefinition().getDefName());
        
        // Error details
        logContext.put("error_category", errorCategory);
        logContext.put("error_type", e.getClass().getSimpleName());
        logContext.put("error_message", e.getMessage());
        logContext.put("timestamp", Instant.now().toString());
        
        // Stack trace (first 5 elements)
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace.length > 0) {
            List<String> topStack = Arrays.stream(stackTrace)
                .limit(5)
                .map(StackTraceElement::toString)
                .collect(Collectors.toList());
            logContext.put("stack_trace_top", topStack);
        }
        
        // Process variables (relevant ones)
        WorkflowVariables vars = context.getProcessVariables();
        logContext.put("order_id", vars.getString("order_id"));
        logContext.put("customer_id", vars.getString("customer_id"));
        logContext.put("order_amount", vars.getDouble("order_amount"));
        
        // Additional context
        if (additionalContext != null) {
            logContext.putAll(additionalContext);
        }
        
        // Log as JSON for easy parsing
        log.error("Workflow error: {}", toJson(logContext), e);
    }
    
    private static String toJson(Map<String, Object> map) {
        try {
            return new ObjectMapper().writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return map.toString();
        }
    }
}
```

**Usage**:
```java
public class MonitoredStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        try {
            performOperation();
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            Map<String, Object> context = new HashMap<>();
            context.put("operation_type", "payment_processing");
            context.put("retry_count", getRetryCount());
            
            StructuredErrorLogger.logError(
                getContext(),
                e,
                "PAYMENT_ERROR",
                context
            );
            
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "payment_error_queue"
            );
        }
    }
}
```

### Metrics Collection

```java
@Component
public class ErrorMetrics {
    
    private final Counter errorCounter;
    private final Counter errorByCategory;
    private final Timer errorResolutionTime;
    
    public ErrorMetrics(MeterRegistry registry) {
        this.errorCounter = Counter.builder("workflow.errors.total")
            .description("Total workflow errors")
            .register(registry);
        
        this.errorByCategory = Counter.builder("workflow.errors.by_category")
            .description("Workflow errors by category")
            .tag("category", "unknown")
            .register(registry);
        
        this.errorResolutionTime = Timer.builder("workflow.error.resolution.time")
            .description("Time to resolve errors")
            .register(registry);
    }
    
    public void recordError(String category, int errorCode) {
        errorCounter.increment();
        
        Counter.builder("workflow.errors.by_category")
            .tag("category", category)
            .tag("error_code", String.valueOf(errorCode))
            .register(meterRegistry)
            .increment();
    }
    
    public void recordErrorResolution(String caseId, long durationMs) {
        errorResolutionTime.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
```

### Error Dashboard Data

```java
@RestController
@RequestMapping("/api/errors")
public class ErrorDashboardController {
    
    @Autowired
    private CommonService dao;
    
    @GetMapping("/active")
    public List<ErrorSummary> getActiveErrors() {
        // Get all cases in error state
        List<WorkflowInfo> allCases = dao.getAll(WorkflowInfo.class);
        
        return allCases.stream()
            .filter(info -> !info.getIsComplete())
            .filter(info -> hasError(info))
            .map(this::toErrorSummary)
            .collect(Collectors.toList());
    }
    
    @GetMapping("/stats")
    public ErrorStats getErrorStats() {
        List<WorkflowInfo> allCases = dao.getAll(WorkflowInfo.class);
        
        Map<String, Long> errorsByWorkbasket = allCases.stream()
            .filter(this::hasError)
            .collect(Collectors.groupingBy(
                this::getErrorWorkbasket,
                Collectors.counting()
            ));
        
        Map<Integer, Long> errorsByCode = allCases.stream()
            .filter(this::hasError)
            .collect(Collectors.groupingBy(
                this::getErrorCode,
                Collectors.counting()
            ));
        
        return new ErrorStats(errorsByWorkbasket, errorsByCode);
    }
    
    private boolean hasError(WorkflowInfo info) {
        return info.getExecPaths().stream()
            .anyMatch(p -> p.getErrorCode() != null && p.getErrorCode() != 0);
    }
    
    private ErrorSummary toErrorSummary(WorkflowInfo info) {
        ExecPath errorPath = info.getExecPaths().stream()
            .filter(p -> p.getErrorCode() != null && p.getErrorCode() != 0)
            .findFirst()
            .orElse(null);
        
        if (errorPath == null) {
            return null;
        }
        
        return new ErrorSummary(
            info.getCaseId(),
            errorPath.getErrorCode(),
            errorPath.getErrorDesc(),
            errorPath.getPendWorkBasket(),
            info.getTimestamp()
        );
    }
    
    private String getErrorWorkbasket(WorkflowInfo info) {
        return info.getExecPaths().stream()
            .filter(p -> p.getErrorCode() != null)
            .map(ExecPath::getPendWorkBasket)
            .findFirst()
            .orElse("unknown");
    }
    
    private Integer getErrorCode(WorkflowInfo info) {
        return info.getExecPaths().stream()
            .filter(p -> p.getErrorCode() != null)
            .map(ExecPath::getErrorCode)
            .findFirst()
            .orElse(0);
    }
}
```

## Testing Error Scenarios

### Unit Testing Error Handling

```java
public class ErrorHandlingTest {
    
    private WorkflowContext context;
    private PaymentProcessingStep step;
    
    @Before
    public void setup() {
        context = TestManager.createTestContext("test-case-1");
        step = new PaymentProcessingStep(context, mockPaymentService);
    }
    
    @Test
    public void testServiceUnavailableError() {
        // Arrange
        when(mockPaymentService.charge(anyString()))
            .thenThrow(new ServiceUnavailableException("Service down"));
        
        // Act
        TaskResponse response = step.executeStep();
        
        // Assert
        assertEquals(StepResponseType.ERROR_PEND, response.getUnitResponseType());
        assertEquals("payment_retry_queue", response.getWorkbasket());
        assertTrue(response.getErrorDesc().contains("Service down"));
    }
    
    @Test
    public void testRetrySucceedsAfterFailure() {
        // Arrange - fail first, succeed second
        when(mockPaymentService.charge(anyString()))
            .thenThrow(new ServiceUnavailableException("Temporary failure"))
            .thenReturn(new PaymentResult("success"));
        
        // Act - first attempt
        TaskResponse response1 = step.executeStep();
        assertEquals(StepResponseType.ERROR_PEND, response1.getUnitResponseType());
        
        // Act - retry
        TaskResponse response2 = step.executeStep();
        
        // Assert
        assertEquals(StepResponseType.OK_PROCEED, response2.getUnitResponseType());
        verify(mockPaymentService, times(2)).charge(anyString());
    }
    
    @Test
    public void testMaxRetriesExceeded() {
        // Arrange
        RetryableStep retryStep = new RetryableStep(context);
        when(mockService.perform()).thenThrow(new TransientException());
        
        // Act - exceed max retries
        for (int i = 0; i < 4; i++) {
            TaskResponse response = retryStep.executeStep();
            if (i < 3) {
                assertEquals(StepResponseType.OK_PEND_EOR, response.getUnitResponseType());
            } else {
                assertEquals(StepResponseType.ERROR_PEND, response.getUnitResponseType());
                assertEquals("max_retry_exceeded_queue", response.getWorkbasket());
            }
        }
    }
    
    @Test
    public void testCompensationOnError() {
        // Arrange
        CompensatingStep compStep = new CompensatingStep(context);
        
        // Simulate previous operations
        context.getProcessVariables().setValue(
            "completed_operations",
            WorkflowVariableType.LIST_OF_OBJECT,
            Arrays.asList("op1", "op2", "op3")
        );
        
        when(mockService.perform()).thenThrow(new RuntimeException("Operation failed"));
        
        // Act
        TaskResponse response = compStep.executeStep();
        
        // Assert
        assertEquals(StepResponseType.ERROR_PEND, response.getUnitResponseType());
        verify(mockService).compensate("op3");
        verify(mockService).compensate("op2");
        verify(mockService).compensate("op1");
    }
}
```

### Integration Testing

```java
@SpringBootTest
public class ErrorHandlingIntegrationTest {
    
    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private CommonService dao;
    
    @Test
    public void testErrorPendAndResume() {
        // Start workflow
        String caseId = "error-test-1";
        String workflowJson = loadWorkflowWithErrorStep();
        
        runtimeService.startCase(caseId, workflowJson, null, null);
        
        // Verify workflow pended with error
        WorkflowInfo info = dao.get(
            WorkflowInfo.class,
            "workflow_process_info-" + caseId
        );
        
        assertFalse(info.getIsComplete());
        ExecPath errorPath = info.getExecPaths().stream()
            .filter(p -> p.getErrorCode() != null && p.getErrorCode() != 0)
            .findFirst()
            .orElse(null);
        
        assertNotNull(errorPath);
        assertEquals("error_queue", errorPath.getPendWorkbasket());
        
        // Fix the error condition
        fixErrorCondition(caseId);
        
        // Resume
        runtimeService.resumeCase(caseId);
        
        // Verify completion
        info = dao.get(WorkflowInfo.class, "workflow_process_info-" + caseId);
        assertTrue(info.getIsComplete());
    }
}
```

## Best Practices

### 1. Error Classification

✅ **DO**: Classify errors clearly
```java
public enum ErrorSeverity {
    LOW,      // Warning, can continue
    MEDIUM,   // Requires attention but not urgent
    HIGH,     // Needs immediate attention
    CRITICAL  // System stability at risk
}

public class ClassifiedError {
    private int errorCode;
    private ErrorSeverity severity;
    private boolean isRetryable;
    private String category;
}
```

❌ **DON'T**: Treat all errors the same way

### 2. Fail Fast

✅ **DO**: Validate early
```java
@Override
public TaskResponse executeStep() {
    // Validate first
    ValidationResult validation = validate();
    if (!validation.isValid()) {
        return new TaskResponse(
            StepResponseType.ERROR_PEND,
            validation.getErrorMessage(),
            "validation_queue"
        );
    }
    
    // Then process
    performOperation();
}
```

❌ **DON'T**: Process invalid data and fail later

### 3. Provide Context

✅ **DO**: Include helpful error information
```java
return new TaskResponse(
    StepResponseType.ERROR_PEND,
    String.format(
        "Payment failed for order %s: %s. Amount: $%.2f, Method: %s",
        orderId, error.getMessage(), amount, paymentMethod
    ),
    "payment_error_queue",
    3002
);
```

❌ **DON'T**: Use generic error messages
```java
return new TaskResponse(
    StepResponseType.ERROR_PEND,
    "Error occurred",
    "error_queue"
);
```

### 4. Idempotent Error Handling

✅ **DO**: Make error recovery idempotent
```java
@Override
public TaskResponse executeStep() {
    // Check if already processed
    Boolean processed = context.getProcessVariables()
        .getBoolean("payment_processed");
    if (Boolean.TRUE.equals(processed)) {
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
    
    try {
        processPayment();
        context.getProcessVariables().setValue(
            "payment_processed",
            WorkflowVariableType.BOOLEAN,
            true
        );
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    } catch (Exception e) {
        // Error handling...
    }
}
```

### 5. Log Before Pending

✅ **DO**: Log error details before pending
```java
try {
    performOperation();
} catch (Exception e) {
    log.error("Operation failed for case {}: {}", 
        context.getCaseId(), e.getMessage(), e);
    
    // Store error details
    context.getProcessVariables().setValue(
        "error_details",
        WorkflowVariableType.OBJECT,
        buildErrorDetails(e)
    );
    
    return new TaskResponse(
        StepResponseType.ERROR_PEND,
        e.getMessage(),
        "error_queue"
    );
}
```

❌ **DON'T**: Pend without logging
```java
try {
    performOperation();
} catch (Exception e) {
    return new TaskResponse(
        StepResponseType.ERROR_PEND,
        e.getMessage(),
        "error_queue"
    );
}
```

### 6. Avoid Catching Generic Exceptions

✅ **DO**: Catch specific exceptions
```java
try {
    performOperation();
} catch (ValidationException e) {
    // Handle validation errors
} catch (ServiceUnavailableException e) {
    // Handle service errors
} catch (DataNotFoundException e) {
    // Handle missing data
}
```

❌ **DON'T**: Catch all exceptions
```java
try {
    performOperation();
} catch (Exception e) {
    // What kind of error is this?
}
```

### 7. Set Appropriate Work Baskets

✅ **DO**: Route errors to appropriate queues
```java
if (e instanceof ValidationException) {
    workbasket = "validation_review_queue";
} else if (e instanceof ServiceUnavailableException) {
    workbasket = "service_retry_queue";
} else if (e instanceof PaymentDeclinedException) {
    workbasket = "payment_review_queue";
} else {
    workbasket = "general_error_queue";
}
```

❌ **DON'T**: Use single error queue for everything

### 8. Clean Up on Error

✅ **DO**: Release resources on error
```java
Connection conn = null;
try {
    conn = getConnection();
    performDatabaseOperation(conn);
} catch (SQLException e) {
    log.error("Database error: {}", e.getMessage());
    return errorResponse();
} finally {
    if (conn != null) {
        try {
            conn.close();
        } catch (SQLException e) {
            log.warn("Failed to close connection: {}", e.getMessage());
        }
    }
}
```

### 9. Don't Swallow Exceptions

✅ **DO**: Handle or propagate exceptions
```java
try {
    performOperation();
} catch (CriticalException e) {
    log.error("Critical error: {}", e.getMessage(), e);
    throw new WorkflowRuntimeException("Critical error", e);
}
```

❌ **DON'T**: Swallow exceptions silently
```java
try {
    performOperation();
} catch (Exception e) {
    // Silent failure - very bad!
}
```

### 10. Test Error Paths

✅ **DO**: Write tests for error scenarios
```java
@Test
public void testHandlesServiceUnavailable() {
    when(service.call()).thenThrow(ServiceUnavailableException.class);
    TaskResponse response = step.executeStep();
    assertEquals(StepResponseType.ERROR_PEND, response.getUnitResponseType());
}

@Test
public void testRetriesAfterTransientError() {
    // Test retry logic
}

@Test
public void testCompensatesOnFailure() {
    // Test compensation logic
}
```

## Real-World Examples

### Example 1: E-Commerce Order Processing

```java
public class OrderProcessingWorkflow {
    
    // Step 1: Validate order
    public static class ValidateOrderStep implements InvokableTask {
        @Override
        public TaskResponse executeStep() {
            WorkflowVariables vars = context.getProcessVariables();
            Order order = getOrder(vars);
            
            // Validation errors - business errors
            if (order.getItems().isEmpty()) {
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    ErrorMap.getErrorDesc(1001, "Order has no items"),
                    "order_validation_queue",
                    1001
                );
            }
            
            if (order.getTotalAmount() > getCustomerCreditLimit()) {
                vars.setValue("requires_approval", WorkflowVariableType.BOOLEAN, true);
                return new TaskResponse(
                    StepResponseType.OK_PEND,
                    "Order exceeds credit limit, requires approval",
                    "credit_approval_queue"
                );
            }
            
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
    }
    
    // Step 2: Reserve inventory with retry
    public static class ReserveInventoryStep implements InvokableTask {
        private static final int MAX_RETRIES = 3;
        
        @Override
        public TaskResponse executeStep() {
            WorkflowVariables vars = context.getProcessVariables();
            Integer retryCount = vars.getInteger("inventory_retry_count");
            if (retryCount == null) retryCount = 0;
            
            try {
                inventoryService.reserve(context.getCaseId());
                vars.setValue("inventory_reserved", WorkflowVariableType.BOOLEAN, true);
                vars.setValue("inventory_retry_count", WorkflowVariableType.INTEGER, null);
                
                return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
                
            } catch (InsufficientInventoryException e) {
                // Business error - not retryable
                log.warn("Insufficient inventory for case {}", context.getCaseId());
                
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    ErrorMap.getErrorDesc(2001, e.getMessage()),
                    "inventory_shortage_queue",
                    2001
                );
                
            } catch (InventoryServiceException e) {
                // Technical error - retryable
                retryCount++;
                vars.setValue("inventory_retry_count", WorkflowVariableType.INTEGER, retryCount);
                
                if (retryCount >= MAX_RETRIES) {
                    log.error("Inventory service failed after {} retries", MAX_RETRIES);
                    return new TaskResponse(
                        StepResponseType.ERROR_PEND,
                        "Inventory service unavailable after retries",
                        "service_escalation_queue",
                        3002
                    );
                }
                
                log.warn("Inventory service error, retry {} of {}", retryCount, MAX_RETRIES);
                return new TaskResponse(
                    StepResponseType.OK_PEND_EOR,
                    "Retrying inventory reservation",
                    "service_retry_queue"
                );
            }
        }
    }
    
    // Step 3: Process payment with circuit breaker
    public static class ProcessPaymentStep implements InvokableTask {
        @Override
        public TaskResponse executeStep() {
            WorkflowVariables vars = context.getProcessVariables();
            
            // Check if payment already processed (idempotency)
            Boolean processed = vars.getBoolean("payment_processed");
            if (Boolean.TRUE.equals(processed)) {
                log.info("Payment already processed for case {}", context.getCaseId());
                return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            }
            
            // Check circuit breaker
            if (isCircuitOpen(vars)) {
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    "Payment service circuit breaker is open",
                    "payment_circuit_breaker_queue",
                    3001
                );
            }
            
            try {
                PaymentResult result = paymentService.charge(
                    context.getCaseId(),
                    vars.getDouble("order_amount")
                );
                
                // Store payment details
                vars.setValue("payment_processed", WorkflowVariableType.BOOLEAN, true);
                vars.setValue("payment_transaction_id", WorkflowVariableType.STRING, result.getTransactionId());
                
                // Reset circuit breaker on success
                resetCircuitBreaker(vars);
                
                return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
                
            } catch (PaymentDeclinedException e) {
                // Business error - card declined
                log.warn("Payment declined for case {}: {}", context.getCaseId(), e.getReason());
                
                vars.setValue("payment_decline_reason", WorkflowVariableType.STRING, e.getReason());
                
                // Need to release inventory
                compensateInventoryReservation(vars);
                
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    ErrorMap.getErrorDesc(2002, e.getReason()),
                    "payment_declined_queue",
                    2002
                );
                
            } catch (PaymentServiceException e) {
                // Technical error - service issue
                log.error("Payment service error for case {}: {}", context.getCaseId(), e.getMessage());
                
                incrementCircuitBreakerFailures(vars);
                
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    "Payment service error: " + e.getMessage(),
                    "payment_service_error_queue",
                    3001
                );
            }
        }
        
        private boolean isCircuitOpen(WorkflowVariables vars) {
            String state = vars.getString("payment_circuit_state");
            return "OPEN".equals(state);
        }
        
        private void resetCircuitBreaker(WorkflowVariables vars) {
            vars.setValue("payment_circuit_state", WorkflowVariableType.STRING, "CLOSED");
            vars.setValue("payment_circuit_failures", WorkflowVariableType.INTEGER, 0);
        }
        
        private void incrementCircuitBreakerFailures(WorkflowVariables vars) {
            Integer failures = vars.getInteger("payment_circuit_failures");
            failures = (failures == null) ? 1 : failures + 1;
            vars.setValue("payment_circuit_failures", WorkflowVariableType.INTEGER, failures);
            
            if (failures >= 5) {
                vars.setValue("payment_circuit_state", WorkflowVariableType.STRING, "OPEN");
                log.error("Payment service circuit breaker opened after {} failures", failures);
            }
        }
        
        private void compensateInventoryReservation(WorkflowVariables vars) {
            Boolean reserved = vars.getBoolean("inventory_reserved");
            if (Boolean.TRUE.equals(reserved)) {
                try {
                    inventoryService.release(context.getCaseId());
                    vars.setValue("inventory_reserved", WorkflowVariableType.BOOLEAN, false);
                    log.info("Released inventory reservation due to payment failure");
                } catch (Exception e) {
                    log.error("Failed to release inventory: {}", e.getMessage());
                }
            }
        }
    }
    
    // Step 4: Error handler step
    public static class OrderErrorHandlerStep implements InvokableTask {
        @Override
        public TaskResponse executeStep() {
            WorkflowVariables vars = context.getProcessVariables();
            
            // Get error details
            Integer errorCode = vars.getInteger("error_code");
            String errorMessage = vars.getString("error_message");
            
            log.info("Handling error {} for case {}: {}", 
                errorCode, context.getCaseId(), errorMessage);
            
            // Compensate any completed operations
            compensateCompletedOperations(vars);
            
            // Notify customer
            sendErrorNotification(vars, errorCode, errorMessage);
            
            // Create support ticket
            createSupportTicket(vars, errorCode, errorMessage);
            
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
        
        private void compensateCompletedOperations(WorkflowVariables vars) {
            // Release inventory if reserved
            if (Boolean.TRUE.equals(vars.getBoolean("inventory_reserved"))) {
                try {
                    inventoryService.release(context.getCaseId());
                    log.info("Compensated inventory reservation");
                } catch (Exception e) {
                    log.error("Failed to compensate inventory: {}", e.getMessage());
                }
            }
            
            // Refund payment if charged
            if (Boolean.TRUE.equals(vars.getBoolean("payment_processed"))) {
                try {
                    String transactionId = vars.getString("payment_transaction_id");
                    paymentService.refund(transactionId);
                    log.info("Compensated payment");
                } catch (Exception e) {
                    log.error("Failed to compensate payment: {}", e.getMessage());
                }
            }
        }
        
        private void sendErrorNotification(WorkflowVariables vars, Integer errorCode, String errorMessage) {
            String customerEmail = vars.getString("customer_email");
            notificationService.sendOrderErrorEmail(
                customerEmail,
                context.getCaseId(),
                errorCode,
                errorMessage
            );
        }
        
        private void createSupportTicket(WorkflowVariables vars, Integer errorCode, String errorMessage) {
            supportService.createTicket(
                "Order Processing Error",
                String.format("Case: %s, Error: %d - %s", 
                    context.getCaseId(), errorCode, errorMessage),
                "high"
            );
        }
    }
}
```

### Example 2: Document Approval with Error Escalation

```java
public class DocumentApprovalWorkflow {
    
    public static class ApprovalStep implements InvokableTask {
        private static final long APPROVAL_TIMEOUT_MS = 7 * 24 * 60 * 60 * 1000; // 7 days
        
        @Override
        public TaskResponse executeStep() {
            WorkflowVariables vars = context.getProcessVariables();
            
            // Check if timed out
            Long submittedTime = vars.getLong("submitted_timestamp");
            if (submittedTime != null) {
                long elapsed = System.currentTimeMillis() - submittedTime;
                if (elapsed > APPROVAL_TIMEOUT_MS) {
                    log.warn("Approval timeout for case {}", context.getCaseId());
                    
                    // Escalate to manager
                    vars.setValue("escalated", WorkflowVariableType.BOOLEAN, true);
                    vars.setValue("escalation_reason", WorkflowVariableType.STRING, "Approval timeout");
                    
                    return new TaskResponse(
                        StepResponseType.OK_PROCEED,
                        "",
                        "",
                        "escalation_step" // Ticket to escalation
                    );
                }
            }
            
            // Wait for approval
            String approval = vars.getString("approval_decision");
            if (approval == null) {
                // First time - set timestamp
                if (submittedTime == null) {
                    vars.setValue("submitted_timestamp", WorkflowVariableType.LONG, System.currentTimeMillis());
                }
                
                return new TaskResponse(
                    StepResponseType.OK_PEND,
                    "Awaiting approval",
                    "approval_queue"
                );
            }
            
            // Process approval decision
            if ("APPROVED".equals(approval)) {
                return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            } else if ("REJECTED".equals(approval)) {
                return new TaskResponse(
                    StepResponseType.OK_PROCEED,
                    "",
                    "",
                    "rejection_handler_step"
                );
            } else if ("REQUEST_CHANGES".equals(approval)) {
                return new TaskResponse(
                    StepResponseType.OK_PROCEED,
                    "",
                    "",
                    "revision_step"
                );
            }
            
            // Unknown decision
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                "Invalid approval decision: " + approval,
                "approval_error_queue",
                7001
            );
        }
    }
    
    public static class EscalationStep implements InvokableTask {
        @Override
        public TaskResponse executeStep() {
            WorkflowVariables vars = context.getProcessVariables();
            
            String escalationReason = vars.getString("escalation_reason");
            log.info("Escalating case {} due to: {}", context.getCaseId(), escalationReason);
            
            // Notify manager
            String managerEmail = vars.getString("manager_email");
            notificationService.sendEscalationEmail(
                managerEmail,
                context.getCaseId(),
                escalationReason
            );
            
            // Update approval level
            vars.setValue("approval_level", WorkflowVariableType.STRING, "MANAGER");
            
            // Pend for manager approval
            return new TaskResponse(
                StepResponseType.OK_PEND,
                "Escalated to manager",
                "manager_approval_queue"
            );
        }
    }
}
```

### Example 3: Data Processing with Dead Letter Queue

```java
public class DataProcessingWorkflow {
    
    public static class ProcessRecordStep implements InvokableTask {
        private static final int MAX_ATTEMPTS = 5;
        private static final String ATTEMPTS_KEY = "processing_attempts";
        private static final String FAILED_RECORDS_KEY = "failed_records";
        
        @Override
        public TaskResponse executeStep() {
            WorkflowVariables vars = context.getProcessVariables();
            
            // Get current record
            @SuppressWarnings("unchecked")
            List<DataRecord> records = (List<DataRecord>) vars.getValue(
                "records_to_process",
                WorkflowVariableType.LIST_OF_OBJECT
            );
            
            Integer currentIndex = vars.getInteger("current_record_index");
            if (currentIndex == null) currentIndex = 0;
            
            if (currentIndex >= records.size()) {
                // All records processed
                return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            }
            
            DataRecord record = records.get(currentIndex);
            Integer attempts = getAttempts(vars, record.getId());
            
            try {
                // Process record
                processRecord(record);
                
                // Success - move to next
                vars.setValue("current_record_index", WorkflowVariableType.INTEGER, currentIndex + 1);
                clearAttempts(vars, record.getId());
                
                return new TaskResponse(
                    StepResponseType.OK_PEND_EOR, // Re-execute for next record
                    "Record processed",
                    "processing_queue"
                );
                
            } catch (ValidationException e) {
                // Invalid data - skip this record
                log.warn("Invalid record {}: {}", record.getId(), e.getMessage());
                
                addToFailedRecords(vars, record, "VALIDATION_ERROR", e.getMessage());
                vars.setValue("current_record_index", WorkflowVariableType.INTEGER, currentIndex + 1);
                
                return new TaskResponse(
                    StepResponseType.OK_PEND_EOR,
                    "Skipped invalid record",
                    "processing_queue"
                );
                
            } catch (TransientException e) {
                // Retryable error
                attempts++;
                setAttempts(vars, record.getId(), attempts);
                
                if (attempts >= MAX_ATTEMPTS) {
                    // Move to DLQ
                    log.error("Moving record {} to DLQ after {} attempts", 
                        record.getId(), attempts);
                    
                    addToFailedRecords(vars, record, "MAX_RETRIES_EXCEEDED", e.getMessage());
                    vars.setValue("current_record_index", WorkflowVariableType.INTEGER, currentIndex + 1);
                    
                    // Alert operations
                    alertService.sendDLQAlert(record.getId(), e.getMessage());
                    
                    return new TaskResponse(
                        StepResponseType.OK_PEND_EOR,
                        "Record moved to DLQ",
                        "processing_queue"
                    );
                }
                
                // Retry
                log.warn("Retrying record {}, attempt {} of {}", 
                    record.getId(), attempts, MAX_ATTEMPTS);
                
                return new TaskResponse(
                    StepResponseType.OK_PEND_EOR,
                    "Retrying record",
                    "retry_queue"
                );
            }
        }
        
        private Integer getAttempts(WorkflowVariables vars, String recordId) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> attemptsMap = (Map<String, Integer>) vars.getValue(
                ATTEMPTS_KEY,
                WorkflowVariableType.OBJECT
            );
            if (attemptsMap == null) return 0;
            return attemptsMap.getOrDefault(recordId, 0);
        }
        
        private void setAttempts(WorkflowVariables vars, String recordId, Integer attempts) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> attemptsMap = (Map<String, Integer>) vars.getValue(
                ATTEMPTS_KEY,
                WorkflowVariableType.OBJECT
            );
            if (attemptsMap == null) attemptsMap = new HashMap<>();
            attemptsMap.put(recordId, attempts);
            vars.setValue(ATTEMPTS_KEY, WorkflowVariableType.OBJECT, attemptsMap);
        }
        
        private void clearAttempts(WorkflowVariables vars, String recordId) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> attemptsMap = (Map<String, Integer>) vars.getValue(
                ATTEMPTS_KEY,
                WorkflowVariableType.OBJECT
            );
            if (attemptsMap != null) {
                attemptsMap.remove(recordId);
                vars.setValue(ATTEMPTS_KEY, WorkflowVariableType.OBJECT, attemptsMap);
            }
        }
        
        @SuppressWarnings("unchecked")
        private void addToFailedRecords(WorkflowVariables vars, DataRecord record, 
                                       String errorType, String errorMessage) {
            List<FailedRecord> failedRecords = (List<FailedRecord>) vars.getValue(
                FAILED_RECORDS_KEY,
                WorkflowVariableType.LIST_OF_OBJECT
            );
            if (failedRecords == null) failedRecords = new ArrayList<>();
            
            failedRecords.add(new FailedRecord(
                record.getId(),
                errorType,
                errorMessage,
                System.currentTimeMillis()
            ));
            
            vars.setValue(FAILED_RECORDS_KEY, WorkflowVariableType.LIST_OF_OBJECT, failedRecords);
        }
    }
}
```

## Summary

### Error Handling Checklist

✅ **Classification**
- [ ] Errors are classified (business, technical, validation, fatal)
- [ ] Error codes are defined in ErrorMap
- [ ] Error categories determine routing

✅ **Recovery**
- [ ] Retry logic for transient errors
- [ ] Circuit breaker for external services
- [ ] Fallback strategies defined
- [ ] Compensation logic for distributed transactions

✅ **Monitoring**
- [ ] Structured logging implemented
- [ ] Error metrics collected
- [ ] Error dashboard available
- [ ] Alerts configured for critical errors

✅ **Testing**
- [ ] Unit tests for error scenarios
- [ ] Integration tests for error recovery
- [ ] Load tests include error conditions
- [ ] DLQ processing tested

✅ **Documentation**
- [ ] Error codes documented
- [ ] Recovery procedures documented
- [ ] Escalation paths defined
- [ ] Runbooks for common errors

### Quick Reference

| Error Type | Response Type | Work Basket Pattern | Retry? |
|------------|---------------|---------------------|--------|
| Validation | ERROR_PEND | validation_*_queue | No |
| Business | OK_PEND or ERROR_PEND | business_*_queue | Sometimes |
| Transient Technical | OK_PEND_EOR | retry_queue | Yes |
| Service Unavailable | ERROR_PEND | service_*_queue | Yes |
| Fatal | WorkflowRuntimeException | N/A | No |

### Related Documentation

- [Crash Recovery](./crash-recovery.md) - Recovering from JVM crashes
- [Idempotency Patterns](./idempotency.md) - Making operations safe to retry
- [Process Variables](../concepts/process-variables.md) - Storing error state
- [Work Baskets](../concepts/work-baskets.md) - Routing errors to queues
- [Monitoring Guide](../operations/monitoring.md) - Tracking errors in production

---

**Remember**: Good error handling is not about preventing all errors—it's about handling them gracefully and providing clear paths to resolution.