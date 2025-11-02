# Getting Started with Simple Workflow

This guide will help you get up and running with Simple Workflow in under 30 minutes. By the end, you'll have created your first workflow, understood the core concepts, and know where to go next.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Installation](#installation)
3. [Core Concepts](#core-concepts)
4. [Your First Workflow](#your-first-workflow)
5. [Understanding the Components](#understanding-the-components)
6. [Workflow Definition](#workflow-definition)
7. [Running Your Workflow](#running-your-workflow)
8. [Monitoring and Debugging](#monitoring-and-debugging)
9. [Next Steps](#next-steps)

## Prerequisites

Before you begin, ensure you have:
- **Java 17+** installed
- **Maven 3.6+** or Gradle
- Basic understanding of Java and JSON
- An IDE (IntelliJ IDEA, Eclipse, or VS Code)

## Installation

### Maven
Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.anode</groupId>
    <artifactId>workflow</artifactId>
    <version>0.0.1</version>
</dependency>
```

### Gradle
Add to your `build.gradle`:

```gradle
implementation 'com.anode:workflow:0.0.1'
```

## Core Concepts

Before diving into code, understand these key concepts:

### 1. Workflow Definition
A JSON document that defines the structure of your workflow:
- **Steps**: Individual units of work
- **Routes**: Decision points that determine the flow
- **Execution Paths**: Sequential or parallel execution tracks

### 2. Components
Java classes you implement to execute business logic:
- **WorkflowStep**: Executes a unit of work
- **WorkflowRoute**: Makes routing decisions
- **EventHandler**: Responds to workflow events

### 3. Services
The workflow engine provides three main services:
- **RuntimeService**: Starts and manages workflow instances
- **WorkManagementService**: Handles work baskets and assignments
- **SlaManagementService**: Tracks milestones and SLAs

### 4. Persistence
Where workflow state is stored:
- **FileDao**: Simple file-based storage (great for development)
- **Custom DAO**: Integrate with your database (PostgreSQL, MongoDB, etc.)

## Your First Workflow

Let's build a simple order processing workflow that:
1. Validates the order
2. Checks inventory
3. Processes payment
4. Ships the order

### Step 1: Initialize the Workflow Service

Create a configuration class:

```java
import com.anode.workflow.service.WorkflowService;
import com.anode.workflow.service.RuntimeService;
import com.anode.workflow.storage.file.FileDao;

public class WorkflowConfig {

    public static RuntimeService initializeWorkflow() {
        // Initialize the workflow service
        // Parameters: poolSize, processorTimeout, executionPathDelimiter
        WorkflowService.init(10, 30000, "-");

        // Create a file-based DAO for persistence
        CommonService dao = new FileDao("./workflow-data");

        // Create your component factory
        WorkflowComponantFactory factory = new OrderComponentFactory();

        // Create your event handler
        EventHandler handler = new OrderEventHandler();

        // Get the runtime service
        return WorkflowService.instance()
            .getRunTimeService(dao, factory, handler, null);
    }
}
```

### Step 2: Implement Your Workflow Steps

Create step implementations for each business operation:

```java
import com.anode.workflow.service.WorkflowStep;
import com.anode.workflow.service.WorkflowContext;

public class ValidateOrderStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        // Get order data from process variables
        String orderId = context.getProcessVariable("orderId");
        Map<String, Object> orderData = context.getProcessVariable("orderData");

        // Perform validation
        if (orderId == null || orderData == null) {
            throw new IllegalArgumentException("Invalid order data");
        }

        // Validate order details
        boolean isValid = validateOrder(orderData);

        // Store result in process variables
        context.setProcessVariable("orderValid", isValid);

        System.out.println("Order " + orderId + " validated: " + isValid);
    }

    private boolean validateOrder(Map<String, Object> orderData) {
        // Your validation logic here
        return orderData.containsKey("customerId")
            && orderData.containsKey("items");
    }
}

public class CheckInventoryStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        String orderId = context.getProcessVariable("orderId");
        Map<String, Object> orderData = context.getProcessVariable("orderData");

        // Check inventory availability
        boolean inStock = checkInventory(orderData);

        context.setProcessVariable("inStock", inStock);

        System.out.println("Inventory check for order " + orderId + ": " + inStock);
    }

    private boolean checkInventory(Map<String, Object> orderData) {
        // Your inventory check logic
        return true; // Simplified
    }
}

public class ProcessPaymentStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        String orderId = context.getProcessVariable("orderId");

        // Process payment
        boolean paymentSuccess = processPayment(context);

        context.setProcessVariable("paymentProcessed", paymentSuccess);

        System.out.println("Payment for order " + orderId + ": " +
            (paymentSuccess ? "SUCCESS" : "FAILED"));
    }

    private boolean processPayment(WorkflowContext context) {
        // Your payment processing logic
        return true; // Simplified
    }
}

public class ShipOrderStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        String orderId = context.getProcessVariable("orderId");

        // Ship the order
        String trackingNumber = shipOrder(orderId);

        context.setProcessVariable("trackingNumber", trackingNumber);

        System.out.println("Order " + orderId + " shipped. Tracking: " + trackingNumber);
    }

    private String shipOrder(String orderId) {
        // Your shipping logic
        return "TRACK-" + System.currentTimeMillis();
    }
}
```

### Step 3: Create a Component Factory

The factory creates instances of your workflow components:

```java
import com.anode.workflow.service.WorkflowComponantFactory;
import com.anode.workflow.service.WorkflowStep;
import com.anode.workflow.service.WorkflowRoute;

