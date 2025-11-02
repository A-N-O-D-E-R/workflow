# API Reference

Complete API reference for the Simple Workflow engine.

## Core Services

### WorkflowService

The main entry point for initializing the workflow engine.

#### Initialization

```java
/**
 * Initialize the workflow service
 * @param poolSize Number of worker threads
 * @param processorTimeout Timeout for step execution (milliseconds)
 * @param executionPathDelimiter Delimiter for nested paths (usually "-")
 */
public static void init(int poolSize, long processorTimeout, String executionPathDelimiter)
```

**Example:**
```java
WorkflowService.init(10, 30000, "-");
```

#### Getting Service Instance

```java
public static WorkflowService instance()
```

**Example:**
```java
WorkflowService service = WorkflowService.instance();
```

---

### RuntimeService

Manages workflow execution and lifecycle.

#### Getting Runtime Service

```java
public RuntimeService getRunTimeService(
    CommonService dao,
    WorkflowComponantFactory factory,
    EventHandler handler,
    SlaConfig slaConfig
)
```

**Parameters:**
- `dao` - Persistence layer implementation
- `factory` - Component factory for creating steps and routes
- `handler` - Event handler for lifecycle callbacks
- `slaConfig` - Optional SLA configuration

**Example:**
```java
CommonService dao = new FileDao("./workflow-data");
WorkflowComponantFactory factory = new MyComponentFactory();
EventHandler handler = new MyEventHandler();

RuntimeService rts = WorkflowService.instance()
    .getRunTimeService(dao, factory, handler, null);
```

#### Starting a Workflow

```java
public void startCase(
    String caseId,
    String workflowDefinitionJson,
    Map<String, Object> processVariables,
    SlaConfig slaConfig
) throws Exception
```

**Parameters:**
- `caseId` - Unique identifier for this workflow instance
- `workflowDefinitionJson` - JSON workflow definition
- `processVariables` - Initial process variables
- `slaConfig` - Optional SLA configuration (overrides service-level config)

**Example:**
```java
Map<String, Object> vars = new HashMap<>();
vars.put("orderId", "ORD-123");
vars.put("amount", 299.99);

rts.startCase("CASE-001", workflowJson, vars, null);
```

#### Querying Workflow Status

```java
public ProcessEntity getProcess(String caseId) throws Exception
```

**Returns:** Complete process state including status, variables, and history

**Example:**
```java
ProcessEntity process = rts.getProcess("CASE-001");
System.out.println("Status: " + process.getStatus());
System.out.println("Progress: " + process.getCompletedSteps() +
    "/" + process.getTotalSteps());
```

#### Getting Step History

```java
public List<StepEntity> getStepHistory(String caseId) throws Exception
```

**Returns:** List of all steps executed for this case

**Example:**
```java
List<StepEntity> steps = rts.getStepHistory("CASE-001");
for (StepEntity step : steps) {
    System.out.println(step.getStepId() + ": " + step.getStatus());
}
```

#### Signaling Steps

```java
public void signalStep(String caseId, String stepId) throws Exception
```

Used to resume execution after external events (e.g., work item completion).

**Example:**
```java
rts.signalStep("CASE-001", "approval-step");
```

#### Retrying Failed Steps

```java
public void retryFailedStep(String caseId) throws Exception
```

**Example:**
```java
if (process.getStatus() == ProcessStatus.FAILED) {
    rts.retryFailedStep("CASE-001");
}
```

#### Recovering Incomplete Processes

```java
public List<ProcessEntity> recoverIncompleteProcesses() throws Exception
```

Called on startup to recover workflows that were interrupted by crashes.

**Example:**
```java
List<ProcessEntity> recovered = rts.recoverIncompleteProcesses();
System.out.println("Recovered " + recovered.size() + " workflows");
```

---

### WorkManagementService

Manages work baskets and human tasks.

#### Getting Work Management Service

```java
public WorkManagementService getWorkManagementService(CommonService dao)
```

**Example:**
```java
WorkManagementService wms = WorkflowService.instance()
    .getWorkManagementService(dao);
```

#### Creating Work Items

```java
public WorkItem createWorkItem(
    String caseId,
    String stepId,
    String basketName
) throws Exception
```

**Example:**
```java
WorkItem item = wms.createWorkItem("CASE-001", "approval", "approvals");
item.setPriority("HIGH");
wms.updateWorkItem(item);
```

#### Querying Work Items

