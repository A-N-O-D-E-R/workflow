# Idempotency Patterns

## Overview

Idempotency is a critical property for building reliable, crash-proof workflows. An idempotent operation produces the same result whether executed once or multiple times. This guide provides comprehensive patterns and practices for implementing idempotent operations in Simple Workflow.

## Table of Contents
- [Understanding Idempotency](#understanding-idempotency)
- [Why Idempotency Matters](#why-idempotency-matters)
- [When Idempotency is Required](#when-idempotency-is-required)
- [Implementation Patterns](#implementation-patterns)
- [Database Patterns](#database-patterns)
- [External Service Patterns](#external-service-patterns)
- [State Management](#state-management)
- [Testing Idempotency](#testing-idempotency)
- [Common Pitfalls](#common-pitfalls)
- [Best Practices](#best-practices)
- [Real-World Examples](#real-world-examples)

## Understanding Idempotency

### Mathematical Definition

An operation `f(x)` is idempotent if:
```
f(f(x)) = f(x)
```

Applied multiple times, it produces the same result as applying it once.

### Examples

**✅ Idempotent Operations**:
```java
// Setting a value
x = 5;           // No matter how many times: x = 5
x = 5;
x = 5;

// Absolute value
abs(-5);         // Always returns 5
abs(abs(-5));

// DELETE WHERE id = 1
deleteById(1);   // First execution deletes, subsequent have no effect
deleteById(1);

// SET status = 'COMPLETED'
setStatus("COMPLETED");  // Same final state
setStatus("COMPLETED");
```

**❌ Non-Idempotent Operations**:
```java
// Incrementing
x = x + 1;       // Result depends on how many times executed
x = x + 1;       // x keeps growing

// Appending
list.add(item);  // List grows with each execution
list.add(item);

// INSERT without check
insert(record);  // Second execution causes duplicate key error
insert(record);

// Sending email (naive)
sendEmail(to, subject, body);  // User receives multiple emails
sendEmail(to, subject, body);
```

### Workflow Context

In Simple Workflow, operations may execute multiple times due to:

1. **Crash Recovery**: JVM crashes during step execution
2. **OK_PEND_EOR Response**: Deliberately re-execute step on resume
3. **Retry Logic**: Application-level retry mechanisms
4. **Network Failures**: Request sent but response not received
5. **Timeouts**: Operation completed but confirmation timeout

**Example Scenario**:
```
T0: Execute step "charge_payment"
T1: Call payment service
T2: Payment service charges card
T3: JVM crashes before saving state
---
T4: Application restarts
T5: Workflow resumes from last saved state
T6: Re-execute step "charge_payment"
T7: Call payment service again
❌ PROBLEM: Customer charged twice!
```

## Why Idempotency Matters

### 1. Crash Recovery Safety

```java
// Without idempotency
public TaskResponse executeStep() {
    paymentService.charge(amount);  // ❌ May charge twice after crash
    return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
}

// With idempotency
public TaskResponse executeStep() {
    if (!isAlreadyCharged()) {      // ✅ Check before executing
        paymentService.charge(amount);
        markAsCharged();
    }
    return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
}
```

### 2. Retry Reliability

```java
// Without idempotency
public TaskResponse executeStep() {
    try {
        sendNotification();  // ❌ User gets duplicate notifications on retry
    } catch (TransientException e) {
        return new TaskResponse(StepResponseType.OK_PEND_EOR, "", "retry_queue");
    }
}

// With idempotency
public TaskResponse executeStep() {
    if (!wasNotificationSent()) {  // ✅ Check first
        try {
            sendNotification();
            markNotificationSent();
        } catch (TransientException e) {
            return new TaskResponse(StepResponseType.OK_PEND_EOR, "", "retry_queue");
        }
    }
}
```

### 3. Data Consistency

```java
// Without idempotency
public TaskResponse executeStep() {
    int currentCount = getCount();
    setCount(currentCount + 1);  // ❌ Race condition, incorrect count
}

// With idempotency
public TaskResponse executeStep() {
    Boolean counted = vars.getBoolean("item_counted");
    if (!Boolean.TRUE.equals(counted)) {  // ✅ Count only once
        database.incrementCounter();
        vars.setValue("item_counted", WorkflowVariableType.BOOLEAN, true);
    }
}
```

## When Idempotency is Required

### Always Required

1. **Steps returning OK_PEND_EOR**
   ```java
   return new TaskResponse(
       StepResponseType.OK_PEND_EOR,  // Will re-execute this step
       "",
       "queue"
   );
   ```

2. **Steps with external service calls**
   ```java
   paymentService.charge();
   emailService.send();
   smsService.notify();
   ```

3. **Steps modifying shared state**
   ```java
   database.insert();
   database.update();
   cache.set();
   ```

4. **Using lazy persistence mode**
   ```java
   WorkflowService.instance().setWriteProcessInfoAfterEachStep(false);
   ```

### Recommended (Best Practice)

Even with aggressive persistence, implement idempotency for:
- Financial transactions
- Notifications/communications
- Inventory operations
- Status changes
- Critical business operations

## Implementation Patterns

### Pattern 1: Check-Then-Execute

The most common pattern - check if already executed before proceeding.

```java
public class CheckThenExecuteStep implements InvokableTask {
    
    private static final String EXECUTED_FLAG = "payment_executed";
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Check if already executed
        Boolean alreadyExecuted = vars.getBoolean(EXECUTED_FLAG);
        if (Boolean.TRUE.equals(alreadyExecuted)) {
            log.info("Payment already executed for case {}, skipping", 
                context.getCaseId());
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
        
        // Execute operation
        try {
            String transactionId = paymentService.charge(
                context.getCaseId(),
                getAmount(vars)
            );
            
            // Mark as executed
            vars.setValue(EXECUTED_FLAG, WorkflowVariableType.BOOLEAN, true);
            vars.setValue("transaction_id", WorkflowVariableType.STRING, transactionId);
            
            log.info("Payment executed: {}", transactionId);
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            log.error("Payment failed: {}", e.getMessage());
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "payment_error_queue"
            );
        }
    }
}
```

**When to use**: Most scenarios, especially with process variables

**Advantages**:
- Simple and explicit
- Easy to understand and debug
- Works with process variables

**Considerations**:
- Process variables must be persisted
- Flag should be set atomically with operation

### Pattern 2: Idempotency Keys

Use unique keys to ensure operations execute only once.

```java
public class IdempotencyKeyStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Generate idempotency key (unique per case + operation)
        String idempotencyKey = generateIdempotencyKey(
            context.getCaseId(),
            "payment_charge"
        );
        
        try {
            // Service uses idempotency key to prevent duplicates
            PaymentResult result = paymentService.chargeWithIdempotencyKey(
                idempotencyKey,
                getPaymentRequest(vars)
            );
            
            // Store result
            vars.setValue("payment_status", WorkflowVariableType.STRING, result.getStatus());
            vars.setValue("transaction_id", WorkflowVariableType.STRING, result.getTransactionId());
            
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "payment_error_queue"
            );
        }
    }
    
    private String generateIdempotencyKey(String caseId, String operation) {
        // Combine case ID and operation name for unique key
        return String.format("%s_%s_%d", 
            caseId, 
            operation, 
            context.getProcessVariables().getLong("workflow_start_time")
        );
    }
}
```

**Example with Stripe API**:
```java
PaymentIntent intent = PaymentIntent.create(
    PaymentIntentCreateParams.builder()
        .setAmount(amount)
        .setCurrency("usd")
        .build(),
    RequestOptions.builder()
        .setIdempotencyKey("order_" + orderId + "_payment")
        .build()
);
```

**When to use**: External services that support idempotency keys

**Advantages**:
- Service handles deduplication
- Works across service restarts
- Standard pattern for many APIs (Stripe, AWS, etc.)

**Considerations**:
- Service must support idempotency keys
- Key must be deterministic and unique per operation
- Key should include sufficient context

### Pattern 3: Natural Idempotency

Design operations to be naturally idempotent.

```java
public class NaturallyIdempotentStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        String orderId = vars.getString("order_id");
        
        // SET operations are naturally idempotent
        orderRepository.setStatus(orderId, OrderStatus.CONFIRMED);
        orderRepository.setConfirmationTime(orderId, Instant.now());
        orderRepository.setConfirmedBy(orderId, getCurrentUser());
        
        // No need for execution flags - setting same value multiple times is safe
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

**Examples of naturally idempotent operations**:
```java
// Setting values (not incrementing)
user.setStatus("ACTIVE");
order.setConfirmed(true);
cache.put(key, value);

// DELETE operations
repository.deleteById(id);  // Deleting non-existent record is safe

// Absolute operations (not relative)
account.setBalance(1000);  // Not: account.addToBalance(100)

// Replacing (not appending)
file.write(content);  // Overwrites
list.set(index, value);  // Replaces at index
```

**When to use**: When possible, design operations this way from the start

**Advantages**:
- No additional checks needed
- Simplest implementation
- Best performance

**Considerations**:
- Not always possible
- May require redesigning operations
- Timestamp updates may not be truly idempotent

### Pattern 4: Database Constraints

Use database constraints to enforce idempotency.

```java
public class DatabaseConstraintStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        try {
            // Primary key or unique constraint prevents duplicates
            orderItemRepository.insert(new OrderItem(
                context.getCaseId(),  // Part of composite primary key
                "ITEM-123",           // Part of composite primary key
                5                     // Quantity
            ));
            
            log.info("Order item inserted");
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (DuplicateKeyException e) {
            // Already exists - this is success from idempotency perspective
            log.info("Order item already exists (idempotent success)");
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        } catch (Exception e) {
            log.error("Failed to insert order item: {}", e.getMessage());
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "database_error_queue"
            );
        }
    }
}
```

**Database Schema**:
```sql
CREATE TABLE order_items (
    order_id VARCHAR(50) NOT NULL,
    item_id VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (order_id, item_id)  -- Prevents duplicates
);

CREATE TABLE audit_log (
    case_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_timestamp BIGINT NOT NULL,
    event_data TEXT,
    UNIQUE (case_id, event_type, event_timestamp)  -- Prevents duplicate events
);
```

**When to use**: Database operations where uniqueness can be enforced

**Advantages**:
- Database guarantees idempotency
- No application-level checks needed
- Works across application instances

**Considerations**:
- Requires proper schema design
- Must handle constraint violations gracefully
- May not work for all operations

### Pattern 5: Version-Based Optimistic Locking

Use version numbers to ensure operations execute only once.

```java
public class VersionedStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        String orderId = vars.getString("order_id");
        
        // Get current version
        Order order = orderRepository.findById(orderId);
        int currentVersion = order.getVersion();
        
        // Attempt to update with version check
        boolean updated = orderRepository.updateWithVersion(
            orderId,
            currentVersion,
            newStatus -> {
                order.setStatus(OrderStatus.CONFIRMED);
                order.setConfirmationTime(Instant.now());
            }
        );
        
        if (updated) {
            log.info("Order updated to version {}", currentVersion + 1);
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        } else {
            // Version mismatch - already updated by another execution
            log.info("Order already updated (idempotent success)");
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
    }
}
```

**JPA Implementation**:
```java
@Entity
public class Order {
    @Id
    private String orderId;
    
    @Version  // JPA manages version automatically
    private Long version;
    
    private OrderStatus status;
    // ... other fields
}

// JPA automatically handles version checking
@Transactional
public void updateOrder(String orderId, OrderStatus newStatus) {
    Order order = entityManager.find(Order.class, orderId);
    order.setStatus(newStatus);
    entityManager.merge(order);  // Will throw OptimisticLockException if version changed
}
```

**When to use**: Concurrent updates to shared entities

**Advantages**:
- Prevents lost updates
- Works across distributed systems
- Standard pattern in JPA/Hibernate

**Considerations**:
- Requires version field in entity
- Must handle OptimisticLockException
- May need retry logic

### Pattern 6: State Machine Guards

Use current state to guard transitions.

```java
public class StateMachineStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        String orderId = vars.getString("order_id");
        
        Order order = orderRepository.findById(orderId);
        
        // Only transition if in expected state
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.PROCESSING);
            orderRepository.save(order);
            log.info("Order status changed to PROCESSING");
            
        } else if (order.getStatus() == OrderStatus.PROCESSING) {
            // Already processing - idempotent success
            log.info("Order already in PROCESSING state");
            
        } else {
            // Unexpected state
            log.warn("Order in unexpected state: {}", order.getStatus());
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                "Order in invalid state: " + order.getStatus(),
                "state_error_queue"
            );
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

**State Transition Validation**:
```java
public class OrderStateMachine {
    
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
        OrderStatus.PENDING, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
        OrderStatus.PROCESSING, Set.of(OrderStatus.CONFIRMED, OrderStatus.FAILED),
        OrderStatus.CONFIRMED, Set.of(OrderStatus.SHIPPED),
        OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED)
    );
    
    public boolean canTransition(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
    
    public void transitionTo(Order order, OrderStatus newStatus) {
        if (order.getStatus() == newStatus) {
            // Already in target state - idempotent
            return;
        }
        
        if (!canTransition(order.getStatus(), newStatus)) {
            throw new IllegalStateTransitionException(
                String.format("Cannot transition from %s to %s", 
                    order.getStatus(), newStatus)
            );
        }
        
        order.setStatus(newStatus);
    }
}
```

**When to use**: Operations that change state

**Advantages**:
- Enforces valid state transitions
- Naturally idempotent
- Easy to reason about

**Considerations**:
- Requires well-defined state machine
- Must handle all edge cases
- Need atomic read-check-update

### Pattern 7: Distributed Locks

Use distributed locks for critical sections.

```java
public class DistributedLockStep implements InvokableTask {
    
    private final RedissonClient redisson;
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        String lockKey = "payment_lock_" + context.getCaseId();
        
        RLock lock = redisson.getLock(lockKey);
        
        try {
            // Try to acquire lock
            boolean acquired = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Could not acquire lock for case {}", context.getCaseId());
                return new TaskResponse(
                    StepResponseType.OK_PEND_EOR,
                    "Lock not available",
                    "retry_queue"
                );
            }
            
            try {
                // Check if already executed (within lock)
                Boolean executed = vars.getBoolean("payment_executed");
                if (Boolean.TRUE.equals(executed)) {
                    log.info("Payment already executed");
                    return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
                }
                
                // Execute payment
                String transactionId = paymentService.charge(
                    context.getCaseId(),
                    getAmount(vars)
                );
                
                // Mark as executed
                vars.setValue("payment_executed", WorkflowVariableType.BOOLEAN, true);
                vars.setValue("transaction_id", WorkflowVariableType.STRING, transactionId);
                
                return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
                
            } finally {
                lock.unlock();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                "Lock acquisition interrupted",
                "error_queue"
            );
        }
    }
}
```

**When to use**: Distributed systems with concurrent access

**Advantages**:
- Works across multiple instances
- Prevents race conditions
- Strong consistency guarantee

**Considerations**:
- Requires distributed lock service (Redis, Zookeeper, etc.)
- Adds latency
- Lock timeouts need careful tuning
- Must handle lock failures

## Database Patterns

### Pattern 1: Upsert (Insert or Update)

```java
public class UpsertStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        CustomerProfile profile = buildCustomerProfile(vars);
        
        // PostgreSQL upsert
        String sql = """
            INSERT INTO customer_profiles (customer_id, name, email, phone)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (customer_id)
            DO UPDATE SET
                name = EXCLUDED.name,
                email = EXCLUDED.email,
                phone = EXCLUDED.phone,
                updated_at = NOW()
            """;
        
        jdbcTemplate.update(sql,
            profile.getCustomerId(),
            profile.getName(),
            profile.getEmail(),
            profile.getPhone()
        );
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

**MySQL/MariaDB**:
```sql
INSERT INTO customer_profiles (customer_id, name, email, phone)
VALUES (?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    email = VALUES(email),
    phone = VALUES(phone),
    updated_at = NOW()
```

**JPA/Hibernate**:
```java
@Transactional
public void upsertCustomerProfile(CustomerProfile profile) {
    CustomerProfile existing = entityManager.find(
        CustomerProfile.class, 
        profile.getCustomerId()
    );
    
    if (existing != null) {
        // Update
        existing.setName(profile.getName());
        existing.setEmail(profile.getEmail());
        existing.setPhone(profile.getPhone());
        entityManager.merge(existing);
    } else {
        // Insert
        entityManager.persist(profile);
    }
}
```

### Pattern 2: Conditional Insert

```java
public class ConditionalInsertStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        String caseId = context.getCaseId();
        
        // Check if already exists
        boolean exists = auditRepository.existsByCaseIdAndEventType(
            caseId,
            "ORDER_CREATED"
        );
        
        if (!exists) {
            // Insert only if doesn't exist
            AuditRecord record = new AuditRecord(
                caseId,
                "ORDER_CREATED",
                System.currentTimeMillis(),
                buildAuditData(vars)
            );
            
            try {
                auditRepository.insert(record);
                log.info("Audit record created");
            } catch (DuplicateKeyException e) {
                // Race condition - another thread inserted
                log.info("Audit record already exists (race condition handled)");
            }
        } else {
            log.info("Audit record already exists");
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### Pattern 3: Update Only If Changed

```java
public class UpdateIfChangedStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        String orderId = vars.getString("order_id");
        OrderStatus newStatus = OrderStatus.CONFIRMED;
        
        // Update only if status different
        int rowsUpdated = jdbcTemplate.update(
            "UPDATE orders SET status = ?, updated_at = NOW() " +
            "WHERE order_id = ? AND status != ?",
            newStatus.name(),
            orderId,
            newStatus.name()
        );
        
        if (rowsUpdated > 0) {
            log.info("Order status updated to {}", newStatus);
        } else {
            log.info("Order already in status {} (idempotent)", newStatus);
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### Pattern 4: Atomic Counter with Cap

```java
public class AtomicCounterStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        String itemId = vars.getString("item_id");
        
        // Atomically increment but only if below cap
        int rowsUpdated = jdbcTemplate.update(
            "UPDATE inventory " +
            "SET reserved_quantity = reserved_quantity + 1 " +
            "WHERE item_id = ? " +
            "AND reserved_quantity < available_quantity",
            itemId
        );
        
        if (rowsUpdated > 0) {
            log.info("Inventory reserved for item {}", itemId);
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        } else {
            log.warn("Insufficient inventory for item {}", itemId);
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                "Insufficient inventory",
                "inventory_error_queue",
                2001
            );
        }
    }
}
```

## External Service Patterns

### Pattern 1: Service-Side Idempotency Keys

```java
public class StripePaymentStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        String idempotencyKey = String.format(
            "order_%s_payment_%d",
            context.getCaseId(),
            vars.getLong("workflow_start_time")
        );
        
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(getAmount(vars))
                .setCurrency("usd")
                .setCustomer(vars.getString("stripe_customer_id"))
                .setDescription("Order " + vars.getString("order_id"))
                .build();
            
            RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();
            
            PaymentIntent intent = PaymentIntent.create(params, requestOptions);
            
            vars.setValue("payment_intent_id", WorkflowVariableType.STRING, intent.getId());
            vars.setValue("payment_status", WorkflowVariableType.STRING, intent.getStatus());
            
            log.info("Payment intent created: {}", intent.getId());
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (StripeException e) {
            log.error("Stripe payment failed: {}", e.getMessage());
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "payment_error_queue"
            );
        }
    }
}
```

### Pattern 2: Query-Before-Action

```java
public class QueryBeforeActionStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        String orderId = vars.getString("order_id");
        
        // Query service to check if already done
        ShipmentStatus status = shippingService.getShipmentStatus(orderId);
        
        if (status != null && status.isCreated()) {
            log.info("Shipment already created for order {}", orderId);
            vars.setValue("tracking_number", WorkflowVariableType.STRING, status.getTrackingNumber());
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
        
        // Not created yet - create it
        try {
            Shipment shipment = shippingService.createShipment(
                buildShipmentRequest(vars)
            );
            
            vars.setValue("tracking_number", WorkflowVariableType.STRING, shipment.getTrackingNumber());
            log.info("Shipment created: {}", shipment.getTrackingNumber());
            
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "shipping_error_queue"
            );
        }
    }
}
```

### Pattern 3: Client-Side Deduplication

```java
public class ClientSideDeduplicationStep implements InvokableTask {
    