public class OrderComponentFactory implements WorkflowComponantFactory {

    @Override
    public WorkflowStep getStepInstance(String stepType) {
        switch (stepType) {
            case "validateOrder":
                return new ValidateOrderStep();
            case "checkInventory":
                return new CheckInventoryStep();
            case "processPayment":
                return new ProcessPaymentStep();
            case "shipOrder":
                return new ShipOrderStep();
            default:
                throw new IllegalArgumentException("Unknown step type: " + stepType);
        }
    }

    @Override
    public WorkflowRoute getRouteInstance(String routeType) {
        // We'll add routes later if needed
        return null;
    }
}
```

### Step 4: Create an Event Handler

Handle workflow lifecycle events:

```java
import com.anode.workflow.service.EventHandler;
import com.anode.workflow.entities.ProcessEntity;
import com.anode.workflow.entities.StepEntity;

public class OrderEventHandler implements EventHandler {

    @Override
    public void onProcessStart(ProcessEntity process) {
        System.out.println("Workflow started: " + process.getProcessId());
    }

    @Override
    public void onProcessComplete(ProcessEntity process) {
        System.out.println("Workflow completed: " + process.getProcessId());
    }

    @Override
    public void onProcessError(ProcessEntity process, Exception error) {
        System.err.println("Workflow error in " + process.getProcessId() +
            ": " + error.getMessage());
    }

    @Override
    public void onStepStart(StepEntity step) {
        System.out.println("Step started: " + step.getStepId() +
            " (" + step.getStepType() + ")");
    }

    @Override
    public void onStepComplete(StepEntity step) {
        System.out.println("Step completed: " + step.getStepId());
    }