```java
// Get work items by basket
public List<WorkItem> getWorkItemsByBasket(String basketName) throws Exception

// Get work items for a user
public List<WorkItem> getWorkItemsByUser(String userId) throws Exception

// Get specific work item
public WorkItem getWorkItem(String workItemId) throws Exception
```

**Example:**
```java
List<WorkItem> items = wms.getWorkItemsByBasket("approvals");
System.out.println("Items in basket: " + items.size());
```

#### Updating Work Items

```java
public void updateWorkItem(WorkItem item) throws Exception
```

**Example:**
```java
WorkItem item = wms.getWorkItem(workItemId);
item.setClaimedBy("user123");
item.setClaimedAt(new Date());
wms.updateWorkItem(item);
```

#### Completing Work Items

```java
public void completeWorkItem(String workItemId) throws Exception
```

**Example:**
```java
wms.completeWorkItem(workItemId);
rts.signalStep(caseId, stepId);  // Resume workflow
```

---

### SlaManagementService

Tracks milestones and SLA compliance.

#### Getting SLA Management Service

```java
public SlaManagementService getSlaManagementService(CommonService dao)
```

**Example:**
```java
SlaManagementService sms = WorkflowService.instance()
    .getSlaManagementService(dao);
```

#### Querying Milestones

```java
public List<Milestone> getMilestones(String caseId) throws Exception

public Milestone getMilestone(String caseId, String milestoneId) throws Exception
```

**Example:**
```java
List<Milestone> milestones = sms.getMilestones("CASE-001");
for (Milestone m : milestones) {
    System.out.println(m.getName() + ": " +
        (m.isBreached() ? "BREACHED" : "OK"));
}
```

#### Checking SLA Status

```java
public boolean isSlaBreached(String caseId) throws Exception

public List<Milestone> getBreachedMilestones(String caseId) throws Exception
```

**Example:**
```java
if (sms.isSlaBreached("CASE-001")) {
    List<Milestone> breached = sms.getBreachedMilestones("CASE-001");
    System.out.println("Breached milestones: " + breached.size());
}
```

---

## Component Interfaces

### WorkflowStep

Interface for implementing workflow steps.

```java
public interface WorkflowStep {
    /**
     * Execute the step
     * @param context Workflow context with access to variables and services
     * @throws Exception on execution failure
     */
    void execute(WorkflowContext context) throws Exception;
}
```

**Example Implementation:**
```java
public class ProcessOrderStep implements WorkflowStep {
    @Override
    public void execute(WorkflowContext context) throws Exception {
        String orderId = context.getProcessVariable("orderId");

        // Business logic here
        processOrder(orderId);

        context.setProcessVariable("status", "PROCESSED");
    }
}
```

---

### WorkflowRoute

Interface for implementing routing decisions.

```java
public interface WorkflowRoute {
    /**
     * Determine which path to take
     * @param context Workflow context
     * @return Path identifier or null for default path
     * @throws Exception on routing failure
     */
    String route(WorkflowContext context) throws Exception;
}
```

**Example Implementation:**
```java
public class ApprovalRoute implements WorkflowRoute {
    @Override
    public String route(WorkflowContext context) throws Exception {
        Double amount = context.getProcessVariable("orderAmount");

        if (amount > 5000) {
            return "manager-approval-path";
        } else if (amount > 1000) {
            return "supervisor-approval-path";
        } else {
            return "auto-approve-path";
        }
    }
}
```

---

### WorkflowComponantFactory

Interface for creating workflow components.

```java
public interface WorkflowComponantFactory {
    /**
     * Create a step instance
     * @param stepType Type identifier from workflow definition
     * @return Step instance or null if type unknown
     */
    WorkflowStep getStepInstance(String stepType);

    /**
     * Create a route instance
     * @param routeType Type identifier from workflow definition
     * @return Route instance or null if type unknown
     */
    WorkflowRoute getRouteInstance(String routeType);
}
```

**Example Implementation:**
```java
public class MyComponentFactory implements WorkflowComponantFactory {
    @Override
    public WorkflowStep getStepInstance(String stepType) {
        switch (stepType) {
            case "validateOrder":
                return new ValidateOrderStep();
            case "processPayment":
                return new ProcessPaymentStep();
            default:
                return null;
        }
    }

    @Override
    public WorkflowRoute getRouteInstance(String routeType) {
        switch (routeType) {
            case "approvalRoute":
                return new ApprovalRoute();
            default:
                return null;
        }
    }
}
```

---

### EventHandler

Interface for handling workflow lifecycle events.