    private final Set<String> sentNotifications = ConcurrentHashMap.newKeySet();
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        String deduplicationKey = String.format(
            "%s_%s",
            context.getCaseId(),
            "order_confirmation"
        );
        
        // Check local deduplication cache
        if (sentNotifications.contains(deduplicationKey)) {
            log.info("Notification already sent (client-side dedup)");
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
        
        // Also check process variables (survives restarts)
        Boolean sent = vars.getBoolean("confirmation_email_sent");
        if (Boolean.TRUE.equals(sent)) {
            log.info("Notification already sent (from process variables)");
            sentNotifications.add(deduplicationKey);  // Update cache
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
        
        try {
            // Send notification
            emailService.sendOrderConfirmation(
                vars.getString("customer_email"),
                vars.getString("order_id")
            );
            
            // Mark as sent
            vars.setValue("confirmation_email_sent", WorkflowVariableType.BOOLEAN, true);
            sentNotifications.add(deduplicationKey);
            
            log.info("Confirmation email sent");
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "notification_error_queue"
            );
        }
    }
}
```

### Pattern 4: Reconciliation Pattern

```java
public class ReconciliationStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        String orderId = vars.getString("order_id");
        
        // Get expected state from workflow
        OrderStatus expectedStatus = OrderStatus.CONFIRMED;
        String expectedTrackingNumber = vars.getString("tracking_number");
        
        // Get actual state from external system
        ExternalOrder externalOrder = externalSystem.getOrder(orderId);
        
        // Reconcile
        if (externalOrder == null) {
            // Doesn't exist - create it
            externalSystem.createOrder(buildOrderRequest(vars));
            log.info("Created order in external system");
            
        } else if (externalOrder.getStatus() != expectedStatus) {
            // Exists but wrong state - update it
            externalSystem.updateOrderStatus(orderId, expectedStatus);
            log.info("Updated order status in externalsystem");
            
        } else if (!expectedTrackingNumber.equals(externalOrder.getTrackingNumber())) {
            // Exists but wrong tracking number - update it
            externalSystem.updateTrackingNumber(orderId, expectedTrackingNumber);
            log.info("Updated tracking number in external system");
            
        } else {
            // Already in correct state
            log.info("Order already in correct state (idempotent)");
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### Pattern 5: Two-Phase Commit with Compensation

```java
public class TwoPhaseCommitStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Phase 1: Prepare
        String reservationId = vars.getString("inventory_reservation_id");
        if (reservationId == null) {
            // First execution - create reservation
            reservationId = inventoryService.reserveInventory(
                vars.getString("item_id"),
                vars.getInteger("quantity")
            );
            vars.setValue("inventory_reservation_id", WorkflowVariableType.STRING, reservationId);
            log.info("Created inventory reservation: {}", reservationId);
        } else {
            // Already reserved - verify it exists
            if (!inventoryService.reservationExists(reservationId)) {
                log.warn("Reservation {} not found, recreating", reservationId);
                reservationId = inventoryService.reserveInventory(
                    vars.getString("item_id"),
                    vars.getInteger("quantity")
                );
                vars.setValue("inventory_reservation_id", WorkflowVariableType.STRING, reservationId);
            }
        }
        
        // Phase 2: Commit
        Boolean committed = vars.getBoolean("inventory_committed");
        if (!Boolean.TRUE.equals(committed)) {
            try {
                inventoryService.commitReservation(reservationId);
                vars.setValue("inventory_committed", WorkflowVariableType.BOOLEAN, true);
                log.info("Committed inventory reservation");
            } catch (ReservationExpiredException e) {
                // Reservation expired - compensate and retry
                log.warn("Reservation expired, retrying");
                vars.setValue("inventory_reservation_id", WorkflowVariableType.STRING, null);
                return new TaskResponse(
                    StepResponseType.OK_PEND_EOR,
                    "Reservation expired, retrying",
                    "retry_queue"
                );
            }
        } else {
            log.info("Inventory already committed");
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

## State Management

### Pattern 1: Process Variables as Execution Log

```java
public class ExecutionLogStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Maintain execution log in process variables
        @SuppressWarnings("unchecked")
        List<String> executionLog = (List<String>) vars.getValue(
            "execution_log",
            WorkflowVariableType.LIST_OF_OBJECT
        );
        if (executionLog == null) {
            executionLog = new ArrayList<>();
        }
        
        String currentOperation = "process_payment";
        
        // Check if already executed
        if (executionLog.contains(currentOperation)) {
            log.info("Operation {} already executed", currentOperation);
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
        
        try {
            // Execute operation
            String result = performOperation();
            
            // Add to execution log
            executionLog.add(currentOperation);
            vars.setValue("execution_log", WorkflowVariableType.LIST_OF_OBJECT, executionLog);
            
            // Store result
            vars.setValue(currentOperation + "_result", WorkflowVariableType.STRING, result);
            
            log.info("Operation {} completed", currentOperation);
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            log.error("Operation {} failed: {}", currentOperation, e.getMessage());
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "error_queue"
            );
        }
    }
}
```

### Pattern 2: Checkpointing

```java
public class CheckpointedStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Multi-phase operation with checkpoints
        String checkpoint = vars.getString("current_checkpoint");
        
        try {
            if (checkpoint == null || checkpoint.equals("START")) {
                // Phase 1: Validate
                validateOrder(vars);
                vars.setValue("current_checkpoint", WorkflowVariableType.STRING, "VALIDATED");
                checkpoint = "VALIDATED";
            }
            
            if (checkpoint.equals("VALIDATED")) {
                // Phase 2: Reserve resources
                reserveResources(vars);
                vars.setValue("current_checkpoint", WorkflowVariableType.STRING, "RESERVED");
                checkpoint = "RESERVED";
            }
            
            if (checkpoint.equals("RESERVED")) {
                // Phase 3: Process payment
                processPayment(vars);
                vars.setValue("current_checkpoint", WorkflowVariableType.STRING, "PAID");
                checkpoint = "PAID";
            }
            
            if (checkpoint.equals("PAID")) {
                // Phase 4: Confirm order
                confirmOrder(vars);
                vars.setValue("current_checkpoint", WorkflowVariableType.STRING, "CONFIRMED");
            }
            
            // All phases complete
            vars.setValue("current_checkpoint", WorkflowVariableType.STRING, null);
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            log.error("Failed at checkpoint {}: {}", checkpoint, e.getMessage());
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                "Failed at checkpoint: " + checkpoint,
                "error_queue"
            );
        }
    }
    
    private void validateOrder(WorkflowVariables vars) {
        // Validation logic - idempotent
        log.info("Validating order");
    }
    
    private void reserveResources(WorkflowVariables vars) {
        // Check if already reserved
        String reservationId = vars.getString("reservation_id");
        if (reservationId == null) {
            reservationId = resourceService.reserve();
            vars.setValue("reservation_id", WorkflowVariableType.STRING, reservationId);
            log.info("Resources reserved: {}", reservationId);
        } else {
            log.info("Resources already reserved: {}", reservationId);
        }
    }
    
    private void processPayment(WorkflowVariables vars) {
        // Check if already processed
        Boolean paid = vars.getBoolean("payment_processed");
        if (!Boolean.TRUE.equals(paid)) {
            String transactionId = paymentService.charge();
            vars.setValue("transaction_id", WorkflowVariableType.STRING, transactionId);
            vars.setValue("payment_processed", WorkflowVariableType.BOOLEAN, true);
            log.info("Payment processed: {}", transactionId);
        } else {
            log.info("Payment already processed");
        }
    }
    
    private void confirmOrder(WorkflowVariables vars) {
        // Confirmation is naturally idempotent (SET operation)
        orderRepository.setStatus(vars.getString("order_id"), OrderStatus.CONFIRMED);
        log.info("Order confirmed");
    }
}
```

### Pattern 3: Timestamp-Based Deduplication

```java
public class TimestampDeduplicationStep implements InvokableTask {
    