    @Override
    public void onStepError(StepEntity step, Exception error) {
        System.err.println("Step error in " + step.getStepId() +
            ": " + error.getMessage());
    }
}
```

## Workflow Definition

Create a JSON file that defines your workflow structure. Save this as `order-process.json`:

```json
{
  "processDefinitionId": "order-process",
  "processDefinitionVersion": "1.0",
  "processDefinitionName": "Order Processing Workflow",
  "description": "Handles order validation, payment, and shipping",

  "steps": [
    {
      "stepId": "validate",
      "stepName": "Validate Order",
      "stepType": "validateOrder",
      "executionPath": "main",
      "order": 1
    },
    {
      "stepId": "inventory",
      "stepName": "Check Inventory",
      "stepType": "checkInventory",
      "executionPath": "main",
      "order": 2
    },
    {
      "stepId": "payment",
      "stepName": "Process Payment",
      "stepType": "processPayment",
      "executionPath": "main",
      "order": 3
    },
    {
      "stepId": "ship",
      "stepName": "Ship Order",
      "stepType": "shipOrder",
      "executionPath": "main",
      "order": 4
    }
  ],

  "routes": [],

  "milestones": [
    {
      "milestoneId": "order-received",
      "milestoneName": "Order Received",
      "durationMinutes": 5
    },
    {
      "milestoneId": "order-shipped",
      "milestoneName": "Order Shipped",
      "durationMinutes": 120
    }
  ]
}
```

### Understanding the Definition

- **processDefinitionId**: Unique identifier for this workflow type
- **steps**: Array of workflow steps to execute
  - **stepId**: Unique identifier within this workflow
  - **stepType**: Maps to your component factory
  - **executionPath**: Groups steps for parallel execution (all "main" = sequential)
  - **order**: Execution order within the path
- **routes**: Decision points (empty for now)
- **milestones**: SLA tracking points

## Running Your Workflow

Now let's put it all together:

```java
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class OrderProcessingApp {

    public static void main(String[] args) throws Exception {
        // 1. Initialize the workflow service
        RuntimeService runtimeService = WorkflowConfig.initializeWorkflow();

        // 2. Load the workflow definition
        String workflowJson = new String(
            Files.readAllBytes(Paths.get("order-process.json"))
        );

        // 3. Prepare process variables (input data)
        Map<String, Object> processVariables = new HashMap<>();
        processVariables.put("orderId", "ORD-12345");

        Map<String, Object> orderData = new HashMap<>();
        orderData.put("customerId", "CUST-001");
        orderData.put("items", List.of(
            Map.of("sku", "ITEM-001", "quantity", 2),
            Map.of("sku", "ITEM-002", "quantity", 1)
        ));
        orderData.put("totalAmount", 299.99);

        processVariables.put("orderData", orderData);

        // 4. Start the workflow
        String caseId = "CASE-" + System.currentTimeMillis();
        runtimeService.startCase(
            caseId,              // Unique case ID
            workflowJson,        // Workflow definition
            processVariables,    // Initial variables
            null                 // SLA config (optional)
        );

        System.out.println("Workflow started with case ID: " + caseId);

        // 5. The workflow now runs asynchronously
        // Steps will be executed by the workflow engine

        // 6. You can query the status
        Thread.sleep(5000); // Wait a bit for processing

        ProcessEntity process = runtimeService.getProcess(caseId);
        System.out.println("Process status: " + process.getStatus());
    }
}
```

### What Happens When You Run This?

1. **Initialization**: The workflow service starts with a thread pool
2. **Persistence**: FileDao creates `./workflow-data/` directory
3. **Case Creation**: A new workflow instance is created with ID `CASE-xxx`
4. **Step Execution**: Steps execute in order:
   - ValidateOrderStep executes
   - CheckInventoryStep executes
   - ProcessPaymentStep executes
   - ShipOrderStep executes
5. **Events**: Your EventHandler receives callbacks for each lifecycle event
6. **Persistence**: State is saved after each step (crash recovery)
7. **Completion**: Workflow completes, final state persisted

### Expected Output

```
Workflow started: CASE-1234567890
Step started: validate (validateOrder)
Order ORD-12345 validated: true
Step completed: validate
Step started: inventory (checkInventory)
Inventory check for order ORD-12345: true
Step completed: inventory
Step started: payment (processPayment)
Payment for order ORD-12345: SUCCESS
Step completed: payment
Step started: ship (shipOrder)
Order ORD-12345 shipped. Tracking: TRACK-1234567890
Step completed: ship
Workflow completed: CASE-1234567890
Process status: COMPLETED
```

## Monitoring and Debugging

### Check Workflow Status

```java
// Get the process entity
ProcessEntity process = runtimeService.getProcess(caseId);

System.out.println("Status: " + process.getStatus());
System.out.println("Current step: " + process.getCurrentStepId());
System.out.println("Progress: " + process.getCompletedSteps() + "/" + process.getTotalSteps());

// Get process variables
Map<String, Object> variables = process.getProcessVariables();
System.out.println("Tracking number: " + variables.get("trackingNumber"));
```

### View Step History

```java
List<StepEntity> steps = runtimeService.getStepHistory(caseId);