```java
public interface EventHandler {
    void onProcessStart(ProcessEntity process);
    void onProcessComplete(ProcessEntity process);
    void onProcessError(ProcessEntity process, Exception error);

    void onStepStart(StepEntity step);
    void onStepComplete(StepEntity step);
    void onStepError(StepEntity step, Exception error);
}
```

**Example Implementation:**
```java
public class MyEventHandler implements EventHandler {
    @Override
    public void onProcessStart(ProcessEntity process) {
        log.info("Workflow started: " + process.getProcessId());
    }

    @Override
    public void onProcessComplete(ProcessEntity process) {
        log.info("Workflow completed: " + process.getProcessId());
        sendNotification(process);
    }

    @Override
    public void onProcessError(ProcessEntity process, Exception error) {
        log.error("Workflow error: " + process.getProcessId(), error);
        alertOps(process, error);
    }

    @Override
    public void onStepStart(StepEntity step) {
        log.debug("Step started: " + step.getStepId());
    }

    @Override
    public void onStepComplete(StepEntity step) {
        log.debug("Step completed: " + step.getStepId());
    }

    @Override
    public void onStepError(StepEntity step, Exception error) {
        log.error("Step error: " + step.getStepId(), error);
    }
}
```

---

### WorkflowContext

Context object passed to workflow components.

```java
public interface WorkflowContext {
    // Case information
    String getCaseId();
    String getCurrentStepId();
    String getExecutionPath();

    // Process variables
    <T> T getProcessVariable(String key);
    void setProcessVariable(String key, Object value);
    Map<String, Object> getAllProcessVariables();
    boolean hasProcessVariable(String key);
    void removeProcessVariable(String key);

    // Services
    CommonService getDao();
    RuntimeService getRuntimeService();
    WorkManagementService getWorkManagementService();
    SlaManagementService getSlaManagementService();
}
```

---

### CommonService (DAO Interface)

Interface for implementing custom persistence.

```java
public interface CommonService {
    // Process operations
    void saveProcess(ProcessEntity process) throws Exception;
    ProcessEntity loadProcess(String caseId) throws Exception;
    void deleteProcess(String caseId) throws Exception;
    List<ProcessEntity> loadAllProcesses() throws Exception;

    // Step operations
    void saveStep(StepEntity step) throws Exception;
    StepEntity loadStep(String caseId, String stepId) throws Exception;
    List<StepEntity> loadSteps(String caseId) throws Exception;

    // Variable operations
    void saveVariables(String caseId, Map<String, Object> variables) throws Exception;
    Map<String, Object> loadVariables(String caseId) throws Exception;

    // Work item operations (if supporting work baskets)
    void saveWorkItem(WorkItem item) throws Exception;
    WorkItem loadWorkItem(String workItemId) throws Exception;
    List<WorkItem> loadWorkItemsByBasket(String basketName) throws Exception;

    // SLA operations (if supporting SLA tracking)
    void saveMilestone(Milestone milestone) throws Exception;
    List<Milestone> loadMilestones(String caseId) throws Exception;
}
```

See [Custom DAO Implementation](../persistence/custom-dao.md) for detailed guide.

---

## Entities

### ProcessEntity

```java
public class ProcessEntity {
    private String processId;
    private String caseId;
    private String processDefinitionId;
    private ProcessStatus status;
    private Date startTime;
    private Date endTime;
    private int totalSteps;
    private int completedSteps;
    private String currentStepId;
    private Map<String, Object> processVariables;
    private String errorMessage;

    // Getters and setters...
}
```

### StepEntity

```java
public class StepEntity {
    private String stepId;
    private String caseId;
    private String stepType;
    private String executionPath;
    private int order;
    private StepStatus status;
    private Date startTime;
    private Date endTime;
    private String errorMessage;
    private int retryCount;

    // Getters and setters...
}
```

### ProcessStatus

```java
public enum ProcessStatus {
    PENDING,      // Created but not started
    RUNNING,      // Currently executing
    COMPLETED,    // Successfully completed
    FAILED,       // Failed with error
    SUSPENDED     // Paused (e.g., waiting for work item)
}
```

### StepStatus

```java
public enum StepStatus {
    PENDING,      // Not yet started
    RUNNING,      // Currently executing
    COMPLETED,    // Successfully completed
    FAILED,       // Failed with error
    SKIPPED       // Skipped due to routing
}
```

---

## Related Documentation

- [Getting Started](../getting-started.md) - Basic usage examples
- [Core Concepts](../concepts/README.md) - Understanding the engine
- [Custom DAO](../persistence/custom-dao.md) - Implementing persistence
- [Examples](../../examples/) - Working code samples
