# Your First Workflow - Step-by-Step Tutorial

This hands-on tutorial will guide you through creating your first complete workflow with Simple Workflow engine. By the end, you'll have a working order validation workflow running on your machine.

## What You'll Build

A simple order validation workflow that:
1. Validates order data
2. Checks customer credit
3. Approves or rejects the order

**Estimated time:** 20 minutes

## Prerequisites

- Java 17+ installed
- Maven 3.6+ installed
- Basic Java knowledge
- A text editor or IDE

## Step 1: Create Your Project

### Create Maven Project

```bash
mvn archetype:generate \
  -DgroupId=com.example.workflow \
  -DartifactId=my-first-workflow \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DarchetypeVersion=1.4 \
  -DinteractiveMode=false

cd my-first-workflow
```

### Add Workflow Dependency

Edit `pom.xml` and add the Simple Workflow dependency:

```xml
<dependencies>
    <!-- Simple Workflow -->
    <dependency>
        <groupId>com.anode</groupId>
        <artifactId>workflow</artifactId>
        <version>0.0.1</version>
    </dependency>

    <!-- Logging -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-simple</artifactId>
        <version>2.0.9</version>
    </dependency>
</dependencies>
```

### Build the Project

```bash
mvn clean package
```

## Step 2: Create Your First Step

Create `src/main/java/com/example/workflow/steps/ValidateOrderStep.java`:

```java
package com.example.workflow.steps;

import com.anode.workflow.service.WorkflowStep;
import com.anode.workflow.service.WorkflowContext;
import java.util.Map;

public class ValidateOrderStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        // Get order data from process variables
        String orderId = context.getProcessVariable("orderId");
        Map<String, Object> orderData = context.getProcessVariable("orderData");

        System.out.println("Validating order: " + orderId);

        // Simple validation: check if required fields exist
        boolean isValid = orderData != null
            && orderData.containsKey("customerId")
            && orderData.containsKey("amount")
            && orderData.containsKey("items");

        // Store validation result
        context.setProcessVariable("orderValid", isValid);

        if (isValid) {
            System.out.println("✓ Order " + orderId + " is valid");
        } else {
            System.out.println("✗ Order " + orderId + " is invalid");
            throw new IllegalArgumentException("Order validation failed");
        }
    }
}
```

**What's happening here:**
- We implement the `WorkflowStep` interface
- The `execute()` method contains our business logic
- We read data from `context.getProcessVariable()`
- We write results back with `context.setProcessVariable()`
- We throw an exception if validation fails

## Step 3: Create the Second Step

Create `src/main/java/com/example/workflow/steps/CheckCreditStep.java`:

```java
package com.example.workflow.steps;

import com.anode.workflow.service.WorkflowStep;
import com.anode.workflow.service.WorkflowContext;

public class CheckCreditStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        String customerId = context.getProcessVariable("customerId");
        Double amount = context.getProcessVariable("orderAmount");

        System.out.println("Checking credit for customer: " + customerId);

        // Simple credit check (in real world, this would call a credit service)
        boolean creditApproved = amount < 1000.0;  // Auto-approve orders under $1000

        context.setProcessVariable("creditApproved", creditApproved);

        if (creditApproved) {
            System.out.println("✓ Credit approved for $" + amount);
        } else {
            System.out.println("✗ Credit check failed for $" + amount);
        }
    }
}
```

## Step 4: Create the Decision Step

Create `src/main/java/com/example/workflow/steps/ApproveOrderStep.java`:

```java
package com.example.workflow.steps;

import com.anode.workflow.service.WorkflowStep;
import com.anode.workflow.service.WorkflowContext;

public class ApproveOrderStep implements WorkflowStep {

    @Override
    public void execute(WorkflowContext context) throws Exception {
        Boolean creditApproved = context.getProcessVariable("creditApproved");
        String orderId = context.getProcessVariable("orderId");

        if (Boolean.TRUE.equals(creditApproved)) {
            context.setProcessVariable("orderStatus", "APPROVED");
            System.out.println("✓ Order " + orderId + " APPROVED");
        } else {
            context.setProcessVariable("orderStatus", "REJECTED");
            System.out.println("✗ Order " + orderId + " REJECTED");
        }
    }
}
```

## Step 5: Create the Component Factory

Create `src/main/java/com/example/workflow/MyComponentFactory.java`:

```java
package com.example.workflow;

import com.anode.workflow.service.WorkflowComponantFactory;
import com.anode.workflow.service.WorkflowStep;
import com.anode.workflow.service.WorkflowRoute;
import com.example.workflow.steps.*;

public class MyComponentFactory implements WorkflowComponantFactory {

    @Override
    public WorkflowStep getStepInstance(String stepType) {
        switch (stepType) {
            case "validateOrder":
                return new ValidateOrderStep();
            case "checkCredit":
                return new CheckCreditStep();
            case "approveOrder":
                return new ApproveOrderStep();
            default:
                System.err.println("Unknown step type: " + stepType);
                return null;
        }
    }

    @Override
    public WorkflowRoute getRouteInstance(String routeType) {
        // No routes in this simple example
        return null;
    }
}
```

