<artifact identifier="common-issues-doc" type="text/markdown" title="Common Issues Troubleshooting Guide">
# Common Issues Troubleshooting Guide

## Overview

This guide provides solutions to common problems encountered when developing and running Simple Workflow applications. Each issue includes symptoms, root causes, diagnostic steps, and solutions.

## Table of Contents
- [Startup and Configuration Issues](#startup-and-configuration-issues)
- [Workflow Execution Issues](#workflow-execution-issues)
- [Parallel Processing Issues](#parallel-processing-issues)
- [Persistence Issues](#persistence-issues)
- [Crash Recovery Issues](#crash-recovery-issues)
- [Performance Issues](#performance-issues)
- [Integration Issues](#integration-issues)
- [Development Issues](#development-issues)

## Startup and Configuration Issues

### Issue 1: "WorkflowService not initialized"

**Symptoms**:
```
Exception: WorkflowService must be initialized before use
```

**Root Cause**: `WorkflowService.init()` not called before getting runtime service

**Solution**:
```java
// ✅ Correct order
public void initializeWorkflow() {
    // 1. Initialize WorkflowService FIRST
    WorkflowService.init(10, 30000, "-");
    
    // 2. Then get runtime service
    RuntimeService rts = WorkflowService.instance()
        .getRunTimeService(dao, factory, handler, null);
}

// ❌ Wrong - will fail
public void initializeWorkflow() {
    RuntimeService rts = WorkflowService.instance()  // WorkflowService not initialized!
        .getRunTimeService(dao, factory, handler, null);
}
```

**Prevention**: Call `WorkflowService.init()` in application startup

```java
@SpringBootApplication
public class Application {
    
    @PostConstruct
    public void init() {
        WorkflowService.init(10, 30000, "-");
    }
    
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

---

### Issue 2: "Component not found" or "Class not found"

**Symptoms**:
```
Exception: Component 'validate_order' not found
ClassNotFoundException: com.example.ValidateOrderStep
```

**Root Cause**: Component factory cannot find or instantiate step/route class

**Diagnosis**:
```java
// Check if class exists
Class.forName("com.example.ValidateOrderStep");

// Check factory registration
WorkflowComponantFactory factory = context.getComponentFactory();
Object component = factory.getComponentInstance("validate_order", context);
```

**Solution 1**: Implement proper component factory

```java
public class MyComponentFactory implements WorkflowComponantFactory {
    
    @Override
    public Object getComponentInstance(String componentName, WorkflowContext context) {
        switch (componentName) {
            case "validate_order":
                return new ValidateOrderStep(context);
            case "process_payment":
                return new ProcessPaymentStep(context);
            case "parallel_route":
                return new ParallelProcessingRoute(context);
            default:
                throw new IllegalArgumentException(
                    "Unknown component: " + componentName
                );
        }
    }
}
```

**Solution 2**: Use reflection-based factory

```java
public class ReflectionComponentFactory implements WorkflowComponantFactory {
    
    private final String basePackage = "com.example.workflow.steps";
    
    @Override
    public Object getComponentInstance(String componentName, WorkflowContext context) {
        try {
            // Convert snake_case to PascalCase
            String className = toPascalCase(componentName);
            String fullClassName = basePackage + "." + className;
            
            Class<?> clazz = Class.forName(fullClassName);
            Constructor<?> constructor = clazz.getConstructor(WorkflowContext.class);
            
            return constructor.newInstance(context);
            
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate component: " + componentName, e);
        }
    }
    
    private String toPascalCase(String snakeCase) {
        String[] parts = snakeCase.split("_");
        return Arrays.stream(parts)
            .map(p -> Character.toUpperCase(p.charAt(0)) + p.substring(1))
            .collect(Collectors.joining());
    }
}
```

**Solution 3**: Spring-based factory

```java
@Component
public class SpringComponentFactory implements WorkflowComponantFactory {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Override
    public Object getComponentInstance(String componentName, WorkflowContext context) {
        try {
            // Get bean by name
            Object bean = applicationContext.getBean(componentName);
            
            // Set context if needed
            if (bean instanceof InvokableTask) {
                ((InvokableTask) bean).setContext(context);
            }
            
            return bean;
            
        } catch (NoSuchBeanDefinitionException e) {
            throw new RuntimeException("Component not found: " + componentName, e);
        }
    }
}
```

---

### Issue 3: Duplicate key separator in case ID

**Symptoms**:
```
Case ID "order-123-456" contains separator character '-'
Workflow behaves incorrectly
```

**Root Cause**: Case ID contains the separator character configured in `WorkflowService.init()`

**Solution**:
```java
// ✅ Option 1: Use different separator
WorkflowService.init(10, 30000, "_");  // Use underscore
String caseId = "order-123-456";  // Hyphens OK now

// ✅ Option 2: Sanitize case IDs
String caseId = "order-123-456".replace("-", "_");  // "order_123_456"

// ✅ Option 3: Use separator-free IDs
String caseId = UUID.randomUUID().toString().replace("-", "");  // No separators
```

**Prevention**: Choose separator not used in business IDs

```java
// Good separators: "-", "_", ":", "|", "~"
// Avoid: ".", "/", "\\" (used in paths)
```

---

### Issue 4: Thread pool exhaustion

**Symptoms**:
```
Workflow hangs
No parallel branches executing
Log: "Thread pool queue full"
```

**Root Cause**: Too many parallel workflows or branches for thread pool size

**Diagnosis**:
```java
ThreadPoolExecutor executor = (ThreadPoolExecutor) WorkflowService.instance()
    .getExecutorService();

System.out.println("Active threads: " + executor.getActiveCount());
System.out.println("Pool size: " + executor.getPoolSize());
System.out.println("Queue size: " + executor.getQueue().size());
System.out.println("Max pool size: " + executor.getMaximumPoolSize());
```

**Solution 1**: Increase thread pool size

```java
// ✅ Increase max threads
WorkflowService.init(
    50,      // Increase from 10 to 50
    30000,
    "-"
);
```

**Solution 2**: Limit parallel branches

```java
public class LimitedParallelRoute implements InvokableRoute {
    
    private static final int MAX_PARALLEL_BRANCHES = 10;
    
    @Override
    public RouteResponse executeRoute() {
        List<String> allBranches = getAllBranches();
        
        // Limit to prevent thread exhaustion
        List<String> limitedBranches = allBranches.stream()
            .limit(MAX_PARALLEL_BRANCHES)
            .collect(Collectors.toList());
        
        if (allBranches.size() > MAX_PARALLEL_BRANCHES) {
            log.warn("Limited branches from {} to {}", 
                allBranches.size(), MAX_PARALLEL_BRANCHES);
        }
        
        return new RouteResponse(
            StepResponseType.OK_PROCEED,
            limitedBranches,
            null
        );
    }
}
```

**Solution 3**: Process in batches

```java
public class BatchedParallelRoute implements InvokableRoute {
    
    @Override
    public RouteResponse executeRoute() {
        WorkflowVariables vars = context.getProcessVariables();
        
        @SuppressWarnings("unchecked")
        List<String> allItems = (List<String>) vars.getValue(
            "all_items",
            WorkflowVariableType.LIST_OF_OBJECT
        );
        
        Integer batchNumber = vars.getInteger("batch_number");
        if (batchNumber == null) batchNumber = 0;
        
        int batchSize = 10;
        int start = batchNumber * batchSize;
        int end = Math.min(start + batchSize, allItems.size());
        
        List<String> batch = allItems.subList(start, end);
        
        // Store remaining
        if (end < allItems.size()) {
            vars.setValue("batch_number", WorkflowVariableType.INTEGER, batchNumber + 1);
        }
        
        return new RouteResponse(StepResponseType.OK_PROCEED, batch, null);
    }
}
```

---

## Workflow Execution Issues

### Issue 5: Workflow starts but doesn't execute

**Symptoms**:
```
startCase() returns successfully
No steps execute
Workflow stuck in initial state
```

**Root Cause**: Workflow definition has no "start" step or incorrect first step

**Diagnosis**:
```java
// Check workflow definition
WorkflowDefinition def = /* load definition */;
List<Step> flow = def.getFlow();

// Find start step
Step startStep = flow.stream()
    .filter(s -> "start".equals(s.getName()))
    .findFirst()
    .orElse(null);

if (startStep == null) {
    System.out.println("ERROR: No start step found!");
}

// Check first step points to valid next step
if (startStep != null) {
    String nextStepName = startStep.getNext();
    Step nextStep = def.getStep(nextStepName);
    if (nextStep == null) {
        System.out.println("ERROR: Start step points to non-existent step: " + nextStepName);
    }
}
```

**Solution**: Ensure valid workflow definition

```json
{
  "flow": [
    {
      "name": "start",
      "type": "start",
      "next": "validate_order"  // Must point to existing step
    },
    {
      "name": "validate_order",
      "type": "task",
      "component": "validator",
      "next": "process_payment"
    },
    {
      "name": "process_payment",
      "type": "task",
      "component": "payment_processor",
      "next": "end"
    },
    {
      "name": "end",
      "type": "end"
    }
  ]
}
```

---

### Issue 6: "Step returns null response"

**Symptoms**:
```
NullPointerException in workflow engine
Log: "Step returned null response"
```

**Root Cause**: Step implementation returns null instead of TaskResponse

**Solution**:
```java
// ❌ Wrong - returns null
public class BrokenStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        performOperation();
        return null;  // WRONG!
    }
}

// ✅ Correct - always return TaskResponse
public class GoodStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        try {
            performOperation();
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        } catch (Exception e) {
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "error_queue"
            );
        }
    }
}
```

**Prevention**: Use template for new steps

```java
public abstract class BaseStep implements InvokableTask {
    
    protected final WorkflowContext context;
    
    public BaseStep(WorkflowContext context) {
        this.context = context;
    }
    
    @Override
    public WorkflowContext getContext() {
        return context;
    }
    
    @Override
    public final TaskResponse executeStep() {
        try {
            return doExecute();
        } catch (Exception e) {
            log.error("Step execution failed", e);
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "error_queue"
            );
        }
    }
    
    protected abstract TaskResponse doExecute();
}
```

---

### Issue 7: Infinite loop in workflow

**Symptoms**:
```
Workflow never completes
Same steps execute repeatedly
High CPU usage
```

**Root Cause**: Step points back to itself or circular reference in flow

**Diagnosis**:
```java
// Check for circular references
public boolean hasCircularReference(WorkflowDefinition def) {
    Set<String> visited = new HashSet<>();
    String currentStep = "start";
    
    while (currentStep != null && !currentStep.equals("end")) {
        if (visited.contains(currentStep)) {
            System.out.println("Circular reference detected at: " + currentStep);
            return true;
        }
        visited.add(currentStep);
        
        Step step = def.getStep(currentStep);
        currentStep = step.getNext();
    }
    
    return false;
}
```

**Solution**: Fix workflow definition

```json
// ❌ Wrong - circular reference
{
  "flow": [
    {"name": "start", "next": "step_1"},
    {"name": "step_1", "next": "step_2"},
    {"name": "step_2", "next": "step_1"}  // Points back to step_1!
  ]
}

// ✅ Correct - linear flow
{
  "flow": [
    {"name": "start", "next": "step_1"},
    {"name": "step_1", "next": "step_2"},
    {"name": "step_2", "next": "end"}
  ]
}
```

**Prevention**: Validate workflow definition on load

```java
public class WorkflowValidator {
    
    public void validate(WorkflowDefinition def) {
        // Check for start step
        if (!hasStartStep(def)) {
            throw new IllegalArgumentException("No start step found");
        }
        
        // Check for end step
        if (!hasEndStep(def)) {
            throw new IllegalArgumentException("No end step found");
        }
        
        // Check all steps reference valid next steps
        for (Step step : def.getFlow()) {
            String nextStepName = step.getNext();
            if (nextStepName != null && def.getStep(nextStepName) == null) {
                throw new IllegalArgumentException(
                    "Step '" + step.getName() + "' references non-existent step: " + nextStepName
                );
            }
        }
        
        // Check for circular references
        if (hasCircularReference(def)) {
            throw new IllegalArgumentException("Circular reference detected in workflow");
        }
    }
}
```

---

### Issue 8: Workflow completes prematurely

**Symptoms**:
```
Workflow marked complete
Not all steps executed
Missing expected results
```

**Root Cause**: Step incorrectly returns OK_PROCEED and jumps to "end"

**Diagnosis**:
```java
// Check workflow info
WorkflowInfo info = dao.get(WorkflowInfo.class, "workflow_process_info-" + caseId);

System.out.println("Is Complete: " + info.getIsComplete());
System.out.println("Last Executed Step: " + info.getLastExecutedStep());

// Check which steps were executed
for (ExecPath path : info.getExecPaths()) {
    System.out.println("Path: " + path.getName() + ", Last Step: " + path.getStep());
}
```

**Solution**: Check step configuration

```json
// ❌ Wrong - step points to "end" too early
{
  "name": "validate_order",
  "type": "task",
  "component": "validator",
  "next": "end"  // Skips remaining steps!
}

// ✅ Correct - step points to next step in sequence
{
  "name": "validate_order",
  "type": "task",
  "component": "validator",
  "next": "process_payment"
}
```

**Also check**: Step implementation doesn't use incorrect ticket

```java
// ❌ Wrong - jumps to end
return new TaskResponse(
    StepResponseType.OK_PROCEED,
    "",
    "",
    "end"  // Don't ticket to "end" unless intentional
);

// ✅ Correct - proceed to next step
return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
```

---

## Parallel Processing Issues

### Issue 9: Parallel routes don't execute

**Symptoms**:
```
Parallel route defined in workflow
Only one branch executes
Other branches never start
```

**Root Cause**: Route doesn't return branch names, or returns empty list

**Diagnosis**:
```java
// Check route implementation
public class DebugRoute implements InvokableRoute {
    @Override
    public RouteResponse executeRoute() {
        List<String> branches = determineBranches();
        
        System.out.println("Route returning branches: " + branches);
        System.out.println("Branch count: " + branches.size());
        
        return new RouteResponse(StepResponseType.OK_PROCEED, branches, null);
    }
}
```

**Solution 1**: Ensure route returns branch names

```java
// ❌ Wrong - returns empty list
public class BrokenRoute implements InvokableRoute {
    @Override
    public RouteResponse executeRoute() {
        return new RouteResponse(
            StepResponseType.OK_PROCEED,
            new ArrayList<>(),  // No branches!
            null
        );
    }
}

// ✅ Correct - returns branch names from definition
public class GoodRoute implements InvokableRoute {
    @Override
    public RouteResponse executeRoute() {
        WorkflowDefinition def = context.getWorkflowDefinition();
        Route routeDef = def.getRoute(getCurrentRouteName());
        
        List<String> branches = routeDef.getBranches().stream()
            .map(RouteBranch::getName)
            .collect(Collectors.toList());
        
        return new RouteResponse(StepResponseType.OK_PROCEED, branches, null);
    }
}
```

**Solution 2**: Check workflow definition has branches

```json
{
  "name": "parallel_route",
  "type": "p_route",
  "component": "parallel_processor",
  "branches": [
    {"name": "branch_a", "next": "step_a"},
    {"name": "branch_b", "next": "step_b"},
    {"name": "branch_c", "next": "step_c"}
  ],
  "join": "join_1"
}
```

---

### Issue 10: Workflow hangs at join point

**Symptoms**:
```
Parallel branches complete
Workflow stuck at join
Never proceeds past join
```

**Root Cause**: One or more branches pended, waiting for resume

**Diagnosis**:
```java
WorkflowInfo info = dao.get(WorkflowInfo.class, "workflow_process_info-" + caseId);

System.out.println("Pend Path: " + info.getPendExecPath());

for (ExecPath path : info.getExecPaths()) {
    System.out.println("Path: " + path.getName());
    System.out.println("  Status: " + path.getStatus());
    System.out.println("  Step: " + path.getStep());
    System.out.println("  Workbasket: " + path.getPendWorkBasket());
    System.out.println("  Response Type: " + path.getStepResponseType());
}
```

**Solution**: Resume pended branch

```java
// Check which branch is pended
String pendPath = info.getPendExecPath();
ExecPath pendedPath = info.getExecPaths().stream()
    .filter(p -> p.getName().equals(pendPath))
    .findFirst()
    .orElse(null);

if (pendedPath != null) {
    System.out.println("Branch pended at: " + pendedPath.getStep());
    System.out.println("Workbasket: " + pendedPath.getPendWorkBasket());
    
    // Resume the workflow
    runtimeService.resumeCase(caseId);
}
```

**Prevention**: Ensure all branches complete or handle pends explicitly

```java
public class ParallelBranchStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        try {
            processData();
            
            // ✅ Always return OK_PROCEED or appropriate response
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (Exception e) {
            // ✅ Handle errors explicitly
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                e.getMessage(),
                "error_queue"
            );
        }
    }
}
```

---

### Issue 11: Race condition in parallel branches

**Symptoms**:
```
Inconsistent results
Duplicate operations
Lost updates
```

**Root Cause**: Multiple branches accessing shared state without synchronization

**Diagnosis**:
```java
// Add logging to detect race conditions
public class DetectRaceCondition implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        String execPath = context.getExecPathName();
        
        Integer counter = vars.getInteger("counter");
        log.info("[{}] Read counter: {}", execPath, counter);
        
        // Simulate work
        Thread.sleep(100);
        
        counter = (counter == null) ? 1 : counter + 1;
        vars.setValue("counter", WorkflowVariableType.INTEGER, counter);
        
        log.info("[{}] Wrote counter: {}", execPath, counter);
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