    private static final long DEDUPLICATION_WINDOW_MS = 5 * 60 * 1000; // 5 minutes
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        Long lastExecutionTime = vars.getLong("last_execution_timestamp");
        long now = System.currentTimeMillis();
        
        // Check if executed recently
        if (lastExecutionTime != null) {
            long timeSinceLastExecution = now - lastExecutionTime;
            
            if (timeSinceLastExecution < DEDUPLICATION_WINDOW_MS) {
                log.info("Operation executed {}ms ago, within deduplication window, skipping",
                    timeSinceLastExecution);
                return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            }
        }
        
        try {
            // Execute operation
            performOperation(vars);
            
            // Update timestamp
            vars.setValue("last_execution_timestamp", WorkflowVariableType.LONG, now);
            
            log.info("Operation executed at {}", now);
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            log.error("Operation failed: {}", e.getMessage());
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "error_queue"
            );
        }
    }
}
```

### Pattern 4: Content-Based Deduplication

```java
public class ContentBasedDeduplicationStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Build operation content
        String itemId = vars.getString("item_id");
        Integer quantity = vars.getInteger("quantity");
        String operation = String.format("reserve_%s_%d", itemId, quantity);
        
        // Generate content hash
        String contentHash = generateHash(operation);
        
        // Check if already executed with same content
        String lastContentHash = vars.getString("last_operation_hash");
        if (contentHash.equals(lastContentHash)) {
            log.info("Operation with same content already executed (hash: {})", contentHash);
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
        
        try {
            // Execute operation
            String reservationId = inventoryService.reserve(itemId, quantity);
            
            // Store content hash
            vars.setValue("last_operation_hash", WorkflowVariableType.STRING, contentHash);
            vars.setValue("reservation_id", WorkflowVariableType.STRING, reservationId);
            
            log.info("Operation executed (hash: {})", contentHash);
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            log.error("Operation failed: {}", e.getMessage());
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "error_queue"
            );
        }
    }
    
    private String generateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