**Important:** The `stepType` strings here must match the `stepType` values in your workflow definition JSON.

## Step 6: Create the Event Handler

Create `src/main/java/com/example/workflow/MyEventHandler.java`:

```java
package com.example.workflow;

import com.anode.workflow.service.EventHandler;
import com.anode.workflow.entities.ProcessEntity;
import com.anode.workflow.entities.StepEntity;

public class MyEventHandler implements EventHandler {

    @Override
    public void onProcessStart(ProcessEntity process) {
        System.out.println("\n═══ Workflow Started ═══");
        System.out.println("Process ID: " + process.getProcessId());
    }

    @Override
    public void onProcessComplete(ProcessEntity process) {
        System.out.println("\n═══ Workflow Completed ═══");
        System.out.println("Process ID: " + process.getProcessId());
        System.out.println("Status: " + process.getStatus());
    }

    @Override
    public void onProcessError(ProcessEntity process, Exception error) {
        System.err.println("\n═══ Workflow Error ═══");
        System.err.println("Process ID: " + process.getProcessId());
        System.err.println("Error: " + error.getMessage());
    }

    @Override
    public void onStepStart(StepEntity step) {
        System.out.println("\n→ Step: " + step.getStepId() + " (" + step.getStepType() + ")");
    }

    @Override
    public void onStepComplete(StepEntity step) {
        System.out.println("✓ Step completed: " + step.getStepId());
    }

    @Override
    public void onStepError(StepEntity step, Exception error) {
        System.err.println("✗ Step failed: " + step.getStepId());
        System.err.println("  Error: " + error.getMessage());
    }
}
```

## Step 7: Create the Workflow Definition

Create `src/main/resources/order-validation-workflow.json`:

```json
{
  "processDefinitionId": "order-validation",
  "processDefinitionVersion": "1.0.0",
  "processDefinitionName": "Order Validation Workflow",
  "description": "Validates orders and checks customer credit",

  "steps": [
    {
      "stepId": "validate",
      "stepName": "Validate Order",
      "stepType": "validateOrder",
      "executionPath": "main",
      "order": 1
    },
    {
      "stepId": "credit-check",
      "stepName": "Check Customer Credit",
      "stepType": "checkCredit",
      "executionPath": "main",
      "order": 2
    },
    {
      "stepId": "approve",
      "stepName": "Approve or Reject Order",
      "stepType": "approveOrder",
      "executionPath": "main",
      "order": 3
    }
  ]
}
```

**Key fields explained:**
- `processDefinitionId`: Unique identifier for this workflow type
- `steps`: Array of workflow steps
- `stepId`: Unique ID for each step
- `stepType`: Maps to your ComponentFactory
- `executionPath`: All "main" means sequential execution
- `order`: Execution order within the path

## Step 8: Create the Main Application

Create `src/main/java/com/example/workflow/App.java`:

```java
package com.example.workflow;

import com.anode.workflow.service.WorkflowService;
import com.anode.workflow.service.RuntimeService;
import com.anode.workflow.storage.CommonService;
import com.anode.workflow.storage.file.FileDao;
import com.anode.workflow.entities.ProcessEntity;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class App {
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   My First Workflow - Tutorial Demo   ║");
        System.out.println("╚════════════════════════════════════════╝\n");

        // Step 1: Initialize the workflow service
        System.out.println("Initializing workflow service...");
        WorkflowService.init(5, 30000, "-");

        // Step 2: Set up file-based persistence
        CommonService dao = new FileDao("./workflow-data");

        // Step 3: Create factory and event handler
        MyComponentFactory factory = new MyComponentFactory();
        MyEventHandler eventHandler = new MyEventHandler();

        // Step 4: Get runtime service
        RuntimeService runtimeService = WorkflowService.instance()
            .getRunTimeService(dao, factory, eventHandler, null);

        // Step 5: Load workflow definition
        InputStream is = App.class.getClassLoader()
            .getResourceAsStream("order-validation-workflow.json");
        String workflowJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        // Step 6: Create test order data
        String caseId = "CASE-" + System.currentTimeMillis();
        String orderId = "ORD-12345";

        Map<String, Object> processVariables = new HashMap<>();
        processVariables.put("orderId", orderId);
        processVariables.put("customerId", "CUST-001");

        Map<String, Object> orderData = new HashMap<>();
        orderData.put("customerId", "CUST-001");
        orderData.put("amount", 799.99);  // Under $1000, should be approved
        orderData.put("items", List.of("Item1", "Item2"));

        processVariables.put("orderData", orderData);
        processVariables.put("orderAmount", 799.99);

        // Step 7: Start the workflow
        System.out.println("Starting workflow for order: " + orderId);
        runtimeService.startCase(caseId, workflowJson, processVariables, null);

        // Step 8: Wait for completion
        Thread.sleep(3000);

        // Step 9: Check results
        ProcessEntity process = runtimeService.getProcess(caseId);
        Map<String, Object> results = process.getProcessVariables();

        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║           Final Results                ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println("Order ID: " + results.get("orderId"));
        System.out.println("Order Valid: " + results.get("orderValid"));
        System.out.println("Credit Approved: " + results.get("creditApproved"));
        System.out.println("Final Status: " + results.get("orderStatus"));
        System.out.println("Workflow Status: " + process.getStatus());

        System.out.println("\n✓ Tutorial completed successfully!");
    }
}
```