for (StepEntity step : steps) {
    System.out.println(step.getStepId() + ": " + step.getStatus() +
        " (started: " + step.getStartTime() +
        ", completed: " + step.getEndTime() + ")");
}
```

### Handle Errors

Errors are automatically caught and persisted. Failed steps can be retried:

```java
try {
    runtimeService.startCase(caseId, workflowJson, variables, null);
} catch (Exception e) {
    System.err.println("Workflow failed: " + e.getMessage());

    // Retrieve the failed process
    ProcessEntity process = runtimeService.getProcess(caseId);

    if (process.getStatus() == ProcessStatus.FAILED) {
        // Fix the issue, then retry
        runtimeService.retryFailedStep(caseId);
    }
}
```

### File-Based Persistence Structure

When using FileDao, the workflow data is stored in:

```
./workflow-data/
├── processes/
│   └── CASE-1234567890.json       # Process state
├── steps/
│   ├── CASE-1234567890-validate.json
│   ├── CASE-1234567890-inventory.json
│   ├── CASE-1234567890-payment.json
│   └── CASE-1234567890-ship.json
└── variables/
    └── CASE-1234567890.json       # Process variables
```

Each file contains the complete state, enabling crash recovery.

## Next Steps

Congratulations! You've created your first workflow. Here's what to explore next:

### 1. Add Parallel Execution
Learn how to run steps in parallel for better performance:
```json
{
  "steps": [
    {
      "stepId": "notify-customer",
      "executionPath": "notifications",
      "order": 1
    },
    {
      "stepId": "update-inventory",
      "executionPath": "inventory-main",
      "order": 1
    }
  ]
}
```
Read more: [Execution Paths](./concepts/execution-paths.md)

### 2. Add Conditional Routing
Use routes to create dynamic workflows:
```json
{
  "routes": [
    {
      "routeId": "check-payment",
      "routeType": "paymentDecision",
      "afterStepId": "payment"
    }
  ]
}
```
Read more: [Routing and Decision Points](./concepts/routing.md)

### 3. Implement Human Tasks
Use work baskets for manual steps:
```java
WorkManagementService wms = WorkflowService.instance()
    .getWorkManagementService(dao);

// Assign work to a basket
wms.createWorkItem(caseId, "approve-order", "managers");
```
Read more: [Work Baskets](./concepts/work-baskets.md)

### 4. Track SLAs
Monitor milestone compliance:
```java
SlaManagementService sms = WorkflowService.instance()
    .getSlaManagementService(dao);

List<Milestone> milestones = sms.getMilestones(caseId);
for (Milestone m : milestones) {
    System.out.println(m.getName() + ": " +
        (m.isBreached() ? "BREACHED" : "OK"));
}
```
Read more: [SLA Management](./concepts/sla-management.md)

### 5. Implement Crash Recovery
Test what happens when your application crashes:
```java
// Workflow crashes mid-execution
// On restart:
RuntimeService rts = WorkflowConfig.initializeWorkflow();
List<ProcessEntity> incomplete = rts.recoverIncompleteProcesses();

for (ProcessEntity p : incomplete) {
    System.out.println("Recovered: " + p.getProcessId());
    // Workflow automatically continues from last checkpoint
}
```
Read more: [Crash Recovery](./concepts/crash-recovery.md)

### 6. Use Database Persistence
Switch from FileDao to a database:
```java
CommonService dao = new PostgresDao(dataSource);
// Or implement your own CustomDao
```
Read more: [Custom DAO Implementation](./persistence/custom-dao.md)

### 7. Advanced Patterns
- [Error Handling Patterns](./patterns/error-handling.md)
- [Idempotency](./patterns/idempotency.md)
- [Saga Pattern Implementation](./patterns/saga.md)
- [Compensation Handling](./patterns/compensation.md)

## Common Issues

### Issue: Steps Not Executing
**Solution**: Check that your factory returns non-null step instances and stepType matches.

### Issue: Process Variables Not Available
**Solution**: Ensure variables are set before the step that needs them. Use `context.setProcessVariable()`.

### Issue: Workflow Doesn't Start
**Solution**: Validate your JSON workflow definition. Check for syntax errors and required fields.

### Issue: "No persistence directory" Error
**Solution**: Ensure the directory specified in FileDao exists or can be created.

## Resources

- [Full Documentation](./README.md)
- [API Reference](./api/README.md)
- [Examples Repository](../examples/)
- [Architecture Overview](./architecture/README.md)
- [FAQ](./faq.md)

## Getting Help

- **Questions**: Open a discussion on GitHub
- **Bugs**: Report issues on GitHub Issues
- **Feature Requests**: Submit via GitHub Issues with `enhancement` label

---

**Ready to build more complex workflows?** Check out the [concepts documentation](./concepts/README.md) to understand advanced features like parallel execution, crash recovery, and SLA management.