```

## Testing Idempotency

### Unit Testing

```java
public class IdempotencyTest {
    
    private WorkflowContext context;
    private PaymentProcessingStep step;
    
    @Before
    public void setup() {
        context = TestManager.createTestContext("test-case-1");
        step = new PaymentProcessingStep(context, mockPaymentService);
    }
    
    @Test
    public void testIdempotency_executeTwice() {
        // Arrange
        when(mockPaymentService.charge(anyString(), anyDouble()))
            .thenReturn("txn-123");
        
        // Act - execute twice
        TaskResponse response1 = step.executeStep();
        TaskResponse response2 = step.executeStep();
        
        // Assert - service called only once
        verify(mockPaymentService, times(1)).charge(anyString(), anyDouble());
        
        // Both responses succeed
        assertEquals(StepResponseType.OK_PROCEED, response1.getUnitResponseType());
        assertEquals(StepResponseType.OK_PROCEED, response2.getUnitResponseType());
        
        // Same transaction ID stored
        String txnId = context.getProcessVariables().getString("transaction_id");
        assertEquals("txn-123", txnId);
    }
    
    @Test
    public void testIdempotency_multipleThreads() throws Exception {
        // Arrange
        when(mockPaymentService.charge(anyString(), anyDouble()))
            .thenReturn("txn-123");
        
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<TaskResponse>> futures = new ArrayList<>();
        
        // Act - execute concurrently
        for (int i = 0; i < threadCount; i++) {
            Future<TaskResponse> future = executor.submit(() -> {
                try {
                    return step.executeStep();
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }
        
        latch.await(10, TimeUnit.SECONDS);
        
        // Assert - service called only once despite concurrent execution
        verify(mockPaymentService, times(1)).charge(anyString(), anyDouble());
        
        // All responses succeed
        for (Future<TaskResponse> future : futures) {
            TaskResponse response = future.get();
            assertEquals(StepResponseType.OK_PROCEED, response.getUnitResponseType());
        }
        
        executor.shutdown();
    }
    
    @Test
    public void testIdempotency_afterCrashRecovery() {
        // Arrange
        when(mockPaymentService.charge(anyString(), anyDouble()))
            .thenReturn("txn-123");
        
        // Act - first execution
        TaskResponse response1 = step.executeStep();
        assertEquals(StepResponseType.OK_PROCEED, response1.getUnitResponseType());
        
        // Simulate crash and recovery - process variables preserved
        WorkflowVariables preservedVars = context.getProcessVariables();
        WorkflowContext newContext = TestManager.createTestContext("test-case-1");
        newContext.setProcessVariables(preservedVars);
        PaymentProcessingStep newStep = new PaymentProcessingStep(newContext, mockPaymentService);
        
        // Execute again after "recovery"
        TaskResponse response2 = newStep.executeStep();
        
        // Assert - service still called only once
        verify(mockPaymentService, times(1)).charge(anyString(), anyDouble());
        assertEquals(StepResponseType.OK_PROCEED, response2.getUnitResponseType());
    }
    
    @Test
    public void testIdempotency_withDifferentInputs() {
        // Arrange
        when(mockPaymentService.charge(anyString(), anyDouble()))
            .thenReturn("txn-123")
            .thenReturn("txn-456");
        
        // Act - execute with first amount
        context.getProcessVariables().setValue("amount", WorkflowVariableType.DOUBLE, 100.0);
        TaskResponse response1 = step.executeStep();
        
        // Change amount and execute again
        context.getProcessVariables().setValue("amount", WorkflowVariableType.DOUBLE, 200.0);
        TaskResponse response2 = step.executeStep();
        
        // Assert - executed only once (first execution "wins")
        verify(mockPaymentService, times(1)).charge(anyString(), anyDouble());
        
        // First transaction ID preserved
        String txnId = context.getProcessVariables().getString("transaction_id");
        assertEquals("txn-123", txnId);
    }
}
```

### Integration Testing

```java
@SpringBootTest
public class IdempotencyIntegrationTest {
    
    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private CommonService dao;
    
    @Autowired
    private PaymentService paymentService;
    
    @Test
    public void testWorkflowIdempotency() {
        // Start workflow
        String caseId = "idempotency-test-1";
        String workflowJson = loadWorkflowWithPaymentStep();
        
        runtimeService.startCase(caseId, workflowJson, null, null);
        
        // Wait for completion
        await().atMost(10, TimeUnit.SECONDS)
            .until(() -> isWorkflowComplete(caseId));
        
        // Verify payment made once
        int paymentCount = paymentService.getPaymentCount(caseId);
        assertEquals(1, paymentCount);
        
        // Simulate crash and restart
        WorkflowInfo info = dao.get(
            WorkflowInfo.class,
            "workflow_process_info-" + caseId
        );
        
        // Manually reset to payment step (simulating crash during execution)
        info.getExecPaths().get(0).setStep("process_payment");
        info.getExecPaths().get(0).setStatus(ExecPathStatus.STARTED);
        info.getExecPaths().get(0).setUnitResponseType(null);
        info.setPendExecPath(".");
        info.setIsComplete(false);
        dao.update("workflow_process_info-" + caseId, info);
        
        // Resume workflow
        runtimeService.resumeCase(caseId);
        
        // Wait for completion again
        await().atMost(10, TimeUnit.SECONDS)
            .until(() -> isWorkflowComplete(caseId));
        
        // Verify payment still made only once (idempotent)
        paymentCount = paymentService.getPaymentCount(caseId);
        assertEquals(1, paymentCount);
    }
    
    @Test
    public void testConcurrentResume() throws Exception {
        // Start workflow and pend it
        String caseId = "concurrent-test-1";
        String workflowJson = loadWorkflowWithPendingStep();
        
        runtimeService.startCase(caseId, workflowJson, null, null);
        
        // Wait for pend
        await().atMost(5, TimeUnit.SECONDS)
            .until(() -> isWorkflowPended(caseId));
        
        // Try to resume concurrently from multiple threads
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    runtimeService.resumeCase(caseId);
                } catch (Exception e) {
                    // Some may fail due to locking - that's OK
                    log.debug("Resume failed (expected): {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Wait for completion
        await().atMost(10, TimeUnit.SECONDS)
            .until(() -> isWorkflowComplete(caseId));
        
        // Verify no duplicate operations
        verifyNoDuplicateOperations(caseId);
    }
}
```

### Property-Based Testing

```java
public class IdempotencyPropertyTest {
    
    @Test
    public void testIdempotencyProperty_anyNumberOfExecutions() {
        // Property: Executing N times produces same result as executing once
        
        for (int executionCount = 1; executionCount <= 10; executionCount++) {
            WorkflowContext context = TestManager.createTestContext("prop-test-" + executionCount);
            PaymentProcessingStep step = new PaymentProcessingStep(context, mockPaymentService);
            
            when(mockPaymentService.charge(anyString(), anyDouble()))
                .thenReturn("txn-" + executionCount);
            
            // Execute N times
            TaskResponse lastResponse = null;
            for (int i = 0; i < executionCount; i++) {
                lastResponse = step.executeStep();
            }
            
            // Assert
            verify(mockPaymentService, times(1)).charge(anyString(), anyDouble());
            assertEquals(StepResponseType.OK_PROCEED, lastResponse.getUnitResponseType());
            
            String txnId = context.getProcessVariables().getString("transaction_id");
            assertEquals("txn-" + executionCount, txnId);
            
            reset(mockPaymentService);
        }
    }
    
    @Test
    public void testIdempotencyProperty_anyExecutionOrder() {
        // Property: Result independent of execution timing
        
        List<Long> delays = Arrays.asList(0L, 10L, 100L, 1000L);
        
        for (Long delay : delays) {
            WorkflowContext context = TestManager.createTestContext("timing-test-" + delay);
            PaymentProcessingStep step = new PaymentProcessingStep(context, mockPaymentService);
            
            when(mockPaymentService.charge(anyString(), anyDouble()))
                .thenAnswer(invocation -> {
                    Thread.sleep(delay);
                    return "txn-123";
                });
            
            // Execute twice with delay
            step.executeStep();
            Thread.sleep(delay);
            step.executeStep();
            
            // Assert - same result regardless of timing
            verify(mockPaymentService, times(1)).charge(anyString(), anyDouble());
            
            reset(mockPaymentService);
        }
    }
}
```

## Common Pitfalls

### Pitfall 1: Using Local Variables

❌ **Wrong - Not Idempotent**:
```java
public class NonIdempotentStep implements InvokableTask {
    
    private int executionCount = 0;  // Lost on crash!
    
    @Override
    public TaskResponse executeStep() {
        executionCount++;  // Not persisted
        
        if (executionCount > 1) {
            // This check won't work after crash
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
        
        performOperation();
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

✅ **Correct - Use Process Variables**:
```java
public class IdempotentStep implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        Integer executionCount = vars.getInteger("execution_count");
        if (executionCount == null) executionCount = 0;
        executionCount++;
        vars.setValue("execution_count", WorkflowVariableType.INTEGER, executionCount);
        
        if (executionCount > 1) {
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
        
        performOperation();
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### Pitfall 2: Incrementing Instead of Setting

❌ **Wrong - Not Idempotent**:
```java
public class NonIdempotentIncrement implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        // Increment on each execution
        int count = database.getCount();
        database.setCount(count + 1);  // Multiple executions = wrong count
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

✅ **Correct - Check Before Incrementing**:
```java
public class IdempotentIncrement implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        Boolean counted = vars.getBoolean("item_counted");
        if (!Boolean.TRUE.equals(counted)) {
            database.incrementCount();
            vars.setValue("item_counted", WorkflowVariableType.BOOLEAN, true);
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### Pitfall 3: Not Checking Before External Calls

❌ **Wrong - Duplicate API Calls**:
```java
public class NonIdempotentApiCall implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        // Always calls API, even on re-execution
        String result = externalApi.createResource();
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

✅ **Correct - Check First**:
```java
public class IdempotentApiCall implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        String resourceId = vars.getString("resource_id");
        if (resourceId == null) {
            // First execution
            resourceId = externalApi.createResource();
            vars.setValue("resource_id", WorkflowVariableType.STRING, resourceId);
        } else {
            // Already created
            log.info("Resource already created: {}", resourceId);
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### Pitfall 4: Appending to Collections

❌ **Wrong - Duplicate Entries**:
```java
public class NonIdempotentAppend implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        List<String> items = getItemsList();
        items.add("new_item");  // Adds on each execution
        saveItemsList(items);
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

✅ **Correct - Check Before Adding**:
```java
public class IdempotentAppend implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        List<String> items = getItemsList();
        String newItem = "new_item";
        
        if (!items.contains(newItem)) {
            items.add(newItem);
            saveItemsList(items);
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### Pitfall 5: Time-Based Logic

❌ **Wrong - Non-Deterministic**:
```java
public class NonIdempotentTimeLogic implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        // Result depends on when executed
        long timestamp = System.currentTimeMillis();
        
        if (timestamp % 2 == 0) {
            doOptionA();
        } else {
            doOptionB();
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

✅ **Correct - Store Decision**:
```java
public class IdempotentTimeLogic implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        String decision = vars.getString("time_based_decision");
        if (decision == null) {
            // Make decision once
            long timestamp = System.currentTimeMillis();
            decision = (timestamp % 2 == 0) ? "OPTION_A" : "OPTION_B";
            vars.setValue("time_based_decision", WorkflowVariableType.STRING, decision);
        }
        
        // Execute based on stored decision
        if ("OPTION_A".equals(decision)) {
            doOptionA();
        } else {
            doOptionB();
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### Pitfall 6: Not Handling Partial Failures

❌ **Wrong - Inconsistent State**:
```java
public class NonIdempotentPartialFailure implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        operation1();  // Succeeds
        operation2();  // Fails
        operation3();  // Never executes
        
        // On retry: operation1 executes again!
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

✅ **Correct - Track Each Operation**:
```java
public class IdempotentPartialFailure implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        if (!Boolean.TRUE.equals(vars.getBoolean("operation1_complete"))) {
            operation1();
            vars.setValue("operation1_complete", WorkflowVariableType.BOOLEAN, true);
        }
        
        if (!Boolean.TRUE.equals(vars.getBoolean("operation2_complete"))) {
            operation2();
            vars.setValue("operation2_complete", WorkflowVariableType.BOOLEAN, true);
        }
        
        if (!Boolean.TRUE.equals(vars.getBoolean("operation3_complete"))) {
            operation3();
            vars.setValue("operation3_complete", WorkflowVariableType.BOOLEAN, true);
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

## Best Practices

### 1. Always Use Process Variables for State

✅ **DO**:
```java
// Store execution state in process variables
WorkflowVariables vars = context.getProcessVariables();
vars.setValue("operation_executed", WorkflowVariableType.BOOLEAN, true);
vars.setValue("result_data", WorkflowVariableType.STRING, result);
```

❌ **DON'T**:
```java
// Don't use instance variables or static fields
private boolean executed = false;  // Lost on crash
private static Set<String> processedIds = new HashSet<>();  // Not persisted
```

### 2. Check Before Executing

✅ **DO**:
```java
Boolean executed = vars.getBoolean("payment_processed");
if (!Boolean.TRUE.equals(executed)) {
    // Execute only if not already done
    processPayment();
    vars.setValue("payment_processed", WorkflowVariableType.BOOLEAN, true);
}
```

❌ **DON'T**:
```java
// Don't execute without checking
processPayment();  // May execute multiple times
```

### 3. Use Idempotency Keys for External Services

✅ **DO**:
```java
String idempotencyKey = String.format("%s_%s_%d",
    context.getCaseId(),
    "payment",
    vars.getLong("workflow_start_time")
);

service.callWithIdempotencyKey(idempotencyKey, request);
```

### 4. Design for Idempotency from the Start

✅ **DO**:
```java
// Use SET operations instead of increments
order.setStatus(OrderStatus.CONFIRMED);
order.setConfirmationTime(Instant.now());

// Use UPSERT instead of INSERT
repository.upsert(record);

// Use database constraints
CREATE UNIQUE INDEX idx_unique_operation ON operations(case_id, operation_type);
```

### 5. Log Idempotency Checks

✅ **DO**:
```java
Boolean executed = vars.getBoolean("payment_processed");
if (Boolean.TRUE.equals(executed)) {
    log.info("Payment already processed for case {}, skipping (idempotent)", 
        context.getCaseId());
    return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
}
log.info("Processing payment for case {} (first execution)", context.getCaseId());
```

❌ **DON'T**:
```java
// Don't skip silently without logging
if (executed) {
    return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
}
```

### 6. Test Idempotency Explicitly

✅ **DO**:
```java
@Test
public void testIdempotency() {
    // Execute twice
    TaskResponse response1 = step.executeStep();
    TaskResponse response2 = step.executeStep();
    
    // Verify service called only once
    verify(mockService, times(1)).performOperation();
    
    // Both responses succeed
    assertEquals(StepResponseType.OK_PROCEED, response1.getUnitResponseType());
    assertEquals(StepResponseType.OK_PROCEED, response2.getUnitResponseType());
}
```

### 7. Use Null-Safe Checks

✅ **DO**:
```java
Boolean executed = vars.getBoolean("payment_processed");
if (!Boolean.TRUE.equals(executed)) {  // Handles null safely
    processPayment();
}
```

❌ **DON'T**:
```java
Boolean executed = vars.getBoolean("payment_processed");
if (!executed) {  // NullPointerException if null!
    processPayment();
}
```

### 8. Document Idempotency Guarantees

✅ **DO**:
```java
/**
 * Processes payment for the order.
 * 
 * <p>This step is idempotent - it can be safely executed multiple times.
 * The payment will be charged exactly once, even if the step is re-executed
 * due to crash recovery or retry logic.
 * 
 * <p>Idempotency is achieved by checking the 'payment_processed' flag in
 * process variables before calling the payment service.
 * 
 * @return OK_PROCEED if payment successful or already processed
 *         ERROR_PEND if payment fails
 */
public class PaymentProcessingStep implements InvokableTask {
    // ...
}
```

### 9. Handle Race Conditions

✅ **DO**:
```java
synchronized (vars) {
    Boolean executed = vars.getBoolean("payment_processed");
    if (!Boolean.TRUE.equals(executed)) {
        processPayment();
        vars.setValue("payment_processed", WorkflowVariableType.BOOLEAN, true);
    }
}
```

### 10. Verify External State

✅ **DO**:
```java
// Query external system to verify
String resourceId = vars.getString("resource_id");
if (resourceId != null) {
    // Verify it actually exists
    if (!externalService.exists(resourceId)) {
        log.warn("Resource {} not found, recreating", resourceId);
        resourceId = null;  // Force recreation
    }
}

if (resourceId == null) {
    resourceId = externalService.create();
    vars.setValue("resource_id", WorkflowVariableType.STRING, resourceId);
}
```

## Real-World Examples

### Example 1: E-Commerce Order Processing

```java
public class OrderProcessingExample {
    
    /**
     * Complete idempotent order processing workflow
     */
    public static class ProcessOrderStep implements InvokableTask {
        
        private final InventoryService inventoryService;
        private final PaymentService paymentService;
        private final ShippingService shippingService;
        private final NotificationService notificationService;
        
        @Override
        public TaskResponse executeStep() {
            WorkflowVariables vars = context.getProcessVariables();
            String orderId = vars.getString("order_id");
            
            try {
                // Step 1: Reserve inventory (idempotent)
                String reservationId = reserveInventory(vars, orderId);
                
                // Step 2: Process payment (idempotent)
                String transactionId = processPayment(vars, orderId);
                
                // Step 3: Create shipment (idempotent)
                String trackingNumber = createShipment(vars, orderId);
                
                // Step 4: Send confirmation (idempotent)
                sendConfirmation(vars, orderId);
                
                log.info("Order {} processed successfully", orderId);
                return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
                
            } catch (InsufficientInventoryException e) {
                // Business error - compensate and fail
                compensatePayment(vars);
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    "Insufficient inventory",
                    "inventory_error_queue",
                    2001
                );
                
            } catch (PaymentDeclinedException e) {
                // Business error - compensate and fail
                compensateInventory(vars);
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    "Payment declined: " + e.getReason(),
                    "payment_declined_queue",
                    2002
                );
                
            } catch (Exception e) {
                // Technical error - compensate and retry
                log.error("Order processing failed: {}", e.getMessage());
                compensateAll(vars);
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    "Technical error: " + e.getMessage(),
                    "technical_error_queue",
                    3001
                );
            }
        }
        
        private String reserveInventory(WorkflowVariables vars, String orderId) {
            String reservationId = vars.getString("inventory_reservation_id");
            
            if (reservationId != null) {
                // Already reserved - verify it's still valid
                if (inventoryService.isReservationValid(reservationId)) {
                    log.info("Inventory already reserved: {}", reservationId);
                    return reservationId;
                } else {
                    log.warn("Reservation {} expired, creating new one", reservationId);
                    reservationId = null;
                }
            }
            
            // Reserve inventory
            List<OrderItem> items = getOrderItems(vars);
            reservationId = inventoryService.reserve(orderId, items);
            vars.setValue("inventory_reservation_id", WorkflowVariableType.STRING, reservationId);
            
            log.info("Inventory reserved: {}", reservationId);
            return reservationId;
        }
        
        private String processPayment(WorkflowVariables vars, String orderId) {
            Boolean paymentProcessed = vars.getBoolean("payment_processed");
            if (Boolean.TRUE.equals(paymentProcessed)) {
                String transactionId = vars.getString("transaction_id");
                log.info("Payment already processed: {}", transactionId);
                return transactionId;
            }
            
            // Generate idempotency key
            String idempotencyKey = String.format(
                "order_%s_payment_%d",
                orderId,
                vars.getLong("workflow_start_time")
            );
            
            // Process payment with idempotency key
            PaymentResult result = paymentService.charge(
                idempotencyKey,
                orderId,
                vars.getDouble("total_amount"),
                vars.getString("payment_method_id")
            );
            
            // Store result
            vars.setValue("payment_processed", WorkflowVariableType.BOOLEAN, true);
            vars.setValue("transaction_id", WorkflowVariableType.STRING, result.getTransactionId());
            
            log.info("Payment processed: {}", result.getTransactionId());
            return result.getTransactionId();
        }
        
        private String createShipment(WorkflowVariables vars, String orderId) {
            String trackingNumber = vars.getString("tracking_number");
            
            if (trackingNumber != null) {
                // Already created - verify it exists
                if (shippingService.shipmentExists(trackingNumber)) {
                    log.info("Shipment already created: {}", trackingNumber);
                    return trackingNumber;
                } else {
                    log.warn("Shipment {} not found, recreating", trackingNumber);
                    trackingNumber = null;
                }
            }
            
            // Create shipment
            ShipmentRequest request = buildShipmentRequest(vars);
            Shipment shipment = shippingService.createShipment(request);
            trackingNumber = shipment.getTrackingNumber();
            vars.setValue("tracking_number", WorkflowVariableType.STRING, trackingNumber);
            
            log.info("Shipment created: {}", trackingNumber);
            return trackingNumber;
        }
        
        private void sendConfirmation(WorkflowVariables vars, String orderId) {
            Boolean confirmationSent = vars.getBoolean("confirmation_sent");
            if (Boolean.TRUE.equals(confirmationSent)) {
                log.info("Confirmation already sent for order {}", orderId);
                return;
            }
            
            // Send confirmation email
            notificationService.sendOrderConfirmation(
                vars.getString("customer_email"),
                orderId,
                vars.getString("tracking_number")
            );
            
            vars.setValue("confirmation_sent", WorkflowVariableType.BOOLEAN, true);
            log.info("Confirmation sent for order {}", orderId);
        }
        
        private void compensateInventory(WorkflowVariables vars) {
            String reservationId = vars.getString("inventory_reservation_id");
            if (reservationId != null) {
                try {
                    inventoryService.releaseReservation(reservationId);
                    vars.setValue("inventory_reservation_id", WorkflowVariableType.STRING, null);
                    log.info("Inventory reservation released: {}", reservationId);
                } catch (Exception e) {
                    log.error("Failed to release inventory: {}", e.getMessage());
                }
            }
        }
        
        private void compensatePayment(WorkflowVariables vars) {
            Boolean paymentProcessed = vars.getBoolean("payment_processed");
            if (Boolean.TRUE.equals(paymentProcessed)) {
                String transactionId = vars.getString("transaction_id");
                try {
                    paymentService.refund(transactionId);
                    vars.setValue("payment_processed", WorkflowVariableType.BOOLEAN, false);
                    log.info("Payment refunded: {}", transactionId);
                } catch (Exception e) {
                    log.error("Failed to refund payment: {}", e.getMessage());
                }
            }
        }
        
        private void compensateAll(WorkflowVariables vars) {
            compensatePayment(vars);
            compensateInventory(vars);
        }
    }
}
```

### Example 2: Document Processing Pipeline

```java
public class DocumentProcessingExample {
    
    /**
     * Idempotent multi-stage document processing
     */
    public static class ProcessDocumentStep implements InvokableTask {
        
        @Override
        public TaskResponse executeStep() {
            WorkflowVariables vars = context.getProcessVariables();
            String documentId = vars.getString("document_id");
            
            try {
                // Stage 1: Upload to storage (idempotent)
                String storageUrl = uploadToStorage(vars, documentId);
                
                // Stage 2: Extract text (idempotent)
                String extractedText = extractText(vars, documentId, storageUrl);
                
                // Stage 3: Analyze content (idempotent)
                AnalysisResult analysis = analyzeContent(vars, documentId, extractedText);
                
                // Stage 4: Index for search (idempotent)
                indexDocument(vars, documentId, extractedText, analysis);
                
                // Stage 5: Generate thumbnail (idempotent)
                String thumbnailUrl = generateThumbnail(vars, documentId, storageUrl);
                
                log.info("Document {} processed successfully", documentId);
                return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
                
            } catch (Exception e) {
                log.error("Document processing failed: {}", e.getMessage());
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    e.getMessage(),
                    "document_error_queue"
                );
            }
        }
        
        private String uploadToStorage(WorkflowVariables vars, String documentId) {
            String storageUrl = vars.getString("storage_url");
            
            if (storageUrl != null) {
                // Already uploaded - verify it exists
                if (storageService.fileExists(storageUrl)) {
                    log.info("Document already uploaded: {}", storageUrl);
                    return storageUrl;
                }
            }
            
            // Upload document
            byte[] content = getDocumentContent(vars);
            storageUrl = storageService.upload(documentId, content);
            vars.setValue("storage_url", WorkflowVariableType.STRING, storageUrl);
            
            log.info("Document uploaded: {}", storageUrl);
            return storageUrl;
        }
        
        private String extractText(WorkflowVariables vars, String documentId, String storageUrl) {
            String extractedText = vars.getString("extracted_text");
            
            if (extractedText != null) {
                log.info("Text already extracted for document {}", documentId);
                return extractedText;
            }
            
            // Extract text from document
            extractedText = ocrService.extractText(storageUrl);
            vars.setValue("extracted_text", WorkflowVariableType.STRING, extractedText);
            
            log.info("Text extracted from document {}", documentId);
            return extractedText;
        }
        
        private AnalysisResult analyzeContent(WorkflowVariables vars, String documentId, String text) {
            String analysisJson = vars.getString("analysis_result");
            
            if (analysisJson != null) {
                log.info("Content already analyzed for document {}", documentId);
                return parseAnalysisResult(analysisJson);
            }
            
            // Analyze content
            AnalysisResult analysis = analysisService.analyze(text);
            analysisJson = serializeAnalysisResult(analysis);
            vars.setValue("analysis_result", WorkflowVariableType.STRING, analysisJson);
            
            log.info("Content analyzed for document {}", documentId);
            return analysis;
        }
        
        private void indexDocument(WorkflowVariables vars, String documentId, 
                                   String text, AnalysisResult analysis) {
            Boolean indexed = vars.getBoolean("document_indexed");
            
            if (Boolean.TRUE.equals(indexed)) {
                log.info("Document {} already indexed", documentId);
                return;
            }
            
            // Index document (upsert - naturally idempotent)
            searchService.indexDocument(
                documentId,
                text,
                analysis.getKeywords(),
                analysis.getCategory()
            );
            
            vars.setValue("document_indexed", WorkflowVariableType.BOOLEAN, true);
            log.info("Document {} indexed", documentId);
        }
        
        private String generateThumbnail(WorkflowVariables vars, String documentId, String storageUrl) {
            String thumbnailUrl = vars.getString("thumbnail_url");
            
            if (thumbnailUrl != null) {
                // Already generated - verify it exists
                if (storageService.fileExists(thumbnailUrl)) {
                    log.info("Thumbnail already generated: {}", thumbnailUrl);
                    return thumbnailUrl;
                }
            }
            
            // Generate thumbnail
            byte[] thumbnail = thumbnailService.generate(storageUrl);
            thumbnailUrl = storageService.upload(documentId + "_thumb", thumbnail);
            vars.setValue("thumbnail_url", WorkflowVariableType.STRING, thumbnailUrl);
            
            log.info("Thumbnail generated: {}", thumbnailUrl);
            return thumbnailUrl;
        }
    }
}
```

### Example 3: Batch Data Processing

```java
public class BatchProcessingExample {
    
    /**
     * Idempotent batch processing with progress tracking
     */
    public static class ProcessBatchStep implements InvokableTask {
        
        private static final int BATCH_SIZE = 100;
        
        @Override
        public TaskResponse executeStep() {
            WorkflowVariables vars = context.getProcessVariables();
            
            // Get total records
            @SuppressWarnings("unchecked")
            List<DataRecord> allRecords = (List<DataRecord>) vars.getValue(
                "records_to_process",
                WorkflowVariableType.LIST_OF_OBJECT
            );
            
            if (allRecords == null || allRecords.isEmpty()) {
                log.info("No records to process");
                return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            }
            
            // Get progress
            Integer processedCount = vars.getInteger("processed_count");
            if (processedCount == null) processedCount = 0;
            
            // Get failed records
            @SuppressWarnings("unchecked")
            Set<String> failedRecordIds = (Set<String>) vars.getValue(
                "failed_record_ids",
                WorkflowVariableType.OBJECT
            );
            if (failedRecordIds == null) failedRecordIds = new HashSet<>();
            
            // Process next batch
            int startIndex = processedCount;
            int endIndex = Math.min(startIndex + BATCH_SIZE, allRecords.size());
            
            log.info("Processing records {} to {} of {}", 
                startIndex, endIndex, allRecords.size());
            
            for (int i = startIndex; i < endIndex; i++) {
                DataRecord record = allRecords.get(i);
                
                // Skip if already failed
                if (failedRecordIds.contains(record.getId())) {
                    log.debug("Skipping failed record: {}", record.getId());
                    continue;
                }
                
                // Check if already processed
                if (isRecordProcessed(record.getId())) {
                    log.debug("Record already processed: {}", record.getId());
                    continue;
                }
                
                try {
                    // Process record (with database constraint for idempotency)
                    processRecord(record);
                    markRecordProcessed(record.getId());
                    
                } catch (ValidationException e) {
                    // Invalid record - skip it
                    log.warn("Invalid record {}: {}", record.getId(), e.getMessage());
                    failedRecordIds.add(record.getId());
                    
                } catch (TransientException e) {
                    // Transient error - will retry in next batch
                    log.warn("Transient error for record {}: {}", record.getId(), e.getMessage());
                    // Don't mark as processed, don't mark as failed
                    // Will retry in next execution
                }
            }
            
            // Update progress
            processedCount = endIndex;
            vars.setValue("processed_count", WorkflowVariableType.INTEGER, processedCount);
            vars.setValue("failed_record_ids", WorkflowVariableType.OBJECT, failedRecordIds);
            
            if (processedCount >= allRecords.size()) {
                // All records processed
                log.info("Batch processing complete. Processed: {}, Failed: {}", 
                    processedCount, failedRecordIds.size());
                return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            } else {
                // More records to process
                log.info("Batch complete. Progress: {}/{}", processedCount, allRecords.size());
                return new TaskResponse(
                    StepResponseType.OK_PEND_EOR,  // Re-execute for next batch
                    "Processing next batch",
                    "batch_processing_queue"
                );
            }
        }
        
        private boolean isRecordProcessed(String recordId) {
            // Check database for processed record
            return processedRecordRepository.existsById(recordId);
        }
        
        private void processRecord(DataRecord record) {
            // Process and store with unique constraint
            ProcessedRecord processed = new ProcessedRecord(
                record.getId(),
                processData(record),
                System.currentTimeMillis()
            );
            
            try {
                processedRecordRepository.insert(processed);
            } catch (DuplicateKeyException e) {
                // Already processed - idempotent success
                log.debug("Record {} already processed", record.getId());
            }
        }
        
        private void markRecordProcessed(String recordId) {
            // Mark as processed (idempotent - uses upsert)
            processedRecordRepository.upsert(
                new ProcessedRecordMarker(recordId, System.currentTimeMillis())
            );
        }
    }
}
```

## Summary

### Idempotency Checklist

✅ **Design**
- [ ] Operations use SET instead of INCREMENT
- [ ] External services support idempotency keys
- [ ] Database constraints prevent duplicates
- [ ] State machines guard transitions

✅ **Implementation**
- [ ] Check execution flags before operations
- [ ] Store state in process variables
- [ ] Use idempotency keys for external calls
- [ ] Handle partial failures gracefully

✅ **Testing**
- [ ] Unit tests execute steps multiple times
- [ ] Integration tests simulate crash recovery
- [ ] Concurrent execution tests
- [ ] Property-based tests verify idempotency

✅ **Documentation**
- [ ] Idempotency guarantees documented
- [ ] Execution flags explained
- [ ] Recovery behavior described

### Quick Reference

| Pattern | When to Use | Complexity | Best For |
|---------|-------------|------------|----------|
| Check-Then-Execute | Most scenarios | Low | Process variables |
| Idempotency Keys | External APIs | Low | Stripe, AWS, etc. |
| Natural Idempotency | When possible | Low | SET operations |
| Database Constraints | Database ops | Medium | INSERT/UPDATE |
| Version-Based | Concurrent updates | Medium | Shared entities |
| State Machine Guards | State transitions | Medium | Status changes |
| Distributed Locks | Critical sections | High | Multi-instance |

### Related Documentation

- [Crash Recovery](./crash-recovery.md) - Why idempotency matters
- [Error Handling](./error-handling.md) - Handling idempotent retries
- [Process Variables](../concepts/process-variables.md) - Storing execution state
- [Testing Guide](../testing/README.md) - Testing idempotency

---

**Remember**: Idempotency is not optional in distributed systems—it's a fundamental requirement for reliability. Design for it from the start, test it explicitly, and document it clearly.