## Step 9: Run Your Workflow

### Build the project
```bash
mvn clean package
```

### Run the application
```bash
mvn exec:java -Dexec.mainClass="com.example.workflow.App"
```

### Expected Output

```
╔════════════════════════════════════════╗
║   My First Workflow - Tutorial Demo   ║
╚════════════════════════════════════════╝

Initializing workflow service...
Starting workflow for order: ORD-12345

═══ Workflow Started ═══
Process ID: CASE-1234567890

→ Step: validate (validateOrder)
Validating order: ORD-12345
✓ Order ORD-12345 is valid
✓ Step completed: validate

→ Step: credit-check (checkCredit)
Checking credit for customer: CUST-001
✓ Credit approved for $799.99
✓ Step completed: credit-check

→ Step: approve (approveOrder)
✓ Order ORD-12345 APPROVED
✓ Step completed: approve

═══ Workflow Completed ═══
Process ID: CASE-1234567890
Status: COMPLETED

╔════════════════════════════════════════╗
║           Final Results                ║
╚════════════════════════════════════════╝
Order ID: ORD-12345
Order Valid: true
Credit Approved: true
Final Status: APPROVED
Workflow Status: COMPLETED

✓ Tutorial completed successfully!
```

## Step 10: Experiment!

### Try Different Scenarios

**1. Invalid Order**
```java
// Remove required field to trigger validation failure
orderData.remove("items");
```

**2. High Amount Order**
```java
// Change amount to trigger credit rejection
processVariables.put("orderAmount", 5000.0);
```

**3. Multiple Orders**
```java
// Run multiple workflows concurrently
for (int i = 0; i < 5; i++) {
    String caseId = "CASE-" + i;
    runtimeService.startCase(caseId, workflowJson, variables, null);
}
```

## Understanding What Happened

### 1. Workflow Lifecycle
```
Initialize Service → Load Definition → Start Case → Execute Steps → Complete
```

### 2. Step Execution
Each step:
1. Receives `WorkflowContext`
2. Reads process variables
3. Executes business logic
4. Writes results to variables
5. Completes successfully (or throws exception)

### 3. Data Flow
```
Process Variables → Step 1 → Modified Variables → Step 2 → Final Variables
```

### 4. Persistence
After each step, the workflow state is saved to `./workflow-data/`:
```
workflow-data/
├── processes/
│   └── CASE-1234567890.json
├── steps/
│   ├── CASE-1234567890-validate.json
│   ├── CASE-1234567890-credit-check.json
│   └── CASE-1234567890-approve.json
└── variables/
    └── CASE-1234567890.json
```

## Common Issues & Solutions

### Issue: "Unknown step type" error

**Cause:** Mismatch between JSON `stepType` and factory method

**Solution:** Ensure `stepType` in JSON matches the case in `getStepInstance()`:
```java
// JSON: "stepType": "validateOrder"
// Factory: case "validateOrder": return new ValidateOrderStep();
```

### Issue: NullPointerException when accessing variables

**Cause:** Variable not set in previous step

**Solution:** Always check for null:
```java
String value = context.getProcessVariable("key");
if (value == null) {
    // Handle missing variable
}
```

### Issue: Workflow doesn't complete

**Cause:** Exception thrown in step

**Solution:** Check logs and add try-catch in steps:
```java
try {
    // Step logic
} catch (Exception e) {
    System.err.println("Error: " + e.getMessage());
    throw e;
}
```

## Next Steps

Now that you've completed your first workflow, you can:

1. **Add Parallel Execution**
   - Learn about execution paths
   - Run steps concurrently
   - [Parallel Execution Tutorial](./parallel-execution.md)

2. **Add Routing Logic**
   - Create conditional workflows
   - Implement WorkflowRoute
   - [Routing Tutorial](./routing.md)

3. **Add Human Tasks**
   - Integrate work baskets
   - Handle approvals
   - [Work Baskets Guide](../concepts/work-baskets.md)

4. **Use a Database**
   - Replace FileDao
   - Implement custom DAO
   - [Custom DAO Guide](../persistence/custom-dao.md)

5. **Deploy to Production**
   - Configure for production
   - Set up monitoring
   - [Deployment Guide](../deployment/README.md)

## Complete Source Code

The complete working code for this tutorial is available in the examples directory:
- [examples/first-workflow/](../../examples/first-workflow/)

## Get Help

- 📚 [Full Documentation](../README.md)
- 💬 [GitHub Discussions](https://github.com/A-N-O-D-E-R/workflow/discussions)
- 🐛 [Report Issues](https://github.com/A-N-O-D-E-R/workflow/issues)

---

**Congratulations!** 🎉 You've successfully created and run your first workflow with Simple Workflow engine!