**Solution**: Use synchronization

```java
// ✅ Correct - synchronized access
public class SafeParallelStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        synchronized (vars) {
            Integer counter = vars.getInteger("counter");
            counter = (counter == null) ? 1 : counter + 1;
            vars.setValue("counter", WorkflowVariableType.INTEGER, counter);
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

**Alternative**: Use branch-specific variables

```java
public class BranchSpecificVariables implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        String execPath = context.getExecPathName();
        
        // Use branch-specific variable names
        String branchName = extractBranchName(execPath);
        String varName = "branch_" + branchName + "_result";
        
        vars.setValue(varName, WorkflowVariableType.STRING, "completed");
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

---

### Issue 12: Execution path naming collision

**Symptoms**:
```
Unexpected behavior in parallel routes
Wrong branch executing
Data corruption
```

**Root Cause**: Route or branch names contain dots or special characters

**Diagnosis**:
```java
// Check workflow definition
Route route = def.getRoute("my_route");
for (RouteBranch branch : route.getBranches()) {
    String branchName = branch.getName();
    if (branchName.contains(".")) {
        System.out.println("ERROR: Branch name contains dot: " + branchName);
    }
}
```

**Solution**: Use valid names

```json
// ❌ Wrong - names contain dots
{
  "name": "route.1",
  "branches": [
    {"name": "branch.a", "next": "step_1"},
    {"name": "branch.b", "next": "step_2"}
  ]
}

// ✅ Correct - use underscores or hyphens
{
  "name": "route_1",
  "branches": [
    {"name": "branch_a", "next": "step_1"},
    {"name": "branch_b", "next": "step_2"}
  ]
}
```

