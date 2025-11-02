# Crash Recovery Mechanism

## Overview

Simple Workflow is designed to be **crash-proof**, meaning it can recover from JVM crashes, unexpected shutdowns, or system failures and resume workflow execution from the last known state.

## Table of Contents
- [How It Works](#how-it-works)
- [State Persistence](#state-persistence)
- [Recovery Process](#recovery-process)
- [Idempotency Requirements](#idempotency-requirements)
- [Identifying Orphaned Cases](#identifying-orphaned-cases)
- [Recovery Modes](#recovery-modes)
- [Best Practices](#best-practices)
- [Limitations](#limitations)

## How It Works

### The Challenge

When a JVM crashes during workflow execution:
- Steps may be partially executed
- Database transactions may be incomplete
- External service calls may have succeeded but not recorded
- Execution state may be lost

### The Solution

Simple Workflow addresses this through:
1. **Aggressive State Persistence**: Write state after each step/route
2. **Automatic Sanitization**: Detect and repair inconsistent state
3. **Idempotent Design**: Steps can safely execute multiple times
4. **Smart Resume Logic**: Determine correct restart point

## State Persistence

### Persistence Points

State is persisted at these points:

```
Case Start
    ↓
[PERSIST] ← Workflow Info saved
    ↓
Execute Step 1
    ↓
[PERSIST] ← State after step 1
    ↓
Execute Step 2
    ↓
[PERSIST] ← State after step 2
    ↓
    ... (crash happens here)
    ↓
Resume
    ↓
[LOAD] ← Load last state (after step 2)
    ↓
Execute Step 3 (continue)
```

### Persistence Modes

#### Mode 1: Aggressive (Default)

```java
WorkflowService.instance().setWriteProcessInfoAfterEachStep(true);
```

**Behavior**: Persist after every step/route execution

**Pros**:
- Maximum crash recovery
- Lose at most one step's execution
- Easiest to debug

**Cons**:
- Higher I/O overhead
- Slower performance

**Use When**:
- Steps take significant time (>1 second)
- External service calls are expensive
- Crash recovery is critical

#### Mode 2: Lazy

```java
WorkflowService.instance().setWriteProcessInfoAfterEachStep(false);
```

**Behavior**: Persist only on case complete or pend

**Pros**:
- Better performance
- Lower I/O overhead

**Cons**:
- May lose multiple steps in crash
- Steps must be **fully idempotent**

**Use When**:
- Steps are fast (<100ms)
- All steps are idempotent
- Performance is critical

### What Gets Persisted

#### Workflow Info (`workflow_process_info-{caseId}`)

```json
{
  "process_info": {
    "last_executed_step": "step_3",
    "last_executed_comp_name": "validate_order",
    "pend_exec_path": ".route_1.2.",
    "ts": 1699564723000,
    "is_complete": false,
    "process_variables": [...],
    "exec_paths": [...],
    "ticket": ""
  }
}
```

#### Key Fields for Recovery

| Field | Purpose | Recovery Use |
|-------|---------|--------------|
| `last_executed_step` | Last step that completed | Information only |
| `pend_exec_path` | Where workflow is pended | Resume from here |
| `ts` | Timestamp of last save | Detect orphaned cases |
| `is_complete` | Case finished? | Skip completed cases |
| `exec_paths[].status` | Path state | Detect incomplete paths |
| `exec_paths[].unit_response_type` | How step responded | Determine resume behavior |

## Recovery Process

### Automatic Sanitization

When a case resumes, workflow automatically sanitizes the state:

```java
private static void sanitize(WorkflowInfo pid, String caseId, WorkflowDefinition pd) {
    // 1. Check if case is complete
    setIsComplete(caseId, pid);
    
    // 2. Handle outstanding tickets
    boolean isTicket = checkAndSetTicketInExecPath(caseId, pid);
    
    // 3. Fix incomplete execution paths
    if (!isTicket) {
        checkExecPathCompletion(pid, caseId, pd);
    }
    
    // 4. Set pend execution path
    setPendExecPath(pid, caseId);
}
```

### Step-by-Step Recovery

#### 1. Detect Incomplete Paths

```java
for (ExecPath path : execPaths) {
    if (path.getStatus() == ExecPathStatus.STARTED) {
        // This path didn't complete - crashed during execution
        
        // Mark as completed
        path.setStatus(ExecPathStatus.COMPLETED);
        
        // Set appropriate work basket
        String wb = path.getPrevPendWorkBasket().isEmpty() 
            ? "workflow_temp_hold" 
            : path.getPrevPendWorkBasket();
        path.setPendWorkBasket(wb);
    }
}
```

#### 2. Determine Resume Point

Based on last `unit_response_type`:

```java
StepResponseType urt = path.getStepResponseType();
Step unit = pd.getStep(path.getStep());

if (urt == StepResponseType.OK_PROCEED) {
    if (unit.getType() == StepType.TASK) {
        // Step completed successfully - move to next
        path.setStepResponseType(StepResponseType.OK_PEND);
    } else if (unit.getType() == StepType.S_ROUTE) {
        // Route completed - re-evaluate
        path.setStepResponseType(StepResponseType.OK_PEND_EOR);
    }
}
```

#### 3. Select Deepest Pend Path

```java
// Find deepest execution path that pended
String pendExecPath = "";
int maxDepth = 0;

for (ExecPath path : execPaths) {
    String wb = path.getPendWorkBasket();
    if (!wb.isEmpty()) {
        int depth = StringUtils.getCount(path.getName(), '.');
        if (depth > maxDepth) {
            maxDepth = depth;
            pendExecPath = path.getName();
        }
    }
}
```

### Recovery Scenarios

#### Scenario 1: Crash During Step Execution

**Before Crash**:
```json
{
  "exec_paths": [{
    "name": ".",
    "status": "started",
    "step": "send_email",
    "unit_response_type": null
  }]
}
```

**After Recovery**:
```json
{
  "exec_paths": [{
    "name": ".",
    "status": "completed",
    "step": "send_email",
    "unit_response_type": "ok_pend_eor",
    "pend_workbasket": "workflow_temp_hold"
  }],
  "pend_exec_path": "."
}
```

**Result**: `send_email` step executes again (must be idempotent)

#### Scenario 2: Crash After Step, Before Persist

**Before Crash**:
```json
{
  "exec_paths": [{
    "status": "started",
    "step": "step_2",
    "unit_response_type": "ok_proceed"
  }]
}
```

**After Recovery**:
```json
{
  "exec_paths": [{
    "status": "completed",
    "step": "step_2",
    "unit_response_type": "ok_pend",
    "pend_workbasket": "workflow_temp_hold"
  }]
}
```

**Result**: Continues from step 3 (step 2 not re-executed)

#### Scenario 3: Crash During Parallel Processing

**Before Crash**:
```json
{
  "exec_paths": [
    {
      "name": ".route_1.1.",
      "status": "completed",
      "step": "step_a"
    },
    {
      "name": ".route_1.2.",
      "status": "started",
      "step": "step_b",
      "unit_response_type": null
    },
    {
      "name": ".route_1.3.",
      "status": "completed",
      "step": "step_c"
    }
  ]
}
```

**After Recovery**:
```json
{
  "exec_paths": [
    {
      "name": ".route_1.1.",
      "status": "completed"
    },
    {
      "name": ".route_1.2.",
      "status": "completed",
      "unit_response_type": "ok_pend_eor",
      "pend_workbasket": "workflow_temp_hold"
    },
    {
      "name": ".route_1.3.",
      "status": "completed"
    }
  ],
  "pend_exec_path": ".route_1.2."
}
```

**Result**: Only `.route_1.2.` resumes, executes `step_b` again

## Idempotency Requirements

### What is Idempotency?

An operation is idempotent if executing it multiple times produces the same result as executing it once.

```java
// ✅ Idempotent
int x = 5;  // No matter how many times, x = 5

// ❌ Not Idempotent
int x = x + 1;  // Result depends on how many times executed
```

### When Idempotency is Critical

#### Always Required
- Steps returning `OK_PEND_EOR` (execute on resume)
- Using lazy persistence mode
- Steps making external service calls

#### Best Practice (Always)
- Even with aggressive persistence, one step may re-execute

### Implementing Idempotent Steps

#### Pattern 1: Check-Before-Execute

```java
public class SendEmailStep implements InvokableTask {
    private WorkflowContext context;
    private EmailService emailService;
    
    @Override
    public TaskResponse executeStep() {
        String caseId = context.getCaseId();
        
        // Check if already sent
        if (emailService.wasEmailSent(caseId, "welcome")) {
            log.info("Email already sent for case {}, skipping", caseId);
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
        
        // Send email
        emailService.sendEmail(caseId, "welcome");
        emailService.recordEmailSent(caseId, "welcome");
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

#### Pattern 2: Idempotent External Calls

```java
public class CreateOrderStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        String orderId = context.getCaseId();
        
        // Use idempotency key in API call
        ApiResponse response = orderApi.createOrder(
            CreateOrderRequest.builder()
                .orderId(orderId)
                .idempotencyKey("order-" + orderId)
                .items(getItems())
                .build()
        );
        
        // API ensures only one order created even if called multiple times
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

#### Pattern 3: Use Process Variables

```java
public class ProcessPaymentStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Check if payment already processed
        Boolean paymentProcessed = vars.getBoolean("payment_processed");
        if (paymentProcessed != null && paymentProcessed) {
            String transactionId = vars.getString("transaction_id");
            log.info("Payment already processed: {}", transactionId);
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
        
        // Process payment
        String transactionId = paymentService.charge(getAmount());
        
        // Record in process variables
        vars.setValue("payment_processed", WorkflowVariableType.BOOLEAN, true);
        vars.setValue("transaction_id", WorkflowVariableType.STRING, transactionId);
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

#### Pattern 4: Database Constraints

```sql
-- Use unique constraints to prevent duplicates
CREATE TABLE order_items (
    order_id VARCHAR(50) NOT NULL,
    item_id VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (order_id, item_id)
);
```

```java
public class AddItemStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        try {
            orderRepository.addItem(orderId, itemId, quantity);
        } catch (DuplicateKeyException e) {
            log.info("Item already added, continuing");
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### Non-Idempotent Operations

Some operations are inherently non-idempotent:

```java
// ❌ Not idempotent
public TaskResponse executeStep() {
    int count = database.getCurrentCount();
    database.setCount(count + 1);
    return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
}

// ✅ Make it idempotent
public TaskResponse executeStep() {
    Boolean counted = vars.getBoolean("item_counted");
    if (counted != null && counted) {
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
    
    database.incrementCount();
    vars.setValue("item_counted", WorkflowVariableType.BOOLEAN, true);
    return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
}
```

## Identifying Orphaned Cases

### Detection Strategy

Orphaned cases have these characteristics:
- `pend_exec_path` is empty (not explicitly pended)
- `is_complete` is false
- `ts` is older than threshold (e.g., 5 minutes)
- Execution paths have status `STARTED`

### Detection Query Example

```java
public class OrphanedCaseDetector {
    
    private static final long ORPHAN_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes
    
    public List<String> findOrphanedCases(CommonService dao) {
        List<String> orphaned = new ArrayList<>();
        long threshold = System.currentTimeMillis() - ORPHAN_THRESHOLD_MS;
        
        // Get all case IDs (implementation depends on your DAO)
        List<String> allCaseIds = getAllCaseIds(dao);
        
        for (String caseId : allCaseIds) {
            WorkflowInfo info = dao.get(
                WorkflowInfo.class,
                "workflow_process_info-" + caseId
            );
            
            if (isOrphaned(info, threshold)) {
                orphaned.add(caseId);
                log.warn("Orphaned case detected: {}", caseId);
            }
        }
        
        return orphaned;
    }
    
    private boolean isOrphaned(WorkflowInfo info, long threshold) {
        if (info == null || info.getIsComplete()) {
            return false;
        }
        
        // Check if pend path is empty (not explicitly pended)
        if (!info.getPendExecPath().isEmpty()) {
            return false;
        }
        
        // Check timestamp
        Document doc = /* load workflow info as document */;
        Long ts = doc.getLong("$.process_info.ts");
        if (ts == null || ts > threshold) {
            return false;
        }
        
        // Check if any exec path is still STARTED
        return info.getExecPaths().stream()
            .anyMatch(p -> p.getStatus() == ExecPathStatus.STARTED);
    }
}
```

### Batch Recovery Job

```java
@Scheduled(fixedDelay = 60000) // Every minute
public void recoverOrphanedCases() {
    log.info("Scanning for orphaned cases...");
    
    List<String> orphaned = orphanDetector.findOrphanedCases(dao);
    
    for (String caseId : orphaned) {
        try {
            log.info("Attempting recovery of case: {}", caseId);
            runtimeService.resumeCase(caseId);
            log.info("Successfully recovered case: {}", caseId);
        } catch (Exception e) {
            log.error("Failed to recover case {}: {}", caseId, e.getMessage());
            // Optionally alert monitoring system
        }
    }
    
    log.info("Recovery scan complete. Recovered {} cases", orphaned.size());
}
```

### Manual Recovery

```java
// For specific case
public void recoverCase(String caseId) {
    try {
        runtimeService.resumeCase(caseId);
        log.info("Case {} recovered", caseId);
    } catch (WorkflowRuntimeException e) {
        log.error("Cannot recover case {}: {}", caseId, e.getMessage());
        
        // Check if case is irreparably corrupted
        if (e.getMessage().contains("cannot be repaired")) {
            // Manual intervention required
            notifyAdministrator(caseId, e);
        }
    }
}
```

## Recovery Modes

### Automatic Recovery (Recommended)

Workflow automatically sanitizes and recovers on resume:

```java
// Just call resume - sanitization happens automatically
runtimeService.resumeCase(caseId);
```

### Manual Inspection Before Recovery

```java
public void inspectBeforeRecovery(String caseId) {
    // Load workflow info
    WorkflowInfo info = dao.get(
        WorkflowInfo.class,
        "workflow_process_info-" + caseId
    );
    
    // Inspect state
    log.info("Case: {}", caseId);
    log.info("Complete: {}", info.getIsComplete());
    log.info("Pend Path: {}", info.getPendExecPath());
    log.info("Ticket: {}", info.getTicket());
    
    // Check each execution path
    for (ExecPath path : info.getExecPaths()) {
        log.info("Path: {}, Status: {}, Step: {}, Response: {}",
            path.getName(),
            path.getStatus(),
            path.getStep(),
            path.getStepResponseType()
        );
    }
    
    // Decide whether to proceed
    if (requiresManualIntervention(info)) {
        log.warn("Case {} requires manual intervention", caseId);
        return;
    }
    
    // Proceed with recovery
    runtimeService.resumeCase(caseId);
}
```

### Recovery with Process Variable Reset

```java
public void recoverWithVariableReset(String caseId, Map<String, Object> newValues) {
    // Load and update process variables
    WorkflowVariables vars = new WorkflowVariables();
    
    for (Map.Entry<String, Object> entry : newValues.entrySet()) {
        WorkflowVariableType type = determineType(entry.getValue());
        vars.setValue(entry.getKey(), type, entry.getValue());
    }
    
    // Resume with updated variables
    runtimeService.resumeCase(caseId, vars);
}
```

## Best Practices

### 1. Design for Crash Recovery

```java
// ✅ Good: Stateless, idempotent
public class ValidateAddressStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        String address = context.getProcessVariables().getString("address");
        
        // Validation is naturally idempotent
        boolean valid = addressValidator.validate(address);
        
        context.getProcessVariables().setValue(
            "address_valid",
            WorkflowVariableType.BOOLEAN,
            valid
        );
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}

// ❌ Bad: Uses local state
public class CounterStep implements InvokableTask {
    private int counter = 0; // Lost on crash!
    
    @Override
    public TaskResponse executeStep() {
        counter++; // Not idempotent
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### 2. Use Process Variables for State

```java
// Store everything important in process variables
context.getProcessVariables().setValue("order_id", WorkflowVariableType.STRING, orderId);
context.getProcessVariables().setValue("amount", WorkflowVariableType.LONG, amount);
context.getProcessVariables().setValue("status", WorkflowVariableType.STRING, "pending");
```

### 3. Log Idempotency Checks

```java
@Override
public TaskResponse executeStep() {
    Boolean processed = vars.getBoolean("payment_processed");
    
    if (processed != null && processed) {
        log.info("Step already executed, skipping (case: {})", context.getCaseId());
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
    
    log.info("Executing step for first time (case: {})", context.getCaseId());
    // ... do work
}
```

### 4. Handle External Service Failures Gracefully

```java
@Override
public TaskResponse executeStep() {
    try {
        externalService.call();
        vars.setValue("service_called", WorkflowVariableType.BOOLEAN, true);
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        
    } catch (ServiceUnavailableException e) {
        log.warn("Service unavailable, will retry on resume");
        return new TaskResponse(
            StepResponseType.ERROR_PEND,
            "",
            "retry_queue"
        );
    }
}
```

### 5. Use Transactional Boundaries

```java
@Override
@Transactional
public TaskResponse executeStep() {
    // Database operations in transaction
    orderRepository.save(order);
    inventoryRepository.decrementStock(itemId);
    
    // Mark as completed in process variables
    vars.setValue("order_saved", WorkflowVariableType.BOOLEAN, true);
    
    return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
}
// If crash happens, entire transaction rolls back
```

### 6. Test Crash Scenarios

```java
@Test
public void testStepIsIdempotent() {
    WorkflowContext context = createTestContext();
    MyStep step = new MyStep(context);
    
    // Execute once
    TaskResponse response1 = step.executeStep();
    Object result1 = getResult();
    
    // Execute again (simulating crash recovery)
    TaskResponse response2 = step.executeStep();
    Object result2 = getResult();
    
    // Results should be identical
    assertEquals(result1, result2);
    assertEquals(response1.getUnitResponseType(), response2.getUnitResponseType());
}
```

### 7. Monitor Recovery Metrics

```java
public class RecoveryMetrics {
    private Counter casesRecovered;
    private Counter recoveryFailures;
    private Timer recoveryTime;
    
    public void recordRecovery(String caseId, long durationMs, boolean success) {
        if (success) {
            casesRecovered.increment();
            recoveryTime.record(durationMs, TimeUnit.MILLISECONDS);
            log.info("Case {} recovered in {}ms", caseId, durationMs);
        } else {
            recoveryFailures.increment();
            log.error("Failed to recover case {}", caseId);
        }
    }
}
```

## Limitations

### What Can Be Recovered

✅ **Recoverable**:
- Incomplete steps
- Incomplete routes
- Parallel processing state
- Process variables
- Execution paths
- Work basket state

### What Cannot Be Recovered

❌ **Not Recoverable**:
- In-flight HTTP requests
- Uncommitted database transactions
- Messages in process of being sent
- Local variables in step implementations
- Thread-local storage
- Cached data not persisted

### Edge Cases

#### Case 1: Crash During Persist Operation

**Problem**: JVM crashes while writing workflow info

**Impact**: Previous state remains, step may execute twice

**Mitigation**: Use idempotent steps

#### Case 2: Multiple Parallel Paths Crash

**Problem**: Several paths crash simultaneously

**Impact**: All are recovered and re-executed

**Mitigation**: Ensure parallel steps don't conflict

#### Case 3: External Service Called But Not Recorded

**Problem**: Payment processed, then crash before recording

**Impact**: Payment may be processed twice

**Mitigation**:
```java
// Use external service idempotency
payment.process(amount, idempotencyKey: caseId);

// Or check before calling
if (!paymentService.wasProcessed(caseId)) {
    paymentService.process(caseId, amount);
}
```

#### Case 4: Corrupted Workflow Info File

**Problem**: Disk corruption, partial write

**Impact**: Cannot recover automatically

**Resolution**:
```java
try {
    runtimeService.resumeCase(caseId);
} catch (WorkflowRuntimeException e) {
    if (e.getMessage().contains("cannot be repaired")) {
        // Manual recovery required
        // 1. Restore from backup
        // 2. Or manually fix workflow info
        // 3. Or reopen case with new ticket
    }
}
```

## Troubleshooting

### Problem: Case Won't Resume

**Symptoms**:
- `resumeCase()` throws exception
- Message: "case cannot be repaired"

**Diagnosis**:
```java
// Check workflow info
WorkflowInfo info = dao.get(WorkflowInfo.class, "workflow_process_info-" + caseId);
System.out.println("Pend Path: " + info.getPendExecPath());
System.out.println("Complete: " + info.getIsComplete());

for (ExecPath path : info.getExecPaths()) {
    System.out.println("Path: " + path.getName() + 
                      ", Status: " + path.getStatus() +
                      ", Workbasket: " + path.getPendWorkBasket());
}
```

**Solutions**:
1. Check if case is actually complete
2. Verify workflow definition exists
3. Check for data corruption
4. Try reopening case with new ticket

### Problem: Step Executes Multiple Times

**Symptoms**:
- Duplicate emails sent
- Multiple payments processed
- Duplicate database records

**Diagnosis**:
- Step is not idempotent
- Using lazy persistence mode

**Solutions**:
1. Implement idempotency checks
2. Use aggressive persistence mode
3. Add execution guards

### Problem: Process Variables Lost

**Symptoms**:
- Variables return null after recovery
- State information missing

**Diagnosis**:
- Not persisting after setting variables
- Using local variables instead of process variables

**Solutions**:
```java
// ❌ Wrong: Local variable
String orderId = "12345";
// Lost on crash

// ✅ Correct: Process variable
context.getProcessVariables().setValue(
    "order_id",
    WorkflowVariableType.STRING,
    "12345"
);
// Persisted and survives crash
```

## Related Documentation

- [Execution Paths](./execution-paths.md)
- [Process Variables](./process-variables.md)
- [Idempotency Patterns](../patterns/idempotency.md)
- [Error Handling](../patterns/error-handling.md)

## FAQ

**Q: How often should I persist state?**  
A: Use aggressive mode (after each step) unless you have idempotent steps and need maximum performance.

**Q: Can I disable crash recovery?**  
A: No, it's always enabled. It's a core feature of the engine.

**Q: What if I don't want a step to re-execute?**  
A: Make it idempotent so re-execution has no side effects.

**Q: How do I test crash recovery?**  
A: Use `System.exit(1)` or `kill -9` to simulate crash, then resume.

**Q: Can recovery fail?**  
A: Yes, if workflow info is corrupted or definition is missing. Manual intervention may be required.

**Q: What's the recovery time?**  
A: Typically milliseconds. The sanitization process is fast.

**Q: Do I need special database features?**  
A: No, but transactions help ensure atomicity of business operations.