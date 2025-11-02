# Best Practices

Guidelines and recommendations for building robust, maintainable, and production-ready workflows with Simple Workflow.

## Table of Contents

1. [Workflow Design](#workflow-design)
2. [Step Implementation](#step-implementation)
3. [Error Handling](#error-handling)
4. [Performance](#performance)
5. [Security](#security)
6. [Testing](#testing)
7. [Operations](#operations)

## Workflow Design

### Keep Workflows Focused

**✅ Good:**
```json
{
  "processDefinitionId": "order-fulfillment",
  "description": "Handle order fulfillment from payment to shipping"
}
```

**❌ Bad:**
```json
{
  "processDefinitionId": "everything",
  "description": "Handles orders, customers, inventory, shipping, and analytics"
}
```

**Guideline:** Each workflow should have a single, clear responsibility.

### Use Descriptive IDs and Names

**✅ Good:**
```json
{
  "stepId": "validate-customer-credit",
  "stepName": "Validate Customer Credit Score",
  "stepType": "creditValidation"
}
```

**❌ Bad:**
```json
{
  "stepId": "step1",
  "stepName": "Do stuff",
  "stepType": "type1"
}
```

### Design for Parallel Execution

Identify steps that can run independently:

**✅ Good:**
```json
{
  "steps": [
    {"stepId": "send-email", "executionPath": "notifications", "order": 1},
    {"stepId": "update-crm", "executionPath": "crm", "order": 1},
    {"stepId": "generate-invoice", "executionPath": "billing", "order": 1}
  ]
}
```

**Benefits:**
- Faster execution
- Better resource utilization
- Natural fault isolation

### Version Your Workflows

```json
{
  "processDefinitionId": "order-process",
  "processDefinitionVersion": "2.1.0"
}
```

**Guidelines:**
- Use semantic versioning
- Document changes between versions
- Support multiple versions simultaneously
- Plan migration strategy

## Step Implementation

### Make Steps Idempotent

Steps should produce the same result when executed multiple times:

**✅ Good:**
```java
public class ChargePaymentStep implements WorkflowStep {
    @Override
    public void execute(WorkflowContext context) throws Exception {
        String orderId = context.getProcessVariable("orderId");

        // Check if already charged
        String paymentId = context.getProcessVariable("paymentId");
        if (paymentId != null) {
            log.info("Payment already processed: " + paymentId);
            return;
        }

        // Process payment
        paymentId = paymentService.charge(orderId);
        context.setProcessVariable("paymentId", paymentId);
    }
}
```

See [Idempotency Patterns](../patterns/idempotency.md) for comprehensive guide.

### Keep Steps Small and Focused

**✅ Good:**
```java
public class ValidateOrderStep implements WorkflowStep {
    public void execute(WorkflowContext context) {
        // Single responsibility: validate order
        Order order = context.getProcessVariable("order");
        ValidationResult result = validator.validate(order);
        context.setProcessVariable("validationResult", result);
    }
}
```

**❌ Bad:**
```java
public class ProcessEverythingStep implements WorkflowStep {
    public void execute(WorkflowContext context) {
        // Too many responsibilities!
        validateOrder();
        checkInventory();
        processPayment();
        updateCRM();
        sendEmail();
        generateInvoice();
    }
}
```

### Handle Errors Gracefully

```java
public class RobustStep implements WorkflowStep {
    @Override
    public void execute(WorkflowContext context) throws Exception {
        try {
            // Business logic
            performOperation();

        } catch (RetryableException e) {
            // Store error state for retry
            context.setProcessVariable("retryReason", e.getMessage());
            throw e;  // Let workflow handle retry

        } catch (BusinessException e) {
            // Store error details
            context.setProcessVariable("businessError", e.getMessage());
            context.setProcessVariable("errorRecoverable", false);
            throw e;

        } finally {
            // Cleanup resources
            cleanup();
        }
    }
}
```

### Use Process Variables Wisely

```java
// ✅ Store business data
context.setProcessVariable("orderId", "ORD-123");
context.setProcessVariable("customerEmail", "customer@example.com");

// ❌ Don't store technical artifacts
context.setProcessVariable("connection", dbConnection);  // Bad!
context.setProcessVariable("thread", currentThread);     // Bad!

// ✅ Store serializable references
context.setProcessVariable("connectionId", "CONN-456");
context.setProcessVariable("sessionToken", "TOKEN-789");
```

See [Process Variables](../concepts/process-variables.md) for detailed guide.

### Implement Proper Logging

```java
public class WellLoggedStep implements WorkflowStep {
    private static final Logger log = LoggerFactory.getLogger(WellLoggedStep.class);

    @Override
    public void execute(WorkflowContext context) throws Exception {
        String caseId = context.getCaseId();
        String orderId = context.getProcessVariable("orderId");

        log.info("Processing order {} for case {}", orderId, caseId);

        try {
            // Business logic
            Result result = processOrder(orderId);

            log.info("Successfully processed order {}: {}", orderId, result);
            context.setProcessVariable("processResult", result);

        } catch (Exception e) {
            log.error("Failed to process order {} for case {}", orderId, caseId, e);
            throw e;
        }
    }
}
```

## Error Handling

### Classify Errors Appropriately

```java
// Transient errors - can be retried
public class TransientException extends RuntimeException {
    public TransientException(String message) {
        super(message);
    }
}

// Permanent errors - cannot be retried
public class PermanentException extends RuntimeException {
    public PermanentException(String message) {
        super(message);
    }
}

// Business errors - valid business scenarios
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
```

### Implement Retry Logic

```java
public class RetryableStep implements WorkflowStep {
    private static final int MAX_RETRIES = 3;

    @Override
    public void execute(WorkflowContext context) throws Exception {
        Integer retryCount = context.getProcessVariable("retryCount");
        if (retryCount == null) {
            retryCount = 0;
        }

        try {
            // Attempt operation
            performOperation();
            context.setProcessVariable("retryCount", 0);  // Reset on success

        } catch (TransientException e) {
            if (retryCount < MAX_RETRIES) {
                context.setProcessVariable("retryCount", retryCount + 1);
                log.warn("Retry attempt {} for transient error", retryCount + 1);
                throw e;  // Workflow will retry
            } else {
                throw new PermanentException(
                    "Max retries exceeded: " + e.getMessage());
            }
        }
    }
}
```

### Use Compensation Steps

```java
// Main operation
public class BookFlightStep implements WorkflowStep {
    public void execute(WorkflowContext context) {
        String bookingId = flightService.book(...);
        context.setProcessVariable("flightBookingId", bookingId);
    }
}

// Compensation if process fails later
public class CancelFlightStep implements WorkflowStep {
    public void execute(WorkflowContext context) {
        String bookingId = context.getProcessVariable("flightBookingId");
        if (bookingId != null) {
            flightService.cancel(bookingId);
        }
    }
}
```

See [Error Handling Patterns](../patterns/error-handling.md) for comprehensive guide.

## Performance

### Optimize Parallel Execution

```json
{
  "steps": [
    // Parallel I/O-bound operations
    {"stepId": "fetch-customer", "executionPath": "customer", "order": 1},
    {"stepId": "fetch-inventory", "executionPath": "inventory", "order": 1},
    {"stepId": "fetch-pricing", "executionPath": "pricing", "order": 1},

    // Sequential dependent operation
    {"stepId": "calculate-total", "executionPath": "main", "order": 2}
  ]
}
```

### Minimize Variable Size

```java
// ❌ Bad: Storing large objects
byte[] largeFile = loadFile();  // 100 MB
context.setProcessVariable("fileData", largeFile);

// ✅ Good: Store reference
String fileId = uploadToStorage(largeFile);
context.setProcessVariable("fileId", fileId);
context.setProcessVariable("fileSize", largeFile.length);
```

### Use Appropriate Thread Pool Size

```java
// For CPU-bound workflows
int cpuCores = Runtime.getRuntime().availableProcessors();
WorkflowService.init(cpuCores, 30000, "-");

// For I/O-bound workflows
WorkflowService.init(cpuCores * 2, 60000, "-");

// For mixed workloads
WorkflowService.init(cpuCores + 4, 45000, "-");
```

### Batch Database Operations

```java
// ❌ Bad: N+1 queries
for (String itemId : items) {
    Item item = dao.loadItem(itemId);
    processItem(item);
}

// ✅ Good: Batch load
List<Item> items = dao.loadItems(itemIds);
for (Item item : items) {
    processItem(item);
}
```

See [Performance Tuning](../operations/performance.md) for detailed guide.

## Security

### Validate Input Data

```java
public class SecureStep implements WorkflowStep {
    @Override
    public void execute(WorkflowContext context) throws Exception {
        String orderId = context.getProcessVariable("orderId");

        // Validate format
        if (!orderId.matches("ORD-\\d{6}")) {
            throw new ValidationException("Invalid order ID format");
        }

        // Prevent injection
        String sanitized = sanitize(orderId);

        // Use parameterized queries
        Order order = dao.findOrder(sanitized);
    }
}
```

### Protect Sensitive Data

```java
// ✅ Encrypt sensitive variables
String creditCard = context.getProcessVariable("creditCard");
String encrypted = encryptionService.encrypt(creditCard);
context.setProcessVariable("creditCard_encrypted", encrypted);

// Remove original
context.removeProcessVariable("creditCard");

// Later, decrypt when needed
String encryptedValue = context.getProcessVariable("creditCard_encrypted");
String decrypted = encryptionService.decrypt(encryptedValue);
```

### Implement Authorization

```java
public class AuthorizedStep implements WorkflowStep {
    @Override
    public void execute(WorkflowContext context) throws Exception {
        String userId = context.getProcessVariable("userId");
        String orderId = context.getProcessVariable("orderId");

        // Check authorization
        if (!authService.canAccessOrder(userId, orderId)) {
            throw new SecurityException(
                "User " + userId + " not authorized for order " + orderId);
        }

        // Proceed with operation
        processOrder(orderId);
    }
}
```

### Audit Important Actions

```java
public class AuditedStep implements WorkflowStep {
    @Override
    public void execute(WorkflowContext context) throws Exception {
        String userId = context.getProcessVariable("userId");
        String action = "APPROVE_ORDER";
        String orderId = context.getProcessVariable("orderId");

        // Perform action
        approveOrder(orderId);

        // Log audit event
        auditService.log(AuditEvent.builder()
            .userId(userId)
            .action(action)
            .resourceId(orderId)
            .caseId(context.getCaseId())
            .timestamp(System.currentTimeMillis())
            .build());

        // Store in process variables for tracking
        context.setProcessVariable("approvedBy", userId);
        context.setProcessVariable("approvalTimestamp",
            System.currentTimeMillis());
    }
}
```

## Testing

### Unit Test Steps in Isolation

```java
@Test
public void testValidateOrderStep() {
    // Arrange
    ValidateOrderStep step = new ValidateOrderStep();
    WorkflowContext context = mock(WorkflowContext.class);

    Order order = new Order("ORD-123", 299.99);
    when(context.getProcessVariable("order")).thenReturn(order);

    // Act
    step.execute(context);

    // Assert
    verify(context).setProcessVariable(eq("validationResult"), any());
}
```

### Integration Test Complete Workflows

```java
@Test
public void testOrderWorkflow() throws Exception {
    // Initialize
    WorkflowService.init(2, 10000, "-");
    CommonService dao = new InMemoryDao();
    RuntimeService rts = WorkflowService.instance()
        .getRunTimeService(dao, factory, handler, null);

    // Start workflow
    String caseId = "TEST-CASE-001";
    Map<String, Object> vars = Map.of("orderId", "ORD-123");
    rts.startCase(caseId, workflowJson, vars, null);

    // Wait for completion
    Thread.sleep(2000);

    // Verify
    ProcessEntity process = rts.getProcess(caseId);
    assertEquals(ProcessStatus.COMPLETED, process.getStatus());
}
```

### Test Error Scenarios

```java
@Test
public void testStepFailureRecovery() {
    // Arrange step to fail
    when(externalService.call()).thenThrow(new RuntimeException());

    // Execute
    assertThrows(RuntimeException.class, () -> {
        step.execute(context);
    });

    // Verify error handling
    verify(context).setProcessVariable("errorOccurred", true);
}
```

See [Testing Guide](../testing/README.md) for comprehensive testing strategies.

## Operations

### Monitor Workflow Health

```java
public class HealthCheckService {
    public WorkflowHealth checkHealth() {
        WorkflowHealth health = new WorkflowHealth();

        // Check active workflows
        int activeCount = dao.countActiveProcesses();
        health.setActiveProcesses(activeCount);

        // Check failed workflows
        int failedCount = dao.countFailedProcesses();
        health.setFailedProcesses(failedCount);

        // Check oldest pending
        Duration oldestPending = dao.getOldestPendingAge();
        health.setOldestPendingAge(oldestPending);

        return health;
    }
}
```

### Implement Graceful Shutdown

```java
public class GracefulShutdown {
    public void shutdown() {
        // Stop accepting new workflows
        workflowService.stopAcceptingNew();

        // Wait for active workflows to complete
        int timeout = 30;  // seconds
        while (workflowService.hasActiveProcesses() && timeout > 0) {
            Thread.sleep(1000);
            timeout--;
        }

        // Force shutdown if timeout
        if (timeout == 0) {
            log.warn("Forcing shutdown with active processes");
        }

        workflowService.shutdown();
    }
}
```

### Plan for Disaster Recovery

```java
// Regular backups
public class BackupService {
    @Scheduled(cron = "0 0 * * * *")  // Hourly
    public void backupWorkflowData() {
        String backupPath = "/backups/workflow-" +
            System.currentTimeMillis();
        dao.backup(backupPath);
    }
}

// Recovery procedure
public class RecoveryService {
    public void recoverFromBackup(String backupPath) {
        dao.restore(backupPath);
        runtimeService.recoverIncompleteProcesses();
    }
}
```

See [Operations Guide](../operations/README.md) for production deployment.

## Summary Checklist

### Workflow Design
- [ ] Single responsibility per workflow
- [ ] Descriptive IDs and names
- [ ] Parallel execution where possible
- [ ] Versioned definitions

### Step Implementation
- [ ] Idempotent operations
- [ ] Small, focused responsibilities
- [ ] Proper error handling
- [ ] Appropriate logging

### Error Handling
- [ ] Error classification
- [ ] Retry logic for transient errors
- [ ] Compensation steps

### Performance
- [ ] Optimized parallel execution
- [ ] Minimal variable size
- [ ] Appropriate thread pool size
- [ ] Batch operations

### Security
- [ ] Input validation
- [ ] Sensitive data protection
- [ ] Authorization checks
- [ ] Audit logging

### Testing
- [ ] Unit tests for steps
- [ ] Integration tests for workflows
- [ ] Error scenario testing

### Operations
- [ ] Health monitoring
- [ ] Graceful shutdown
- [ ] Disaster recovery plan

## Related Documentation

- [Error Handling Patterns](../patterns/error-handling.md)
- [Idempotency Patterns](../patterns/idempotency.md)
- [Performance Tuning](../operations/performance.md)
- [Testing Guide](../testing/README.md)
- [Operations Guide](../operations/README.md)