**Validation**:
```java
public void validateNames(WorkflowDefinition def) {
    for (Step step : def.getFlow()) {
        if (step.getName().contains(".")) {
            throw new IllegalArgumentException(
                "Step name cannot contain dots: " + step.getName()
            );
        }
        
        if (step.getType() == StepType.P_ROUTE || step.getType() == StepType.S_ROUTE) {
            Route route = def.getRoute(step.getName());
            for (RouteBranch branch : route.getBranches()) {
                if (branch.getName().contains(".")) {
                    throw new IllegalArgumentException(
                        "Branch name cannot contain dots: " + branch.getName()
                    );
                }
            }
        }
    }
}
```

---

## Persistence Issues

### Issue 13: Process variables not persisting

**Symptoms**:
```
Variables set in step
Lost after workflow restart
Variables return null
```

**Root Cause**: Variables not saved, or using lazy persistence mode

**Diagnosis**:
```java
// Check persistence mode
boolean writeAfterEachStep = WorkflowService.instance()
    .isWriteProcessInfoAfterEachStep();
System.out.println("Write after each step: " + writeAfterEachStep);

// Check if variables are in workflow info
WorkflowInfo info = dao.get(WorkflowInfo.class, "workflow_process_info-" + caseId);
WorkflowVariables vars = info.getProcessVariables();

System.out.println("Variables in storage:");
for (ProcessVariableValue var : vars.getVariables()) {
    System.out.println("  " + var.getName() + " = " + var.getValueString());
}
```

