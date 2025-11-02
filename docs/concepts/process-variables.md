# Process Variables

Process variables are the primary mechanism for storing and sharing data throughout a workflow's lifecycle. They act as the workflow's memory, persisting data between steps, surviving crashes, and enabling communication between different execution paths.

## Table of Contents
1. [Overview](#overview)
2. [Variable Lifecycle](#variable-lifecycle)
3. [Working with Variables](#working-with-variables)
4. [Variable Scope](#variable-scope)
5. [Data Types and Serialization](#data-types-and-serialization)
6. [Best Practices](#best-practices)
7. [Advanced Patterns](#advanced-patterns)
8. [Common Pitfalls](#common-pitfalls)

## Overview

### What Are Process Variables?

Process variables are key-value pairs attached to a workflow instance (process). They:
- **Persist across steps**: Available to all steps in the workflow
- **Survive crashes**: Automatically saved and restored during recovery
- **Enable communication**: Share data between parallel execution paths
- **Support complex types**: Store primitives, objects, collections, etc.

### Key Characteristics

```java
// Process variables are:
✅ Persistent (saved to storage)
✅ Mutable (can be updated by any step)
✅ Shared (accessible across execution paths)
✅ Versioned (tracked through workflow history)
✅ Type-flexible (support various data types)
```

## Variable Lifecycle

### 1. Initialization

Variables can be set when starting a workflow:

```java
Map<String, Object> processVariables = new HashMap<>();
processVariables.put("orderId", "ORD-12345");
processVariables.put("customerId", "CUST-001");
processVariables.put("orderAmount", 299.99);

runtimeService.startCase(
    caseId,
    workflowJson,
    processVariables,  // Initial variables
    null
);
```

### 2. Reading During Execution

Steps can read variables through the WorkflowContext:

```java
public class ProcessOrderStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        // Read primitive values
        String orderId = context.getProcessVariable("orderId");
        Double amount = context.getProcessVariable("orderAmount");

        // Read complex objects
        Customer customer = context.getProcessVariable("customer");

        // Use the values
        System.out.println("Processing order " + orderId +
            " for customer " + customer.getName());
    }
}
```

### 3. Writing/Updating

Steps can set or update variables:

```java
public class EnrichOrderStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        String orderId = context.getProcessVariable("orderId");

        // Fetch additional data
        Order orderDetails = fetchOrderDetails(orderId);

        // Store enriched data
        context.setProcessVariable("orderDetails", orderDetails);
        context.setProcessVariable("lastUpdated", System.currentTimeMillis());

        // Update existing variable
        String status = context.getProcessVariable("status");
        context.setProcessVariable("status", "ENRICHED");
    }
}
```

### 4. Persistence

Variables are automatically persisted:
- **After each step completes**: State is saved to storage
- **During crash recovery**: Variables are restored from last checkpoint
- **Across execution paths**: All paths see the same variable state

### 5. Cleanup

Variables exist for the lifetime of the process. They are automatically cleaned up when:
- Process completes successfully
- Process is explicitly deleted
- Retention policies are applied (if configured)

## Working with Variables

### Basic Operations

#### Setting Variables

```java
// Set a single variable
context.setProcessVariable("key", value);

// Set multiple variables
Map<String, Object> vars = new HashMap<>();
vars.put("var1", "value1");
vars.put("var2", 123);
vars.put("var3", true);
context.setAllProcessVariables(vars);
```

#### Getting Variables

```java
// Get a specific variable
String value = context.getProcessVariable("key");

// Get with type safety
String stringValue = context.getProcessVariable("name");
Integer intValue = context.getProcessVariable("count");
Boolean boolValue = context.getProcessVariable("isActive");

// Get all variables
Map<String, Object> allVars = context.getAllProcessVariables();

// Check if variable exists
if (context.hasProcessVariable("optionalKey")) {
    Object value = context.getProcessVariable("optionalKey");
}
```

#### Removing Variables

```java
// Remove a specific variable
context.removeProcessVariable("tempKey");

// Remove multiple variables
context.removeProcessVariables(Arrays.asList("temp1", "temp2", "temp3"));
```

### Type-Safe Variable Access

Use generics for type-safe access:

```java
public class TypeSafeVariableStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        // Explicit casting
        String orderId = (String) context.getProcessVariable("orderId");
        Integer quantity = (Integer) context.getProcessVariable("quantity");

        // Or use helper methods if your context implementation provides them
        String customerId = context.getVariableAsString("customerId");
        int count = context.getVariableAsInt("count");
        boolean flag = context.getVariableAsBoolean("flag");

        // For complex objects
        Order order = (Order) context.getProcessVariable("order");
        List<Item> items = (List<Item>) context.getProcessVariable("items");
        Map<String, Object> metadata = (Map<String, Object>)
            context.getProcessVariable("metadata");
    }
}
```

### Handling Missing Variables

```java
public class SafeVariableAccessStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        // Check before accessing
        if (context.hasProcessVariable("optionalValue")) {
            String value = context.getProcessVariable("optionalValue");
            processValue(value);
        }

        // Provide default values
        Integer retryCount = context.getProcessVariable("retryCount");
        if (retryCount == null) {
            retryCount = 0;
        }

        // Or use a helper method
        String status = getVariableOrDefault(context, "status", "PENDING");
    }

    private <T> T getVariableOrDefault(WorkflowContext context,
                                       String key, T defaultValue) {
        T value = context.getProcessVariable(key);
        return value != null ? value : defaultValue;
    }
}
```

## Variable Scope

### Process-Level Variables

Variables are scoped at the process level, meaning:
- **All steps see the same variables**: Any step can read variables set by previous steps
- **All execution paths share variables**: Parallel paths can communicate via variables
- **Variables persist across the entire workflow lifecycle**

```java
// Step in execution path "main"
public class MainPathStep implements WorkflowStep {
    @Override
    public void execute(WorkflowContext context) throws Exception {
        context.setProcessVariable("sharedData", "available to all paths");
    }
}

// Step in execution path "parallel-1"
public class Parallel1Step implements WorkflowStep {
    @Override
    public void execute(WorkflowContext context) throws Exception {
        // Can access variables set by main path
        String data = context.getProcessVariable("sharedData");
        System.out.println("Parallel path sees: " + data);
    }
}
```

### Variable Isolation

Process variables are isolated per workflow instance:
- Each case has its own variable set
- Variables from one case cannot affect another
- No risk of data leakage between workflow instances

```java
// Case 1
runtimeService.startCase("CASE-001", workflow, Map.of("orderId", "ORD-1"), null);

// Case 2 - completely separate variables
runtimeService.startCase("CASE-002", workflow, Map.of("orderId", "ORD-2"), null);

// ORD-1 and ORD-2 never interfere with each other
```

## Data Types and Serialization

### Supported Types

Process variables support any serializable Java object:

```java
// Primitives and Wrappers
context.setProcessVariable("string", "Hello");
context.setProcessVariable("integer", 42);
context.setProcessVariable("long", 123L);
context.setProcessVariable("double", 3.14);
context.setProcessVariable("boolean", true);

// Collections
context.setProcessVariable("list", Arrays.asList("a", "b", "c"));
context.setProcessVariable("set", new HashSet<>(Arrays.asList(1, 2, 3)));
context.setProcessVariable("map", Map.of("key", "value"));

// Complex Objects
context.setProcessVariable("order", new Order("ORD-123", 299.99));
context.setProcessVariable("customer", new Customer("John", "john@example.com"));

// Nested Structures
Map<String, List<Order>> customerOrders = new HashMap<>();
customerOrders.put("CUST-001", Arrays.asList(order1, order2));
context.setProcessVariable("customerOrders", customerOrders);
```

### Serialization Requirements

For objects to be persisted correctly:

1. **Implement Serializable** (for Java serialization):
```java
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderId;
    private Double amount;
    private List<OrderItem> items;

    // Constructors, getters, setters
}
```

2. **Use JSON-compatible objects** (if using JSON persistence):
```java
// Plain POJOs work well
public class Customer {
    private String customerId;
    private String name;
    private String email;

    // Must have default constructor for JSON deserialization
    public Customer() {}

    public Customer(String customerId, String name, String email) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
    }

    // Getters and setters
}
```

3. **Avoid non-serializable objects**:
```java
// ❌ DON'T store these
context.setProcessVariable("connection", databaseConnection);  // Not serializable
context.setProcessVariable("thread", new Thread());            // Not serializable
context.setProcessVariable("stream", inputStream);             // Not serializable

// ✅ DO store these
context.setProcessVariable("connectionInfo", connectionString);
context.setProcessVariable("threadId", Thread.currentThread().getId());
context.setProcessVariable("streamData", inputStream.readAllBytes());
```

### Large Data Handling

For large data objects:

```java
public class LargeDataHandlingStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        // ❌ Don't store large binary data directly
        // byte[] largeFile = loadLargeFile(); // 500 MB
        // context.setProcessVariable("largeFile", largeFile);

        // ✅ Store reference instead
        String fileId = uploadToExternalStorage(largeFile);
        context.setProcessVariable("fileId", fileId);
        context.setProcessVariable("fileSize", largeFile.length);

        // Later steps retrieve by reference
        String retrievedFileId = context.getProcessVariable("fileId");
        byte[] retrievedFile = downloadFromExternalStorage(retrievedFileId);
    }
}
```

## Best Practices

### 1. Use Meaningful Names

```java
// ❌ Bad
context.setProcessVariable("d", data);
context.setProcessVariable("x", 123);
context.setProcessVariable("tmp", tempValue);

// ✅ Good
context.setProcessVariable("orderData", data);
context.setProcessVariable("retryCount", 123);
context.setProcessVariable("validationResult", tempValue);
```

### 2. Prefix Temporary Variables

```java
// Mark variables that are only needed temporarily
context.setProcessVariable("temp_apiResponse", response);
context.setProcessVariable("temp_validationErrors", errors);

// Clean up when done
context.removeProcessVariable("temp_apiResponse");
context.removeProcessVariable("temp_validationErrors");
```

### 3. Use Structured Data

```java
// ❌ Avoid flat structure for related data
context.setProcessVariable("customerName", "John");
context.setProcessVariable("customerEmail", "john@example.com");
context.setProcessVariable("customerPhone", "555-1234");

// ✅ Use objects for related data
Customer customer = new Customer("John", "john@example.com", "555-1234");
context.setProcessVariable("customer", customer);
```

### 4. Document Variable Usage

```java
/**
 * Process Variables:
 * - orderId (String): Unique order identifier
 * - orderData (Map): Raw order payload from API
 * - customer (Customer): Enriched customer information
 * - validationResult (Boolean): Order validation outcome
 * - paymentId (String): Payment transaction ID
 * - shipmentTrackingNumber (String): Shipping tracking number
 */
public class OrderProcessWorkflow {
    // Implementation
}
```

### 5. Validate Variable Types

```java
public class ValidatedVariableAccessStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        // Validate presence
        if (!context.hasProcessVariable("orderId")) {
            throw new IllegalStateException("Missing required variable: orderId");
        }

        // Validate type
        Object orderIdObj = context.getProcessVariable("orderId");
        if (!(orderIdObj instanceof String)) {
            throw new IllegalStateException(
                "Variable 'orderId' must be String, got: " +
                orderIdObj.getClass().getName());
        }

        String orderId = (String) orderIdObj;
    }
}
```

### 6. Avoid Variable Pollution

```java
// ❌ Don't set unnecessary variables
context.setProcessVariable("stepName", "ValidateOrder");  // Not needed
context.setProcessVariable("timestamp", System.currentTimeMillis());  // Maybe not needed
context.setProcessVariable("logMessage", "Processing...");  // Definitely not needed

// ✅ Only store business-relevant data
context.setProcessVariable("orderId", orderId);
context.setProcessVariable("validationStatus", "PASSED");
```

### 7. Use Immutable Objects When Possible

```java
// ✅ Immutable objects prevent accidental modification
public class OrderSummary {
    private final String orderId;
    private final double amount;
    private final List<String> items;

    public OrderSummary(String orderId, double amount, List<String> items) {
        this.orderId = orderId;
        this.amount = amount;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    // Only getters, no setters
}

// Set once
context.setProcessVariable("orderSummary",
    new OrderSummary("ORD-123", 299.99, itemsList));
```

## Advanced Patterns

### 1. Variable Versioning

Track changes to variables:

```java
public class VersionedVariableStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        // Get current version
        Integer version = context.getProcessVariable("orderVersion");
        if (version == null) {
            version = 0;
        }

        // Make changes
        Order order = context.getProcessVariable("order");
        order.setStatus("UPDATED");

        // Save with new version
        context.setProcessVariable("order", order);
        context.setProcessVariable("orderVersion", version + 1);
        context.setProcessVariable("lastModifiedAt", System.currentTimeMillis());
    }
}
```

### 2. Variable Change Listeners

Implement a pattern to track variable changes:

```java
public class ChangeTrackingStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        // Get change log
        List<Map<String, Object>> changeLog = context.getProcessVariable("changeLog");
        if (changeLog == null) {
            changeLog = new ArrayList<>();
        }

        // Record change
        Map<String, Object> change = new HashMap<>();
        change.put("timestamp", System.currentTimeMillis());
        change.put("stepId", context.getCurrentStepId());
        change.put("variable", "orderStatus");
        change.put("oldValue", "PENDING");
        change.put("newValue", "PROCESSING");

        changeLog.add(change);
        context.setProcessVariable("changeLog", changeLog);

        // Make the actual change
        context.setProcessVariable("orderStatus", "PROCESSING");
    }
}
```

### 3. Conditional Variable Setting

Set variables based on conditions:

```java
public class ConditionalVariableStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        Double orderAmount = context.getProcessVariable("orderAmount");

        // Set variables based on conditions
        if (orderAmount > 1000) {
            context.setProcessVariable("approvalRequired", true);
            context.setProcessVariable("approvalLevel", "MANAGER");
            context.setProcessVariable("priority", "HIGH");
        } else if (orderAmount > 500) {
            context.setProcessVariable("approvalRequired", true);
            context.setProcessVariable("approvalLevel", "SUPERVISOR");
            context.setProcessVariable("priority", "MEDIUM");
        } else {
            context.setProcessVariable("approvalRequired", false);
            context.setProcessVariable("priority", "LOW");
        }
    }
}
```

### 4. Variable Aggregation

Aggregate data from parallel execution paths:

```java
public class AggregationStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        // Collect results from parallel paths
        List<String> results = new ArrayList<>();

        // Path 1 result
        String result1 = context.getProcessVariable("path1Result");
        if (result1 != null) results.add(result1);

        // Path 2 result
        String result2 = context.getProcessVariable("path2Result");
        if (result2 != null) results.add(result2);

        // Path 3 result
        String result3 = context.getProcessVariable("path3Result");
        if (result3 != null) results.add(result3);

        // Aggregate
        context.setProcessVariable("aggregatedResults", results);
        context.setProcessVariable("totalResults", results.size());

        // Compute summary
        boolean allSuccessful = results.stream()
            .allMatch(r -> r.equals("SUCCESS"));
        context.setProcessVariable("allPathsSuccessful", allSuccessful);
    }
}
```

### 5. Variable Encryption

Encrypt sensitive variables:

```java
public class EncryptedVariableStep implements WorkflowStep {

    private final EncryptionService encryption;

    @Override
    public void execute(WorkflowContext context) throws Exception {
        // Encrypt sensitive data before storing
        String creditCard = getCreditCardNumber();
        String encryptedCard = encryption.encrypt(creditCard);
        context.setProcessVariable("creditCard_encrypted", encryptedCard);

        // Later, decrypt when needed
        String encryptedValue = context.getProcessVariable("creditCard_encrypted");
        String decryptedCard = encryption.decrypt(encryptedValue);
        processPayment(decryptedCard);
    }
}
```

## Common Pitfalls

### 1. Mutable Object Modification

```java
// ❌ Problem: Modifying object without re-setting variable
List<String> items = context.getProcessVariable("items");
items.add("newItem");  // Change not persisted!

// ✅ Solution: Re-set the variable after modification
List<String> items = context.getProcessVariable("items");
items.add("newItem");
context.setProcessVariable("items", items);  // Persist the change
```

### 2. Type Casting Errors

```java
// ❌ Problem: Assuming type without checking
Integer count = (Integer) context.getProcessVariable("count");
// Throws ClassCastException if "count" is actually a Long or String

// ✅ Solution: Validate before casting
Object countObj = context.getProcessVariable("count");
Integer count;
if (countObj instanceof Number) {
    count = ((Number) countObj).intValue();
} else {
    throw new IllegalStateException("Invalid type for 'count'");
}
```

### 3. Null Pointer Exceptions

```java
// ❌ Problem: Not checking for null
String status = context.getProcessVariable("status");
if (status.equals("COMPLETE")) {  // NPE if status is null
    // ...
}

// ✅ Solution: Always check for null
String status = context.getProcessVariable("status");
if (status != null && status.equals("COMPLETE")) {
    // ...
}
```

### 4. Overwriting Variables Accidentally

```java
// ❌ Problem: Different steps using same variable name
public class Step1 implements WorkflowStep {
    public void execute(WorkflowContext context) {
        context.setProcessVariable("result", "Step1 result");
    }
}

public class Step2 implements WorkflowStep {
    public void execute(WorkflowContext context) {
        context.setProcessVariable("result", "Step2 result");  // Overwrites!
    }
}

// ✅ Solution: Use unique, descriptive names
public class Step1 implements WorkflowStep {
    public void execute(WorkflowContext context) {
        context.setProcessVariable("validationResult", "Step1 result");
    }
}

public class Step2 implements WorkflowStep {
    public void execute(WorkflowContext context) {
        context.setProcessVariable("enrichmentResult", "Step2 result");
    }
}
```

### 5. Storing Non-Serializable Objects

```java
// ❌ Problem: Storing non-serializable objects
context.setProcessVariable("dbConnection", connection);
// Fails during persistence!

// ✅ Solution: Store connection info, not the connection itself
context.setProcessVariable("dbConnectionString", connectionString);
context.setProcessVariable("dbUsername", username);
```

## Related Documentation

- [Execution Paths](./execution-paths.md) - How variables are shared across paths
- [Error Handling](../patterns/error-handling.md) - Using variables for error tracking
- [Idempotency](../patterns/idempotency.md) - Variables for idempotent operations
- [Crash Recovery](./crash-recovery.md) - How variables survive crashes
- [Custom DAO](../persistence/custom-dao.md) - Implementing variable persistence

## Summary

Process variables are the backbone of workflow state management:
- Use them to store all business data needed by your workflow
- Ensure objects are serializable
- Follow naming conventions and best practices
- Clean up temporary variables when done
- Always validate variable presence and types
- Be mindful of data size and serialization costs

With proper variable management, your workflows will be robust, maintainable, and crash-proof.