**Solution 1**: Use aggressive persistence

```java
// ✅ Enable aggressive persistence
WorkflowService.instance().setWriteProcessInfoAfterEachStep(true);
```

**Solution 2**: Ensure variables are set correctly

```java
// ❌ Wrong - variable not actually set
public class BrokenStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        String localVar = "value";  // Only in local memory!
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}

// ✅ Correct - variable persisted
public class GoodStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        vars.setValue("my_var", WorkflowVariableType.STRING, "value");
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

---

### Issue 14: Database connection errors

**Symptoms**:
```
SQLException: Connection refused
HikariPool: Connection is not available
Timeout waiting for connection
```

**Root Cause**: Database not accessible, connection pool exhausted, or misconfiguration

**Diagnosis**:
```java
// Test database connection
try {
    Connection conn = dataSource.getConnection();
    System.out.println("Database connection successful");
    conn.close();
} catch (SQLException e) {
    System.out.println("Database connection failed: " + e.getMessage());
}

// Check connection pool stats
HikariDataSource ds = (HikariDataSource) dataSource;
HikariPoolMXBean pool = ds.getHikariPoolMXBean();

System.out.println("Active connections: " + pool.getActiveConnections());
System.out.println("Idle connections: " + pool.getIdleConnections());
System.out.println("Total connections: " + pool.getTotalConnections());
System.out.println("Threads waiting: " + pool.getThreadsAwaitingConnection());
```

**Solution 1**: Fix database configuration

```properties
# Check database URL
spring.datasource.url=jdbc:postgresql://localhost:5432/workflow_db
spring.datasource.username=workflow_user
spring.datasource.password=correct_password

# Test connection
psql -h localhost -U workflow_user -d workflow_db
```

**Solution 2**: Increase connection pool size

```java
HikariConfig config = new HikariConfig();
config.setMaximumPoolSize(20);  // Increase from default
config.setMinimumIdle(5);
config.setConnectionTimeout(30000);
```

**Solution 3**: Close connections properly

```java
// ❌ Wrong - connection leak
public void queryDatabase() {
    Connection conn = dataSource.getConnection();
    // ... use connection
    // Connection never closed!
}

// ✅ Correct - use try-with-resources
public void queryDatabase() {
    try (Connection conn = dataSource.getConnection()) {
        // ... use connection
    } // Automatically closed
}
```

---

### Issue 15: File DAO: Corrupted JSON files

**Symptoms**:
```
JsonParseException: Unexpected end of input
Workflow info cannot be loaded
File appears truncated
```

**Root Cause**: JVM crash during file write, non-atomic write operation

**Diagnosis**:
```bash
# Check file integrity
cat workflow-data/workflow_process_info-case-123.json | jq .

# Check for temp files
ls -la workflow-data/*.tmp

# Check file sizes
ls -lh workflow-data/workflow_process_info-*.json
```

**Solution 1**: Use atomic writes (already in FileDao)

```java
// FileDao uses atomic writes
File tempFile = new File(file.getAbsolutePath() + ".tmp");
Files.write(tempFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
Files.move(tempFile.toPath(), file.toPath(), 
           StandardCopyOption.REPLACE_EXISTING,
           StandardCopyOption.ATOMIC_MOVE);
```

**Solution 2**: Implement backup strategy

```java
public class BackupFileDao extends FileDao {
    
    @Override
    public void save(Serializable id, Object object) {
        String filename = id.toString() + ".json";
        File file = new File(storageDir, filename);
        
        // Backup existing file
        if (file.exists()) {
            File backup = new File(storageDir, filename + ".bak");
            Files.copy(file.toPath(), backup.toPath(), 
                      StandardCopyOption.REPLACE_EXISTING);
        }
        
        // Save new version
        super.save(id, object);
    }
}
```

**Recovery**:
```bash
# Restore from backup
cp workflow-data/workflow_process_info-case-123.json.bak \
   workflow-data/workflow_process_info-case-123.json
```

---

## Crash Recovery Issues

### Issue 16: Workflow re-executes steps after crash

**Symptoms**:
```
After crash and restart
Steps execute multiple times
Duplicate emails sent
Duplicate payments charged
```

**Root Cause**: Steps are not idempotent

**Diagnosis**:
```java
// Check if step is idempotent
@Test
public void testStepIdempotency() {
    WorkflowContext context = createTestContext();
    MyStep step = new MyStep(context);
    
    // Execute twice
    TaskResponse response1 = step.executeStep();
    TaskResponse response2 = step.executeStep();
    
    // Should produce same result
    assertEquals(response1.getUnitResponseType(), response2.getUnitResponseType());
    
    // External service should be called only once
    verify(mockService, times(1)).performOperation();
}
```

**Solution**: Implement idempotency

```java
// ❌ Wrong - not idempotent
public class NonIdempotentStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        emailService.sendEmail();  // Sends every time!
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}

// ✅ Correct - idempotent
public class IdempotentStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        // Check if already executed
        Boolean emailSent = vars.getBoolean("email_sent");
        if (Boolean.TRUE.equals(emailSent)) {
            log.info("Email already sent, skipping");
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
        
        // Execute
        emailService.sendEmail();
        
        // Mark as executed
        vars.setValue("email_sent", WorkflowVariableType.BOOLEAN, true);
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

**See**: [Idempotency Patterns Documentation](../patterns/idempotency.md)

---

### Issue 17: "Workflow cannot be repaired" error

**Symptoms**:
```
resumeCase() throws exception
Message: "Workflow cannot be repaired"
Workflow stuck in invalid state
```

**Root Cause**: Workflow info corrupted beyond automatic repair

**Diagnosis**:
```java
// Load workflow info
WorkflowInfo info = dao.get(WorkflowInfo.class, "workflow_process_info-" + caseId);

// Check for issues
System.out.println("Is Complete: " + info.getIsComplete());
System.out.println("Pend Path: " + info.getPendExecPath());

for (ExecPath path : info.getExecPaths()) {
    System.out.println("Path: " + path.getName());
    System.out.println("  Status: " + path.getStatus());
    System.out.println("  Step: " + path.getStep());
    
    // Check for null or invalid values
    if (path.getStep() == null) {
        System.out.println("  ERROR: Step is null!");
    }
}

// Check if definition exists
WorkflowDefinition def = dao.get(WorkflowDefinition.class, "journey-" + caseId);
if (def == null) {
    System.out.println("ERROR: Workflow definition not found!");
}
```

**Solution 1**: Restore from backup

```bash
# Restore workflow info from backup
cp workflow-data/workflow_process_info-case-123.json.bak \
   workflow-data/workflow_process_info-case-123.json

# Or restore from database backup
pg_restore -t workflow_info workflow_backup.dump
```

**Solution 2**: Manual repair

```java
public void repairWorkflowInfo(String caseId) {
    WorkflowInfo info = dao.get(WorkflowInfo.class, "workflow_process_info-" + caseId);
    
    // Fix null values
    if (info.getPendExecPath() == null) {
        info.setPendExecPath("");
    }
    
    // Fix execution paths
    for (ExecPath path : info.getExecPaths()) {
        if (path.getStep() == null) {
            path.setStep("start");  // Reset to start
        }
        if (path.getStatus() == null) {
            path.setStatus(ExecPathStatus.COMPLETED);
        }
    }
    
    // Save repaired info
    dao.update("workflow_process_info-" + caseId, info);
    
    // Try resume again
    runtimeService.resumeCase(caseId);
}
```

**Solution 3**: Reopen case with new workflow

```java
public void reopenCase(String caseId) {
    // Archive old workflow info
    WorkflowInfo oldInfo = dao.get(WorkflowInfo.class, "workflow_process_info-" + caseId);
    dao.save("workflow_process_info-" + caseId + "_archived", oldInfo);
    
    // Delete corrupted info
    dao.delete("workflow_process_info-" + caseId);
    dao.delete("journey-" + caseId);
    
    // Start new workflow with same case ID
    String workflowJson = loadWorkflowDefinition();
    
    // Copy important process variables from old info
    WorkflowVariables oldVars = oldInfo.getProcessVariables();
    WorkflowVariables newVars = new WorkflowVariables();
    copyImportantVariables(oldVars, newVars);
    
    runtimeService.startCase(caseId, workflowJson, newVars, null);
}
```

---

### Issue 18: Orphaned workflow cases

**Symptoms**:
```
Workflows in STARTED state
No activity for hours/days
Not pended in any work basket
```

**Root Cause**: JVM crash during execution, cases not properly sanitized

**Diagnosis**:
```java
public List<String> findOrphanedCases() {
    List<String> orphaned = new ArrayList<>();
    long threshold = System.currentTimeMillis() - (60 * 60 * 1000); // 1 hour
    
    List<WorkflowInfo> allCases = dao.getAll(WorkflowInfo.class);
    
    for (WorkflowInfo info : allCases) {
        if (!info.getIsComplete() && 
            info.getPendExecPath().isEmpty() &&
            info.getTimestamp() < threshold) {
            
            // Check if any exec path is STARTED
            boolean hasStartedPath = info.getExecPaths().stream()
                .anyMatch(p -> p.getStatus() == ExecPathStatus.STARTED);
            
            if (hasStartedPath) {
                orphaned.add(info.getCaseId());
            }
        }
    }
    
    return orphaned;
}
```

**Solution**: Automatic recovery job

```java
@Scheduled(fixedDelay = 300000) // Every 5 minutes
public void recoverOrphanedCases() {
    List<String> orphaned = findOrphanedCases();
    
    log.info("Found {} orphaned cases", orphaned.size());
    
    for (String caseId : orphaned) {
        try {
            log.info("Recovering orphaned case: {}", caseId);
            runtimeService.resumeCase(caseId);
            log.info("Successfully recovered case: {}", caseId);
        } catch (Exception e) {
            log.error("Failed to recover case {}: {}", caseId, e.getMessage());
            alertMonitoring(caseId, e);
        }
    }
}
```

---

## Performance Issues

### Issue 19: Slow workflow execution

**Symptoms**:
```
Workflows take too long
High latency between steps
Poor throughput
```

**Diagnosis**:
```java
// Add timing logs
public class TimedStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        long start = System.currentTimeMillis();
        
        try {
            // Your logic
            performOperation();
            
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("Step {} took {}ms", getClass().getSimpleName(), duration);
            
            if (duration > 1000) {
                log.warn("Slow step detected: {}ms", duration);
            }
        }
    }
}

// Check persistence timing
long start = System.currentTimeMillis();
dao.save("workflow_process_info-" + caseId, info);
long duration = System.currentTimeMillis() - start;
System.out.println("Persistence took: " + duration + "ms");
```

**Solution 1**: Optimize database queries

```java
// ❌ Slow - N+1 query problem
public void processOrders() {
    List<String> orderIds = getOrderIds();
    for (String orderId : orderIds) {
        Order order = dao.get(Order.class, "order-" + orderId);  // N queries!
        processOrder(order);
    }
}

// ✅ Fast - batch query
public void processOrders() {
    List<String> orderIds = getOrderIds();
    List<Order> orders = dao.getAll(Order.class).stream()
        .filter(o -> orderIds.contains(o.getOrderId()))
        .collect(Collectors.toList());
    
    for (Order order : orders) {
        processOrder(order);
    }
}
```

**Solution 2**: Use lazy persistence for fast steps

```java
// For fast, idempotent steps
WorkflowService.instance().setWriteProcessInfoAfterEachStep(false);
```

**Solution 3**: Optimize external service calls

```java
// ❌ Slow - sequential calls
public void validateOrder() {
    validateCustomer();      // 200ms
    validateInventory();     // 300ms
    validatePayment();       // 250ms
    // Total: 750ms
}

// ✅ Fast - parallel calls
public void validateOrder() {
    CompletableFuture<Boolean> customerFuture = 
        CompletableFuture.supplyAsync(() -> validateCustomer());
    CompletableFuture<Boolean> inventoryFuture = 
        CompletableFuture.supplyAsync(() -> validateInventory());
    CompletableFuture<Boolean> paymentFuture = 
        CompletableFuture.supplyAsync(() -> validatePayment());
    
    CompletableFuture.allOf(customerFuture, inventoryFuture, paymentFuture).join();
    // Total: ~300ms (longest operation)
}
```

**Solution 4**: Add connection pooling

```java
// Configure HikariCP
HikariConfig config = new HikariConfig();
config.setMaximumPoolSize(20);
config.setMinimumIdle(5);
config.setConnectionTimeout(30000);

// Enable statement caching
config.addDataSourceProperty("cachePrepStmts", "true");
config.addDataSourceProperty("prepStmtCacheSize", "250");
```

---

### Issue 20: Memory leaks

**Symptoms**:
```
OutOfMemoryError after running for hours
Heap usage constantly growing
Garbage collection taking long time
```

**Diagnosis**:
```bash
# Take heap dump
jmap -dump:live,format=b,file=heap.bin <pid>

# Analyze with tools like Eclipse MAT or VisualVM

# Check for common issues:
# - Process variables holding large objects
# - Static collections growing unbounded
# - Workflow info not cleaned up
```

**Solution 1**: Clean up completed workflows

```java
@Scheduled(fixedDelay = 3600000) // Every hour
public void cleanupCompletedWorkflows() {
    List<WorkflowInfo> completed = dao.getAll(WorkflowInfo.class).stream()
        .filter(WorkflowInfo::getIsComplete)
        .filter(info -> info.getTimestamp() < getRetentionThreshold())
        .collect(Collectors.toList());
    
    log.info("Cleaning up {} completed workflows", completed.size());
    
    for (WorkflowInfo info : completed) {
        // Archive to cold storage if needed
        archiveWorkflow(info);
        
        // Delete from active storage
        dao.delete("workflow_process_info-" + info.getCaseId());
        dao.delete("journey-" + info.getCaseId());
    }
}

private long getRetentionThreshold() {
    // Keep completed workflows for 30 days
    return System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
}
```

**Solution 2**: Limit process variable size

```java
public class ProcessVariableSizeValidator {
    
    private static final int MAX_STRING_LENGTH = 10000;
    private static final int MAX_LIST_SIZE = 1000;
    
    public void validate(WorkflowVariables vars) {
        for (ProcessVariableValue var : vars.getVariables()) {
            if (var.getType() == WorkflowVariableType.STRING) {
                String value = var.getValueString();
                if (value != null && value.length() > MAX_STRING_LENGTH) {
                    throw new IllegalArgumentException(
                        "Variable " + var.getName() + " exceeds max length: " + value.length()
                    );
                }
            }
            
            if (var.getType() == WorkflowVariableType.LIST_OF_OBJECT) {
                @SuppressWarnings("unchecked")
                List<?> list = (List<?>) var.getValueObject();
                if (list != null && list.size() > MAX_LIST_SIZE) {
                    throw new IllegalArgumentException(
                        "Variable " + var.getName() + " list exceeds max size: " + list.size()
                    );
                }
            }
        }
    }
}
```

**Solution 3**: Use references instead of embedding

```java
// ❌ Bad - stores entire object in process variables
vars.setValue("order_data", WorkflowVariableType.OBJECT, largeOrderObject);

// ✅ Good - stores reference only
vars.setValue("order_id", WorkflowVariableType.STRING, orderId);

// Retrieve when needed
Order order = orderRepository.findById(orderId);
```

---

### Issue 21: High CPU usage

**Symptoms**:
```
CPU constantly at 100%
Application slow
Workflows taking long time
```

**Diagnosis**:
```bash
# Take thread dump
jstack <pid> > thread-dump.txt

# Look for:
# - Busy loops
# - Stuck threads
# - Deadlocks

# Check workflow logs for:
# - Infinite loops in workflow definition
# - Busy-wait patterns
# - Excessive polling
```

**Solution 1**: Fix infinite loops

```java
// ❌ Bad - busy wait
public TaskResponse executeStep() {
    while (!isReady()) {
        // Busy loop - burns CPU!
    }
    return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
}

// ✅ Good - pend and wait
public TaskResponse executeStep() {
    if (!isReady()) {
        return new TaskResponse(
            StepResponseType.OK_PEND_EOR,
            "Waiting for condition",
            "wait_queue"
        );
    }
    return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
}
```

**Solution 2**: Reduce polling frequency

```java
// ❌ Bad - polls every second
@Scheduled(fixedDelay = 1000)
public void checkWorkQueue() {
    // Check and process
}

// ✅ Good - polls less frequently
@Scheduled(fixedDelay = 10000)  // Every 10 seconds
public void checkWorkQueue() {
    // Check and process
}

// ✅ Better - use event-driven approach
public void onWorkItemAdded(WorkItem item) {
    runtimeService.resumeCase(item.getCaseId());
}
```

---

## Integration Issues

### Issue 22: External service timeouts

**Symptoms**:
```
SocketTimeoutException
Read timed out
Workflows fail intermittently
```

**Diagnosis**:
```java
// Add timeout logging
public class TimedServiceCall {
    public String callExternalService() {
        long start = System.currentTimeMillis();
        try {
            return externalService.call();
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("External service call took {}ms", duration);
            
            if (duration > 5000) {
                log.warn("Slow external service: {}ms", duration);
            }
        }
    }
}
```

**Solution 1**: Configure appropriate timeouts

```java
// HTTP client configuration
HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create(url))
    .timeout(Duration.ofSeconds(30))  // Read timeout
    .build();

// Or RestTemplate
RestTemplate restTemplate = new RestTemplate();
HttpComponentsClientHttpRequestFactory factory = 
    new HttpComponentsClientHttpRequestFactory();
factory.setConnectTimeout(10000);
factory.setReadTimeout(30000);
restTemplate.setRequestFactory(factory);
```

**Solution 2**: Implement retry with backoff

```java
public class RetryableServiceCall implements InvokableTask {
    
    private static final int MAX_RETRIES = 3;
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        Integer retryCount = vars.getInteger("service_retry_count");
        if (retryCount == null) retryCount = 0;
        
        try {
            String result = externalService.call();
            
            // Success - clear retry count
            vars.setValue("service_retry_count", WorkflowVariableType.INTEGER, null);
            vars.setValue("service_result", WorkflowVariableType.STRING, result);
            
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (TimeoutException e) {
            retryCount++;
            vars.setValue("service_retry_count", WorkflowVariableType.INTEGER, retryCount);
            
            if (retryCount >= MAX_RETRIES) {
                log.error("Max retries exceeded");
                return new TaskResponse(
                    StepResponseType.ERROR_PEND,
                    "Service timeout after " + MAX_RETRIES + " retries",
                    "service_error_queue"
                );
            }
            
            // Calculate backoff
            long delaySeconds = (long) Math.pow(2, retryCount - 1);
            log.info("Retry {} of {} after {}s", retryCount, MAX_RETRIES, delaySeconds);
            
            return new TaskResponse(
                StepResponseType.OK_PEND_EOR,
                "Retrying after timeout",
                "retry_queue"
            );
        }
    }
}
```

**Solution 3**: Use circuit breaker

```java
// See Error Handling Patterns documentation for complete implementation
if (circuitBreakerOpen()) {
    return new TaskResponse(
        StepResponseType.ERROR_PEND,
        "Circuit breaker open - service unavailable",
        "circuit_breaker_queue"
    );
}
```

---

### Issue 23: Serialization errors with complex objects

**Symptoms**:
```
NotSerializableException
Cannot store object in process variables
ClassNotFoundException on deserialization
```

**Diagnosis**:
```java
// Test if object is serializable
public boolean isSerializable(Object obj) {
    try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return true;
    } catch (Exception e) {
        System.out.println("Not serializable: " + e.getMessage());
        return false;
    }
}
```

**Solution 1**: Make class serializable

```java
// ❌ Not serializable
public class OrderData {
    private String orderId;
    private List<Item> items;
}

// ✅ Serializable
public class OrderData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String orderId;
    private List<Item> items;  // Item must also be Serializable
}

public class Item implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String sku;
    private int quantity;
}
```

**Solution 2**: Use DTO instead of entity

```java
// ❌ Don't store JPA entities
@Entity
public class Order {
    @OneToMany
    private List<OrderItem> items;  // May not be serializable
}

// ✅ Use simple DTO
public class OrderDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String orderId;
    private List<String> itemSkus;
    private double totalAmount;
}

// Convert entity to DTO
OrderDTO dto = new OrderDTO(
    order.getOrderId(),
    order.getItems().stream().map(Item::getSku).collect(Collectors.toList()),
    order.getTotalAmount()
);

vars.setValue("order_data", WorkflowVariableType.OBJECT, dto);
```

**Solution 3**: Store ID reference instead

```java
// ✅ Best - store reference only
vars.setValue("order_id", WorkflowVariableType.STRING, orderId);

// Retrieve when needed
Order order = orderRepository.findById(orderId);
```

---

## Development Issues

### Issue 24: "Cannot find symbol" compilation errors

**Symptoms**:
```
Cannot find symbol: class InvokableTask
Cannot find symbol: variable context
Package com.anode.workflow does not exist
```

**Root Cause**: Workflow dependency not in classpath

**Solution**: Add workflow dependency

```xml
<!-- Maven -->
<dependency>
    <groupId>com.anode</groupId>
    <artifactId>workflow</artifactId>
    <version>0.0.1</version>
</dependency>
```

```gradle
// Gradle
implementation 'com.anode:workflow:0.0.1'
```

---

### Issue 25: Context not accessible in step

**Symptoms**:
```
NullPointerException when accessing context
context.getCaseId() returns null
```

**Root Cause**: Context not properly initialized or passed

**Solution**:
```java
// ✅ Correct pattern
public class MyStep implements InvokableTask {
    
    private final WorkflowContext context;
    
    // Constructor receives context
    public MyStep(WorkflowContext context) {
        this.context = context;
    }
    
    // Implement getter
    @Override
    public WorkflowContext getContext() {
        return context;
    }
    
    @Override
    public TaskResponse executeStep() {
        // Context is available
        String caseId = context.getCaseId();
        WorkflowVariables vars = context.getProcessVariables();
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}

// Component factory must pass context
public class MyFactory implements WorkflowComponantFactory {
    @Override
    public Object getComponentInstance(String componentName, WorkflowContext context) {
        if ("my_step".equals(componentName)) {
            return new MyStep(context);  // Pass context here
        }
        throw new IllegalArgumentException("Unknown component: " + componentName);
    }
}
```

---

### Issue 26: Testing workflow steps

**Symptoms**:
```
Don't know how to create WorkflowContext for tests
Mock setup is complex
Tests fail with NullPointerException
```

**Solution**: Use test utilities

```java
public class MyStepTest {
    
    private WorkflowContext context;
    private MyStep step;
    
    @Before
    public void setup() {
        // Create test context
        context = TestManager.createTestContext("test-case-1");
        
        // Initialize process variables
        WorkflowVariables vars = context.getProcessVariables();
        vars.setValue("order_id", WorkflowVariableType.STRING, "ORDER-123");
        vars.setValue("amount", WorkflowVariableType.DOUBLE, 99.99);
        
        // Create step
        step = new MyStep(context);
    }
    
    @Test
    public void testStepExecution() {
        // Execute
        TaskResponse response = step.executeStep();
        
        // Assert
        assertEquals(StepResponseType.OK_PROCEED, response.getUnitResponseType());
        
        // Check process variables
        String result = context.getProcessVariables().getString("result");
        assertEquals("success", result);
    }
    
    @Test
    public void testStepWithMocks() {
        // Mock external service
        ExternalService mockService = mock(ExternalService.class);
        when(mockService.process(anyString())).thenReturn("OK");
        
        MyStep step = new MyStep(context, mockService);
        
        // Execute
        TaskResponse response = step.executeStep();
        
        // Verify
        verify(mockService, times(1)).process("ORDER-123");
        assertEquals(StepResponseType.OK_PROCEED, response.getUnitResponseType());
    }
}
```

---

## Diagnostic Tools

### Workflow Inspector

```java
public class WorkflowInspector {
    
    public void inspect(String caseId, CommonService dao) {
        System.out.println("=== Workflow Inspection: " + caseId + " ===\n");
        
        // Load workflow info
        WorkflowInfo info = dao.get(WorkflowInfo.class, "workflow_process_info-" + caseId);
        if (info == null) {
            System.out.println("ERROR: Workflow info not found");
            return;
        }
        
        // Basic info
        System.out.println("Case ID: " + info.getCaseId());
        System.out.println("Complete: " + info.getIsComplete());
        System.out.println("Pend Path: " + info.getPendExecPath());
        System.out.println("Last Step: " + info.getLastExecutedStep());
        System.out.println("Timestamp: " + new Date(info.getTimestamp()));
        System.out.println("Ticket: " + info.getTicket());
        
        // Execution paths
        System.out.println("\n--- Execution Paths ---");
        for (ExecPath path : info.getExecPaths()) {
            System.out.println("Path: " + path.getName());
            System.out.println("  Status: " + path.getStatus());
            System.out.println("  Step: " + path.getStep());
            System.out.println("  Response: " + path.getStepResponseType());
            System.out.println("  Workbasket: " + path.getPendWorkBasket());
            if (path.getErrorCode() != null && path.getErrorCode() != 0) {
                System.out.println("  ERROR: " + path.getErrorCode() + " - " + path.getErrorDesc());
            }
            System.out.println();
        }
        
        // Process variables
        System.out.println("--- Process Variables ---");
        for (ProcessVariableValue var : info.getProcessVariables().getVariables()) {
            System.out.println(var.getName() + " (" + var.getType() + "): " + getValueAsString(var));
        }
        
        // Workflow definition
        System.out.println("\n--- Workflow Definition ---");
        WorkflowDefinition def = dao.get(WorkflowDefinition.class, "journey-" + caseId);
        if (def != null) {
            System.out.println("Name: " + def.getDefName());
            System.out.println("Version: " + def.getDefVersion());
            System.out.println("Steps: " + def.getFlow().size());
        } else {
            System.out.println("WARNING: Workflow definition not found");
        }
    }
    
    private String getValueAsString(ProcessVariableValue var) {
        switch (var.getType()) {
            case STRING:
                return var.getValueString();
            case LONG:
                return String.valueOf(var.getValueLong());
            case INTEGER:
                return String.valueOf(var.getValueInteger());
            case DOUBLE:
                return String.valueOf(var.getValueDouble());
            case BOOLEAN:
                return String.valueOf(var.getValueBoolean());
            default:
                return var.getValueObject() != null ? var.getValueObject().toString() : "null";
        }
    }
}
```

### Usage:
```java
WorkflowInspector inspector = new WorkflowInspector();
inspector.inspect("order-123", dao);
```

---

## Summary Checklist

### Startup Issues
- [ ] WorkflowService.init() called before use
- [ ] Component factory properly implemented
- [ ] Case IDs don't contain separator character
- [ ] Thread pool sized appropriately

### Execution Issues
- [ ] Workflow definition valid (start, end, valid next steps)
- [ ] Steps return non-null TaskResponse
- [ ] No circular references in flow
- [ ] Steps configured with correct "next" values

### Parallel Processing
- [ ] Routes return branch names
- [ ] Branch names don't contain dots
- [ ] Synchronized access to shared state
- [ ] All branches complete or pend

### Persistence
- [ ] Process variables set correctly
- [ ] Database connection working
- [ ] Aggressive persistence for critical steps
- [ ] File writes are atomic

### Crash Recovery
- [ ] Steps are idempotent
- [ ] Execution flags checked before operations
- [ ] Orphaned cases detected and recovered
- [ ] Workflow info not corrupted

### Performance
- [ ] Database queries optimized
- [ ] Connection pooling configured
- [ ] External service calls efficient
- [ ] Completed workflows cleaned up

### Integration
- [ ] Timeouts configured appropriately
- [ ] Retry logic implemented
- [ ] Circuit breakers for external services
- [ ] Objects are serializable

### Development
- [ ] Dependencies added to project
- [ ] Context properly passed to components
- [ ] Tests use TestManager utilities
- [ ] Error handling implemented

---

## Getting Help

If you encounter issues not covered in this guide:

1. **Enable Debug Logging**:
   ```properties
   logging.level.com.anode.workflow=DEBUG
   ```

2. **Use Workflow Inspector**: Run diagnostic tool on affected cases

3. **Check Logs**: Look for exceptions, warnings, timing information

4. **Review Documentation**:
   - [Execution Paths](../concepts/execution-paths.md)
   - [Crash Recovery](../concepts/crash-recovery.md)
   - [Idempotency Patterns](../patterns/idempotency.md)
   - [Error Handling](../patterns/error-handling.md)

5. **Community Support**: GitHub Issues, Discussion Forums

---

**Remember**: Most workflow issues are related to:
- Configuration (30%)
- Idempotency (25%)
- Workflow definition errors (20%)
- External service integration (15%)
- Performance/resource limits (10%)

Start with the most common issues first!

- **SLA Management Deep Dive**
- **Work Basket Management**
- **Testing Guide**
- **Contributing Guide**
- **Deployment Guide**