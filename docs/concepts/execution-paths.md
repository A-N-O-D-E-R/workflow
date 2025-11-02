# Understanding Execution Paths - Complete Guide

## Overview

Execution paths are the foundation of Simple Workflow's ability to manage both sequential and parallel workflow execution. This guide provides everything you need to understand, debug, and optimize execution path behavior.

## Table of Contents
- [Core Concepts](#core-concepts)
- [Naming Convention and Structure](#naming-convention-and-structure)
- [Lifecycle Management](#lifecycle-management)
- [Parallel Processing Deep Dive](#parallel-processing-deep-dive)
- [Path Coordination and Synchronization](#path-coordination-and-synchronization)
- [Advanced Patterns](#advanced-patterns)
- [Debugging and Troubleshooting](#debugging-and-troubleshooting)
- [Performance Considerations](#performance-considerations)
- [Real-World Examples](#real-world-examples)

## Core Concepts

### What is an Execution Path?

An execution path represents a **thread of execution** within a workflow. Think of it as a "thread context" that tracks:
- Where in the workflow this thread is executing
- What step it's currently on
- Whether it has completed or is pending
- Any errors that occurred
- Which work basket it's assigned to (if pended)

```
Single Sequential Flow:
┌─────────────────────────────────────┐
│  Execution Path: "."                │
│  ┌─────┐   ┌─────┐   ┌─────┐        │
│  │Step1│──▶│Step2│──▶│Step3│──▶End  │
│  └─────┘   └─────┘   └─────┘        │
└─────────────────────────────────────┘

Parallel Flow:
┌─────────────────────────────────────────────┐
│  Root Path: "."                             │
│  ┌─────┐    ┌─────────────────┐             │
│  │Start│───▶│ Parallel Route  │             │
│  └─────┘    └────┬──────┬─────┘             │
│                  │      │                   │
│    ┌─────────────┘      └─────────────┐     │
│    │                                  │     │
│    ▼ .route_1.1.                      ▼ .route_1.2.
│  ┌─────┐                            ┌─────┐
│  │Step2│                            │Step3│
│  └─────┘                            └─────┘
│    │                                   │    │
│    └─────────────┬──────┬──────────────┘    │
│                  │ Join │                   │
│                  ▼      ▼                   │
│                ┌─────────┐                  │
│                │  Step4  │                  │
│                └─────────┘                  │
└─────────────────────────────────────────────┘
```

### Key Characteristics

1. **Root Path Always Exists**: Every workflow starts with the root execution path named `.`
2. **Hierarchical**: Child paths inherit their parent's context
3. **Independent**: Each path executes independently (true parallel processing)
4. **Synchronized**: Paths synchronize at join points
5. **Persistent**: Path state is saved for crash recovery

### ExecPath Entity Structure

```java
public class ExecPath {
    private String name;                      // e.g., ".route_1.1."
    private ExecPathStatus status;            // STARTED or COMPLETED
    private String step;                      // Current/last step name
    private StepResponseType unitResponseType; // How step responded
    private String pendWorkBasket;            // Where pended (if any)
    private String prevPendWorkBasket;        // Previous basket
    private Integer errorCode;                // Error code if failed
    private String errorDesc;                 // Error description
    
    // Methods for path relationships
    public String getParentExecPathName();
    public boolean isSibling(ExecPath other);
    public boolean isChildOf(String parentPath);
}
```

## Naming Convention and Structure

### Naming Rules

The execution path name follows a strict pattern:

```
.<route_name>.<branch_name>.<route_name>.<branch_name>...
```

**Absolute Rules**:
1. Always starts with `.` (dot)
2. Always ends with `.` (dot)
3. Route name and branch name are separated by `.`
4. Multiple levels are dot-separated
5. Names are case-sensitive
6. **NO DOTS ALLOWED** in route or branch names

### Examples with Explanation

#### Example 1: Root Path
```
Name: "."
Depth: 1
Meaning: The main thread of execution (root)
```

#### Example 2: First Level Parallel
```json
{
  "name": "parallel_order_processing",
  "type": "p_route",
  "branches": [
    {"name": "inventory", "next": "check_inventory"},
    {"name": "payment", "next": "process_payment"},
    {"name": "shipping", "next": "calculate_shipping"}
  ]
}
```

**Resulting Paths**:
```
.                                          (root - creates children)
.parallel_order_processing.inventory.     (inventory branch)
.parallel_order_processing.payment.       (payment branch)
.parallel_order_processing.shipping.      (shipping branch)
```

#### Example 3: Nested Parallel Routes
```json
{
  "flow": [
    {
      "name": "regional_processing",
      "type": "p_route",
      "branches": [
        {"name": "north_america", "next": "na_route"},
        {"name": "europe", "next": "eu_route"}
      ]
    },
    {
      "name": "na_route",
      "type": "p_route",
      "branches": [
        {"name": "usa", "next": "process_usa"},
        {"name": "canada", "next": "process_canada"}
      ]
    }
  ]
}
```

**Resulting Paths**:
```
.                                                      (root)
├─ .regional_processing.north_america.                (NA branch)
│  ├─ .regional_processing.north_america.na_route.usa.      (USA sub-branch)
│  └─ .regional_processing.north_america.na_route.canada.   (Canada sub-branch)
└─ .regional_processing.europe.                       (Europe branch)
```

### Depth Calculation

Depth is the number of dots in the path name:

```java
int depth = StringUtils.getCount(execPathName, '.');

// Examples:
"."                                          → depth = 1
".route_1.1."                                → depth = 3
".route_1.1.route_2.1."                      → depth = 5
".route_1.A.route_2.X.route_3.1."           → depth = 7
```

**Why Depth Matters**:
- Used to select the "deepest" path when multiple paths pend
- Determines parent-child relationships
- Affects join synchronization logic

### Parent-Child Relationships

```java
// Get parent path name
public String getParentExecPathName() {
    if (name.equals(".")) {
        return ""; // Root has no parent
    }
    
    int lastDot = name.lastIndexOf(".", name.length() - 2);
    if (lastDot == -1) {
        return "."; // Parent is root
    }
    
    return name.substring(0, lastDot + 1);
}

// Examples:
".route_1.A.route_2.X." → parent is ".route_1.A."
".route_1.A."           → parent is "."
"."                     → parent is "" (none)
```

### Sibling Detection

Siblings share the same parent and depth:

```java
public boolean isSibling(ExecPath other) {
    // Must have same depth
    int thisDepth = StringUtils.getCount(this.name, '.');
    int otherDepth = StringUtils.getCount(other.getName(), '.');
    if (thisDepth != otherDepth) {
        return false;
    }
    
    // Must have same parent
    return this.getParentExecPathName()
               .equals(other.getParentExecPathName());
}

// Examples:
".route_1.A." and ".route_1.B." → siblings (same parent ".", same depth 3)
".route_1.A." and ".route_2.A." → NOT siblings (different parents)
".route_1.A." and ".route_1.A.route_2.1." → NOT siblings (different depths)
```

## Lifecycle Management

### State Machine

Each execution path follows this state machine:

```
     ┌──────────────┐
     │   Created    │ (Implicit - not persisted initially)
     └──────┬───────┘
            │
            ▼
     ┌──────────────┐
     │   STARTED    │ ◄─────┐
     └──────┬───────┘       │
            │               │
            ├─ OK_PROCEED ──┘ (Continue to next step)
            │
            ├─ OK_PEND ───────► (Pause, resume from next step)
            │
            ├─ OK_PEND_EOR ───► (Pause, resume from same step)
            │
            ├─ ERROR_PEND ────► (Pause with error)
            │
            ▼
     ┌──────────────┐
     │  COMPLETED   │
     └──────────────┘
```

### Creation

Execution paths are created in two scenarios:

**1. Case Start** - Root path created:
```java
// When workflow starts
ExecPath rootPath = new ExecPath(".");
rootPath.setStatus(ExecPathStatus.STARTED);
rootPath.setStep("start");
```

**2. Parallel Route** - Child paths created:
```java
// When parallel route encountered
for (String branchName : route.getBranches()) {
    String childPathName = parentPath + route.getName() + "." + branchName + ".";
    ExecPath childPath = new ExecPath(childPathName);
    childPath.setStatus(ExecPathStatus.STARTED);
    childPath.setStep(branch.getNextStep());
}
```

### Execution

During execution, the path tracks its progress:

```java
// Step execution
execPath.setStep("process_payment");
execPath.setUnitResponseType(StepResponseType.OK_PROCEED);

// If step pends
execPath.setPendWorkBasket("payment_review_queue");
execPath.setUnitResponseType(StepResponseType.OK_PEND);

// If error occurs
execPath.setErrorCode(1001);
execPath.setErrorDesc("Payment service unavailable");
execPath.setPendWorkBasket("error_queue");
execPath.setUnitResponseType(StepResponseType.ERROR_PEND);
```

### Completion

A path completes when:

**1. Reaches End Step**:
```java
if (currentStep.getName().equals("end")) {
    execPath.setStatus(ExecPathStatus.COMPLETED);
}
```

**2. Reaches Join Point** (parallel paths):
```java
if (currentStep.getName().equals(route.getJoinName())) {
    execPath.setStatus(ExecPathStatus.COMPLETED);
}
```

**3. Raises Ticket** (transfers control):
```java
execPath.setStatus(ExecPathStatus.COMPLETED);
workflowInfo.setTicket(ticketDestination);
```

### Detailed State Transitions

```java
// Example: Complete step execution flow
public void executeStep(ExecPath execPath, Step step) {
    // 1. Mark as executing
    execPath.setStep(step.getName());
    
    // 2. Execute step
    TaskResponse response = invokeStep(step);
    
    // 3. Update based on response
    switch (response.getUnitResponseType()) {
        case OK_PROCEED:
            // Continue to next step
            execPath.setUnitResponseType(StepResponseType.OK_PROCEED);
            execPath.setStep(step.getNext());
            break;
            
        case OK_PEND:
            // Pend and resume from next step
            execPath.setUnitResponseType(StepResponseType.OK_PEND);
            execPath.setPendWorkBasket(response.getWorkbasket());
            execPath.setStep(step.getNext());
            break;
            
        case OK_PEND_EOR:
            // Pend and resume from same step (re-execute)
            execPath.setUnitResponseType(StepResponseType.OK_PEND_EOR);
            execPath.setPendWorkBasket(response.getWorkbasket());
            // step remains the same
            break;
            
        case ERROR_PEND:
            // Error occurred, pend for manual intervention
            execPath.setUnitResponseType(StepResponseType.ERROR_PEND);
            execPath.setPendWorkBasket(response.getWorkbasket());
            execPath.setErrorCode(response.getErrorCode());
            execPath.setErrorDesc(response.getErrorDesc());
            break;
    }
    
    // 4. Persist state (for crash recovery)
    persistWorkflowInfo();
}
```

## Parallel Processing Deep Dive

### How Parallel Routes Work

When the workflow engine encounters a parallel route:

```java
public void executeParallelRoute(Route route, ExecPath parentPath) {
    List<String> branches = route.getBranches();
    
    // 1. Create child execution paths
    List<ExecPath> childPaths = new ArrayList<>();
    for (String branchName : branches) {
        String childName = parentPath.getName() + 
                          route.getName() + "." + 
                          branchName + ".";
        
        ExecPath childPath = new ExecPath(childName);
        childPath.setStatus(ExecPathStatus.STARTED);
        childPaths.add(childPath);
    }
    
    // 2. Spawn threads for each branch
    List<Future<?>> futures = new ArrayList<>();
    for (ExecPath childPath : childPaths) {
        Future<?> future = executorService.submit(() -> {
            executeFlow(childPath, workflow);
        });
        futures.add(future);
    }
    
    // 3. Wait for all branches to complete or pend
    waitForChildren(futures, timeout);
    
    // 4. Check if all children completed
    boolean allCompleted = childPaths.stream()
        .allMatch(p -> p.getStatus() == ExecPathStatus.COMPLETED);
    
    if (allCompleted) {
        // 5. Continue with parent path after join
        parentPath.setStep(route.getJoinName());
        continueExecution(parentPath);
    } else {
        // 6. At least one child pended - parent waits at join
        parentPath.setStep(route.getJoinName());
        parentPath.setStatus(ExecPathStatus.STARTED);
        // Will resume when all children complete
    }
}
```

### Thread Management

```java
// Initialize thread pool
ExecutorService executorService = Executors.newFixedThreadPool(
    maxThreads  // Configured in WorkflowService.init()
);

// Each branch gets its own thread
Future<?> future = executorService.submit(new Runnable() {
    @Override
    public void run() {
        try {
            // Execute this branch's flow
            executeFlow(childExecPath, workflowDef);
        } catch (Exception e) {
            log.error("Branch execution failed", e);
            // Mark path with error
            childExecPath.setErrorCode(-1);
            childExecPath.setErrorDesc(e.getMessage());
        }
    }
});
```

### Join Point Synchronization

The join point is where parallel branches synchronize:

```
Branch 1: .route_1.A.
    ├─ step_a1
    ├─ step_a2
    └─ [reaches join_1]  ← waits here

Branch 2: .route_1.B.
    ├─ step_b1
    ├─ step_b2  [PENDS]  ← still working
    
Branch 3: .route_1.C.
    ├─ step_c1
    └─ [reaches join_1]  ← waits here

─────────────────────────────────────
Join Point: join_1
    Status: WAITING (Branch 2 not done)
```

**Join Logic**:
```java
public boolean canProceedPastJoin(Route route, List<ExecPath> allPaths) {
    // Get all sibling paths for this route
    List<ExecPath> siblings = allPaths.stream()
        .filter(p -> p.getName().startsWith(parentPath + route.getName()))
        .filter(p -> isBranchOfRoute(p, route))
        .collect(Collectors.toList());
    
    // Check if all siblings completed or pended
    boolean allDone = siblings.stream().allMatch(p -> 
        p.getStatus() == ExecPathStatus.COMPLETED ||
        !p.getPendWorkBasket().isEmpty()
    );
    
    if (!allDone) {
        // Not all branches finished - wait at join
        return false;
    }
    
    // Check if any sibling pended
    boolean anyPended = siblings.stream()
        .anyMatch(p -> !p.getPendWorkBasket().isEmpty());
    
    if (anyPended) {
        // At least one branch pended - entire route pends
        return false;
    }
    
    // All completed successfully - proceed past join
    return true;
}
```

### Example: Parallel Execution Timeline

```
Time 0ms: Start workflow (case-123)
  │
  ├─ Create root path: "."
  │
Time 10ms: Reach parallel route "route_1"
  │
  ├─ Create child paths:
  │  ├─ ".route_1.branch_a."
  │  ├─ ".route_1.branch_b."
  │  └─ ".route_1.branch_c."
  │
  ├─ Spawn 3 threads
  │
Time 15ms: Thread 1 executing ".route_1.branch_a."
Time 15ms: Thread 2 executing ".route_1.branch_b."
Time 15ms: Thread 3 executing ".route_1.branch_c."
  │
Time 100ms: Thread 1 completes branch_a (reached join)
  │         ├─ ExecPath status: COMPLETED
  │         └─ Waiting at join for others
  │
Time 150ms: Thread 3 completes branch_c (reached join)
  │         ├─ ExecPath status: COMPLETED
  │         └─ Waiting at join for others
  │
Time 200ms: Thread 2 pends branch_b (user review needed)
  │         ├─ ExecPath status: STARTED
  │         ├─ Workbasket: "manager_review"
  │         └─ Response: OK_PEND
  │
Time 201ms: Join synchronization check
  │         ├─ Branch A: COMPLETED ✓
  │         ├─ Branch B: PENDED ✗
  │         ├─ Branch C: COMPLETED ✓
  │         └─ Result: Cannot proceed past join
  │
Time 202ms: Root path "." waits at join_1
  │         ├─ Step: "join_1"
  │         ├─ Status: STARTED
  │         └─ Workflow PENDS
  │
─────────── WORKFLOW PAUSED ───────────
  │
Time 3600000ms: User completes review (1 hour later)
  │
Time 3600001ms: Resume case-123
  │
Time 3600010ms: Thread 2 resumes ".route_1.branch_b."
  │
Time 3600100ms: Thread 2 completes branch_b (reached join)
  │            ├─ ExecPath status: COMPLETED
  │            └─ All branches now complete
  │
Time 3600101ms: Join synchronization check
  │            ├─ Branch A: COMPLETED ✓
  │            ├─ Branch B: COMPLETED ✓
  │            ├─ Branch C: COMPLETED ✓
  │            └─ Result: Proceed past join
  │
Time 3600105ms: Root path "." continues from join_1
  │            └─ Execute next step after join
  │
Time 3600200ms: Workflow completes
```

## Path Coordination and Synchronization

### Selecting the Pend Path

When multiple paths pend, the workflow must choose one as the "official" pend path:

```java
public String selectPendExecPath(List<ExecPath> execPaths) {
    String pendPath = "";
    int maxDepth = 0;
    
    for (ExecPath path : execPaths) {
        String workbasket = path.getPendWorkBasket();
        
        // Only consider paths that actually pended
        if (!workbasket.isEmpty()) {
            int depth = StringUtils.getCount(path.getName(), '.');
            
            // Select deepest path
            if (depth > maxDepth) {
                maxDepth = depth;
                pendPath = path.getName();
            }
        }
    }
    
    return pendPath;
}
```

**Why Deepest?**
- Nested parallel routes can have multiple pend points
- The deepest represents the most specific execution context
- Ensures correct resume point

**Example**:
```
Paths that pended:
├─ ".route_1.A." (depth 3) - pended at step_x
└─ ".route_1.A.route_2.1." (depth 5) - pended at step_y

Selected: ".route_1.A.route_2.1." (deepest)
```

### Process Variables and Coordination

Parallel branches can coordinate using shared process variables:

```java
// Branch 1: Set flag when complete
public class Branch1Step implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        // Do work...
        
        // Signal completion to other branches
        synchronized (context.getProcessVariables()) {
            context.getProcessVariables().setValue(
                "branch1_complete",
                WorkflowVariableType.BOOLEAN,
                true
            );
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}

// Branch 2: Wait for Branch 1
public class Branch2Step implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        Boolean branch1Done = context.getProcessVariables()
            .getBoolean("branch1_complete");
        
        if (branch1Done == null || !branch1Done) {
            // Branch 1 not done yet - pend and check again later
            return new TaskResponse(
                StepResponseType.OK_PEND_EOR,
                "",
                "wait_queue"
            );
        }
        
        // Branch 1 is done - proceed
        // Do work...
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### Critical Section Pattern

Ensure only one branch executes critical code:

```java
public class CriticalSectionStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        synchronized (vars) {
            // Check if already executed by another branch
            Boolean executed = vars.getBoolean("critical_section_executed");
            if (executed != null && executed) {
                log.info("Critical section already executed by another branch");
                return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            }
            
            // Execute critical code
            performCriticalOperation();
            
            // Mark as executed
            vars.setValue(
                "critical_section_executed",
                WorkflowVariableType.BOOLEAN,
                true
            );
        }
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

### Barrier Synchronization Pattern

Wait for all branches to reach a certain point:

```java
public class BarrierStep implements InvokableTask {
    private static final String BARRIER_KEY = "barrier_count";
    private static final int EXPECTED_BRANCHES = 3;
    
    @Override
    public TaskResponse executeStep() {
        WorkflowVariables vars = context.getProcessVariables();
        
        synchronized (vars) {
            // Increment counter
            Integer count = vars.getInteger(BARRIER_KEY);
            count = (count == null) ? 1 : count + 1;
            vars.setValue(BARRIER_KEY, WorkflowVariableType.INTEGER, count);
            
            log.info("Branch {} of {} reached barrier", count, EXPECTED_BRANCHES);
            
            if (count < EXPECTED_BRANCHES) {
                // Not all branches here yet - wait
                return new TaskResponse(
                    StepResponseType.OK_PEND_EOR,
                    "",
                    "barrier_wait"
                );
            }
            
            // All branches reached barrier - proceed
            log.info("All branches reached barrier, proceeding");
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
    }
}
```

## Advanced Patterns

### Dynamic Parallel Routes

Create variable number of branches at runtime:

```java
public class DynamicOrderProcessingRoute implements InvokableRoute {
    
    @Override
    public RouteResponse executeRoute() {
        WorkflowContext context = getContext();
        
        // Get list of items to process
        @SuppressWarnings("unchecked")
        List<String> orderItems = (List<String>) context.getProcessVariables()
            .getValue("order_items", WorkflowVariableType.LIST_OF_OBJECT);
        
        // Create one branch per item
        List<String> branches = new ArrayList<>();
        for (int i = 0; i < orderItems.size(); i++) {
            branches.add("item_" + i);
            
            // Store item data for this branch
            context.getProcessVariables().setValue(
                "item_" + i + "_data",
                WorkflowVariableType.OBJECT,
                orderItems.get(i)
            );
        }
        
        log.info("Creating {} parallel branches for order items", branches.size());
        
        return new RouteResponse(
            StepResponseType.OK_PROCEED,
            branches,
            null
        );
    }
}
```

**Resulting Paths**:
```
If order has 3 items:
.dynamic_route.item_0.
.dynamic_route.item_1.
.dynamic_route.item_2.
```

### Conditional Branching in Parallel Routes

```java
public class ConditionalParallelRoute implements InvokableRoute {
    
    @Override
    public RouteResponse executeRoute() {
        WorkflowContext context = getContext();
        List<String> branches = new ArrayList<>();
        
        // Always process payment
        branches.add("payment");
        
        // Conditional branches
        Boolean isInternational = context.getProcessVariables()
            .getBoolean("is_international");
        if (isInternational != null && isInternational) {
            branches.add("customs");
            branches.add("currency_conversion");
        }
        
        Boolean requiresInsurance = context.getProcessVariables()
            .getBoolean("requires_insurance");
        if (requiresInsurance != null && requiresInsurance) {
            branches.add("insurance");
        }
        
        log.info("Executing {} branches", branches.size());
        
        return new RouteResponse(
            StepResponseType.OK_PROCEED,
            branches,
            null
        );
    }
}
```

### Nested Dynamic Routes

```java
// Level 1: Process each region
public class RegionalRoute implements InvokableRoute {
    @Override
    public RouteResponse executeRoute() {
        List<String> regions = Arrays.asList("north_america", "europe", "asia");
        return new RouteResponse(StepResponseType.OK_PROCEED, regions, null);
    }
}

// Level 2: Within each region, process countries
public class CountryRoute implements InvokableRoute {
    @Override
    public RouteResponse executeRoute() {
        String execPath = getContext().getExecPathName();
        List<String> countries;
        
        if (execPath.contains("north_america")) {
            countries = Arrays.asList("usa", "canada", "mexico");
        } else if (execPath.contains("europe")) {
            countries = Arrays.asList("uk", "germany", "france");
        } else {
            countries = Arrays.asList("japan", "china", "india");
        }
        
        return new RouteResponse(StepResponseType.OK_PROCEED, countries, null);
    }
}
```

**Resulting Path Hierarchy**:
```
.regional.north_america.
├─ .regional.north_america.country.usa.
├─ .regional.north_america.country.canada.
└─ .regional.north_america.country.mexico.

.regional.europe.
├─ .regional.europe.country.uk.
├─ .regional.europe.country.germany.
└─ .regional.europe.country.france.

.regional.asia.
├─ .regional.asia.country.japan.
├─ .regional.asia.country.china.
└─ .regional.asia.country.india.
```

### Ticket Handling in Parallel Paths

```java
public class ParallelStepWithTicket implements InvokableTask {
    
    @Override
    public TaskResponse executeStep() {
        String execPath = getContext().getExecPathName();
        
        // Check if critical error in this branch
        boolean criticalError = checkForError();
        
        if (criticalError) {
            log.error("Critical error in path: {}", execPath);
            
            // Raise ticket to jump to error handler
            // NOTE: Ticket destination must be OUTSIDE parallel construct
            return new TaskResponse(
                StepResponseType.OK_PROCEED,
                "",
                "",
                "error_handler_step"  // Ticket destination
            );
        }
        
        // Normal processing
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

**Important**: When a ticket is raised from a child path:
1. The child path completes immediately
2. Other sibling paths continue executing
3. After all siblings complete/pend, the ticket is processed
4. Control jumps to the ticket destination (must be outside parallel route)

## Debugging and Troubleshooting

### Viewing Execution Paths

#### In Workflow Info JSON

```json
{
  "process_info": {
    "case_id": "order-123",
    "pend_exec_path": ".route_1.branch_b.",
    "exec_paths": [
      {
        "name": ".",
        "status": "started",
        "step": "join_1",
        "unit_response_type": "ok_proceed"
      },
      {
        "name": ".route_1.branch_a.",
        "status": "completed",
        "step": "step_a2",
        "unit_response_type": "ok_proceed",
        "pend_workbasket": ""
      },
      {
        "name": ".route_1.branch_b.",
        "status": "started",
        "step": "step_b1",
        "unit_response_type": "ok_pend",
        "pend_workbasket": "manager_review"
      },
      {
        "name": ".route_1.branch_c.",
        "status": "completed",
        "step": "step_c1",
        "unit_response_type": "ok_proceed",
        "pend_workbasket": ""
      }
    ]
  }
}
```

#### In Console Logs

```
[ExecThreadTask] - Case id -> order-123, executing step -> start, component -> init, execution path -> .
[ExecThreadTask] - Case id -> order-123, executing parallel routing rule -> route_1, execution path -> .
[ExecThreadTask] - Case id -> order-123, executing step -> step_a1, component -> process_a, execution path -> .route_1.branch_a.
[ExecThreadTask] - Case id -> order-123, executing step -> step_b1, component -> process_b, execution path -> .route_1.branch_b.
[ExecThreadTask] - Case id -> order-123, executing step -> step_c1, component -> process_c, execution path -> .route_1.branch_c.
[ExecThreadTask] - Case id -> order-123, executing step -> step_a2, component -> finalize_a, execution path -> .route_1.branch_a.
[ExecThreadTask] - Case id -> order-123, step -> step_b1 returned OK_PEND, execution path -> .route_1.branch_b.
[ExecThreadTask] - Case id -> order-123, executing step -> step_c2, component -> finalize_c, execution path -> .route_1.branch_c.
```

### Debugging Helper Class

```java
public class ExecutionPathDebugger {
    
    public static void printPathHierarchy(WorkflowInfo workflowInfo) {
        System.out.println("Execution Path Hierarchy for case: " + workflowInfo.getCaseId());
        System.out.println("─".repeat(80));
        
        List<ExecPath> paths = workflowInfo.getExecPaths();
        
        // Sort by depth and name
        paths.sort(Comparator
            .comparingInt((ExecPath p) -> StringUtils.getCount(p.getName(), '.'))
            .thenComparing(ExecPath::getName));
        
        for (ExecPath path : paths) {
            int depth = StringUtils.getCount(path.getName(), '.');
            String indent = "  ".repeat(depth - 1);
            String status = path.getStatus() == ExecPathStatus.COMPLETED ? "✓" : "◷";
            String workbasket = path.getPendWorkBasket().isEmpty() 
                ? "" 
                : " [" + path.getPendWorkBasket() + "]";
            
            System.out.printf("%s%s %s (step: %s)%s%n", 
                indent, 
                status, 
                path.getName(), 
                path.getStep(),
                workbasket
            );
        }
        
        System.out.println("─".repeat(80));
        System.out.println("Pend Path: " + workflowInfo.getPendExecPath());
        System.out.println();
    }
    
    public static void validatePathConsistency(WorkflowInfo workflowInfo) {
        List<String> issues = new ArrayList<>();
        List<ExecPath> paths = workflowInfo.getExecPaths();
        
        // Check 1: All paths have valid names
        for (ExecPath path : paths) {
            if (!path.getName().startsWith(".") || !path.getName().endsWith(".")) {
                issues.add("Invalid path name: " + path.getName());
            }
        }
        
        // Check 2: All child paths have parents
        for (ExecPath path : paths) {
            if (!path.getName().equals(".")) {
                String parentName = path.getParentExecPathName();
                boolean hasParent = paths.stream()
                    .anyMatch(p -> p.getName().equals(parentName));
                if (!hasParent) {
                    issues.add("Orphaned path (no parent): " + path.getName());
                }
            }
        }
        
        // Check 3: Pend path exists
        String pendPath = workflowInfo.getPendExecPath();
        if (!pendPath.isEmpty()) {
            boolean exists = paths.stream()
                .anyMatch(p -> p.getName().equals(pendPath));
            if (!exists) {
                issues.add("Pend path not found: " + pendPath);
            }
        }
        
        // Check 4: Only one path should have workbasket if pended
        if (!pendPath.isEmpty()) {
            long pendedCount = paths.stream()
                .filter(p -> !p.getPendWorkBasket().isEmpty())
                .count();
            if (pendedCount > 1) {
                issues.add("Multiple paths have workbaskets: " + pendedCount);
            }
        }
        
        // Report
        if (issues.isEmpty()) {
            System.out.println("✓ Path consistency check passed");
        } else {
            System.out.println("✗ Path consistency issues found:");
            issues.forEach(issue -> System.out.println("  - " + issue));
        }
    }
    
    public static Map<String, Object> getPathStatistics(WorkflowInfo workflowInfo) {
        Map<String, Object> stats = new HashMap<>();
        List<ExecPath> paths = workflowInfo.getExecPaths();
        
        stats.put("total_paths", paths.size());
        stats.put("completed_paths", paths.stream()
            .filter(p -> p.getStatus() == ExecPathStatus.COMPLETED)
            .count());
        stats.put("active_paths", paths.stream()
            .filter(p -> p.getStatus() == ExecPathStatus.STARTED)
            .count());
        stats.put("pended_paths", paths.stream()
            .filter(p -> !p.getPendWorkBasket().isEmpty())
            .count());
        stats.put("max_depth", paths.stream()
            .mapToInt(p -> StringUtils.getCount(p.getName(), '.'))
            .max()
            .orElse(0));
        stats.put("parallel_routes", paths.stream()
            .filter(p -> StringUtils.getCount(p.getName(), '.') > 1)
            .map(ExecPath::getParentExecPathName)
            .distinct()
            .count());
        
        return stats;
    }
}
```

### Usage Example

```java
// Debug workflow state
WorkflowInfo info = dao.get(WorkflowInfo.class, "workflow_process_info-order-123");

// Print hierarchy
ExecutionPathDebugger.printPathHierarchy(info);

// Output:
// Execution Path Hierarchy for case: order-123
// ────────────────────────────────────────────────────────────────────────────────
// ◷ . (step: join_1)
//   ✓ .route_1.branch_a. (step: step_a2)
//   ◷ .route_1.branch_b. (step: step_b1) [manager_review]
//   ✓ .route_1.branch_c. (step: step_c1)
// ────────────────────────────────────────────────────────────────────────────────
// Pend Path: .route_1.branch_b.

// Validate consistency
ExecutionPathDebugger.validatePathConsistency(info);

// Get statistics
Map<String, Object> stats = ExecutionPathDebugger.getPathStatistics(info);
System.out.println("Statistics: " + stats);
// Output: {total_paths=4, completed_paths=2, active_paths=2, pended_paths=1, 
//          max_depth=3, parallel_routes=1}
```

### Common Issues and Solutions

#### Issue 1: "Orphaned" Execution Paths

**Symptoms**: Paths with status STARTED but no workbasket after crash

**Diagnosis**:
```java
for (ExecPath path : workflowInfo.getExecPaths()) {
    if (path.getStatus() == ExecPathStatus.STARTED 
        && path.getPendWorkBasket().isEmpty()
        && !workflowInfo.getPendExecPath().equals(path.getName())) {
        System.out.println("Orphaned path: " + path.getName());
    }
}
```

**Cause**: JVM crashed during execution before persistence

**Solution**: Automatic sanitization on resume (see crash recovery docs)

#### Issue 2: Multiple Paths with Workbaskets

**Symptoms**: Multiple paths show workbaskets, unclear which is "official"

**Diagnosis**:
```java
List<ExecPath> pendedPaths = workflowInfo.getExecPaths().stream()
    .filter(p -> !p.getPendWorkBasket().isEmpty())
    .collect(Collectors.toList());

System.out.println("Pended paths: " + pendedPaths.size());
pendedPaths.forEach(p -> System.out.println("  - " + p.getName()));
```

**Cause**: Legitimate - parallel branches can pend independently

**Understanding**: The "deepest" path is selected as the official pend path

#### Issue 3: Join Never Completes

**Symptoms**: Workflow stuck at join, some branches completed

**Diagnosis**:
```java
// Check which branches haven't completed
String joinStep = "join_1";
List<ExecPath> atJoin = workflowInfo.getExecPaths().stream()
    .filter(p -> joinStep.equals(p.getStep()))
    .collect(Collectors.toList());

System.out.println("Paths at join: " + atJoin.size());

// Check siblings of pended path
String pendPath = workflowInfo.getPendExecPath();
if (!pendPath.isEmpty()) {
    ExecPath pended = workflowInfo.getExecPaths().stream()
        .filter(p -> p.getName().equals(pendPath))
        .findFirst()
        .orElse(null);
    
    if (pended != null) {
        List<ExecPath> siblings = workflowInfo.getExecPaths().stream()
            .filter(p -> pended.isSibling(p))
            .collect(Collectors.toList());
        
        System.out.println("Sibling paths:");
        siblings.forEach(s -> System.out.printf("  %s: %s%n", 
            s.getName(), 
            s.getStatus()
        ));
    }
}
```

**Common Causes**:
1. One branch pended - join waits correctly
2. Ticket raised from child path - join waits for ticket processing
3. Error in branch without proper error handling

**Solution**: Resume the pended path or fix the error

#### Issue 4: Wrong Path Selected as Pend Path

**Symptoms**: Unexpected path name in `pend_exec_path`

**Diagnosis**:
```java
// Show all pended paths with depths
workflowInfo.getExecPaths().stream()
    .filter(p -> !p.getPendWorkBasket().isEmpty())
    .forEach(p -> {
        int depth = StringUtils.getCount(p.getName(), '.');
        System.out.printf("%s (depth %d) - %s%n", 
            p.getName(), 
            depth, 
            p.getPendWorkBasket()
        );
    });

System.out.println("Selected: " + workflowInfo.getPendExecPath());
```

**Cause**: Deepest path logic - this is usually correct behavior

**Example**:
```
.route_1.A. (depth 3) - review_queue
.route_1.A.route_2.1. (depth 5) - approval_queue

Selected: .route_1.A.route_2.1.  ← Correct (deepest)
```

#### Issue 5: Race Conditions in Parallel Branches

**Symptoms**: Inconsistent process variable values, duplicate operations

**Diagnosis**:
```java
// Add thread-safety logging
public class DebugStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        String threadName = Thread.currentThread().getName();
        String execPath = context.getExecPathName();
        
        log.info("[{}] Path {} starting operation", threadName, execPath);
        
        // Your operation here
        Integer counter = context.getProcessVariables().getInteger("counter");
        log.info("[{}] Path {} read counter: {}", threadName, execPath, counter);
        
        // ... rest of operation
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}
```

**Solution**: Use synchronization
```java
synchronized (context.getProcessVariables()) {
    // Critical section
    Integer counter = context.getProcessVariables().getInteger("counter");
    counter = (counter == null) ? 1 : counter + 1;
    context.getProcessVariables().setValue(
        "counter", 
        WorkflowVariableType.INTEGER, 
        counter
    );
}
```

### Visual Path Debugger

```java
public class VisualPathDebugger {
    
    public static String generateAsciiTree(WorkflowInfo workflowInfo) {
        StringBuilder tree = new StringBuilder();
        List<ExecPath> paths = workflowInfo.getExecPaths();
        
        // Build tree structure
        Map<String, List<ExecPath>> childrenMap = new HashMap<>();
        for (ExecPath path : paths) {
            String parent = path.getParentExecPathName();
            childrenMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(path);
        }
        
        // Render tree starting from root
        ExecPath root = paths.stream()
            .filter(p -> p.getName().equals("."))
            .findFirst()
            .orElse(null);
        
        if (root != null) {
            renderPath(tree, root, childrenMap, "", true, workflowInfo.getPendExecPath());
        }
        
        return tree.toString();
    }
    
    private static void renderPath(
        StringBuilder tree,
        ExecPath path,
        Map<String, List<ExecPath>> childrenMap,
        String prefix,
        boolean isLast,
        String pendPath
    ) {
        // Status indicator
        String status = path.getStatus() == ExecPathStatus.COMPLETED ? "✓" : "◷";
        if (path.getName().equals(pendPath)) {
            status = "⏸";
        }
        if (path.getErrorCode() != null && path.getErrorCode() != 0) {
            status = "✗";
        }
        
        // Build line
        tree.append(prefix);
        tree.append(isLast ? "└── " : "├── ");
        tree.append(status).append(" ");
        tree.append(path.getName());
        tree.append(" (").append(path.getStep()).append(")");
        
        if (!path.getPendWorkBasket().isEmpty()) {
            tree.append(" [").append(path.getPendWorkBasket()).append("]");
        }
        if (path.getErrorCode() != null && path.getErrorCode() != 0) {
            tree.append(" ERROR: ").append(path.getErrorDesc());
        }
        tree.append("\n");
        
        // Render children
        List<ExecPath> children = childrenMap.getOrDefault(path.getName(), Collections.emptyList());
        for (int i = 0; i < children.size(); i++) {
            boolean childIsLast = (i == children.size() - 1);
            String childPrefix = prefix + (isLast ? "    " : "│   ");
            renderPath(tree, children.get(i), childrenMap, childPrefix, childIsLast, pendPath);
        }
    }
}
```

**Usage**:
```java
WorkflowInfo info = dao.get(WorkflowInfo.class, "workflow_process_info-order-123");
String tree = VisualPathDebugger.generateAsciiTree(info);
System.out.println(tree);
```

**Output**:
```
└── ◷ . (join_1)
    ├── ✓ .route_1.branch_a. (step_a2)
    ├── ⏸ .route_1.branch_b. (step_b1) [manager_review]
    │   └── ✓ .route_1.branch_b.route_2.1. (step_x)
    └── ✓ .route_1.branch_c. (step_c1)
```

## Performance Considerations

### Thread Pool Sizing

```java
// Configure during initialization
WorkflowService.init(
    maxThreads,    // Parallel thread pool size
    joinTimeout,   // Timeout for join operations
    keySeparator   // Key separator character
);
```

**Sizing Guidelines**:
```
CPU-bound tasks:  maxThreads = CPU cores + 1
I/O-bound tasks:  maxThreads = CPU cores * 2
Mixed workload:   maxThreads = CPU cores * 1.5

Example for 4-core machine:
- CPU-bound: 5 threads
- I/O-bound: 8 threads
- Mixed: 6 threads
```

**Monitoring Thread Usage**:
```java
ThreadPoolExecutor executor = (ThreadPoolExecutor) WorkflowService.instance()
    .getExecutorService();

System.out.println("Active threads: " + executor.getActiveCount());
System.out.println("Pool size: " + executor.getPoolSize());
System.out.println("Queue size: " + executor.getQueue().size());
System.out.println("Completed tasks: " + executor.getCompletedTaskCount());
```

### Join Timeout Handling

```java
// What happens on timeout
try {
    future.get(joinTimeout, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    // Branch didn't complete in time
    log.warn("Branch {} timed out after {}ms", 
        execPath.getName(), 
        joinTimeout
    );
    
    // Options:
    // 1. Pend the workflow for manual intervention
    // 2. Cancel the branch
    // 3. Continue with partial results
}
```

**Timeout Recommendations**:
```
Fast operations (<1s):   30 seconds
Normal operations:       5 minutes (300000ms)
Long operations:         30 minutes
External API calls:      Based on SLA
```

### Limiting Parallel Depth

```java
public class DepthLimitingRoute implements InvokableRoute {
    
    private static final int MAX_DEPTH = 5; // 3 levels of nesting
    
    @Override
    public RouteResponse executeRoute() {
        String currentPath = getContext().getExecPathName();
        int currentDepth = StringUtils.getCount(currentPath, '.');
        
        if (currentDepth >= MAX_DEPTH) {
            log.warn("Maximum depth {} reached at path {}, executing sequentially", 
                MAX_DEPTH, 
                currentPath
            );
            
            // Execute sequentially instead of parallel
            return new RouteResponse(
                StepResponseType.OK_PROCEED,
                Collections.singletonList("sequential"),
                null
            );
        }
        
        // Normal parallel execution
        List<String> branches = determineBranches();
        return new RouteResponse(
            StepResponseType.OK_PROCEED,
            branches,
            null
        );
    }
}
```

### Memory Optimization

**Problem**: Large number of execution paths consumes memory

**Solutions**:

1. **Limit Parallel Branches**:
```java
public RouteResponse executeRoute() {
    List<String> allItems = getItemsToProcess();
    
    // Process in batches instead of all at once
    if (allItems.size() > 10) {
        List<String> batch = allItems.subList(0, 10);
        
        // Store remaining for next iteration
        context.getProcessVariables().setValue(
            "remaining_items",
            WorkflowVariableType.LIST_OF_OBJECT,
            allItems.subList(10, allItems.size())
        );
        
        return new RouteResponse(StepResponseType.OK_PROCEED, batch, null);
    }
    
    return new RouteResponse(StepResponseType.OK_PROCEED, allItems, null);
}
```

2. **Clean Completed Paths**:
```java
// Periodically remove completed paths from memory
public void cleanupCompletedPaths(WorkflowInfo info) {
    if (info.getIsComplete()) {
        // Keep only summary information
        info.getExecPaths().clear();
        info.getExecPaths().add(createSummaryPath());
    }
}
```

### Persistence Optimization

**Aggressive vs Lazy Persistence**:

```java
// Aggressive (default) - safer but slower
WorkflowService.instance().setWriteProcessInfoAfterEachStep(true);
// Persists after every step execution
// Better crash recovery
// Higher I/O overhead

// Lazy - faster but requires idempotent steps
WorkflowService.instance().setWriteProcessInfoAfterEachStep(false);
// Persists only on pend or complete
// Better performance
// May lose more work on crash
```

**Recommendation**: Use aggressive for:
- Production systems
- Long-running steps
- Expensive external calls

Use lazy for:
- Fast operations (<100ms per step)
- Fully idempotent workflows
- Performance-critical scenarios

## Real-World Examples

### Example 1: Order Processing System

```json
{
  "flow": [
    {"name": "start", "next": "validate_order"},
    {"name": "validate_order", "type": "task", "component": "validator", "next": "parallel_processing"},
    {
      "name": "parallel_processing",
      "type": "p_route",
      "component": "order_route",
      "branches": [
        {"name": "inventory", "next": "check_inventory"},
        {"name": "payment", "next": "process_payment"},
        {"name": "shipping", "next": "calculate_shipping"}
      ],
      "join": "join_processing"
    },
    {"name": "check_inventory", "type": "task", "component": "inventory_check", "next": "join_processing"},
    {"name": "process_payment", "type": "task", "component": "payment_processor", "next": "join_processing"},
    {"name": "calculate_shipping", "type": "task", "component": "shipping_calc", "next": "join_processing"},
    {"name": "join_processing", "type": "task", "component": "consolidator", "next": "fulfill_order"},
    {"name": "fulfill_order", "type": "task", "component": "fulfillment", "next": "end"},
    {"name": "end", "type": "end"}
  ]
}
```

**Execution Path Timeline**:
```
T0:   . → validate_order
T1:   . → parallel_processing (spawns children)
T2:   .parallel_processing.inventory. → check_inventory
T2:   .parallel_processing.payment. → process_payment
T2:   .parallel_processing.shipping. → calculate_shipping
T3:   .parallel_processing.inventory. → COMPLETED
T4:   .parallel_processing.shipping. → COMPLETED
T5:   .parallel_processing.payment. → PENDED (requires approval)
      [Workflow pends, waits for payment approval]
T100: .parallel_processing.payment. → RESUMED
T101: .parallel_processing.payment. → COMPLETED
T102: . → join_processing (all branches done)
T103: . → fulfill_order
T104: . → end (COMPLETED)
```

### Example 2: Document Approval Workflow

```json
{
  "flow": [
    {"name": "start", "next": "submit_document"},
    {"name": "submit_document", "type": "task", "component": "doc_submission", "next": "parallel_review"},
    {
      "name": "parallel_review",
      "type": "p_route",
      "component": "review_route",
      "branches": [
        {"name": "legal", "next": "legal_review"},
        {"name": "compliance", "next": "compliance_review"},
        {"name": "technical", "next": "technical_review"}
      ],
      "join": "join_reviews"
    },
    {"name": "legal_review", "type": "task", "component": "legal_reviewer", "next": "join_reviews"},
    {"name": "compliance_review", "type": "task", "component": "compliance_reviewer", "next": "join_reviews"},
    {"name": "technical_review", "type": "task", "component": "technical_reviewer", "next": "join_reviews"},
    {"name": "join_reviews", "type": "task", "component": "review_aggregator", "next": "decision_route"},
    {
      "name": "decision_route",
      "type": "s_route",
      "component": "decision_router",
      "branches": [
        {"name": "approved", "next": "publish_document"},
        {"name": "rejected", "next": "notify_rejection"},
        {"name": "revisions", "next": "request_revisions"}
      ]
    },
    {"name": "publish_document", "type": "task", "component": "publisher", "next": "end"},
    {"name": "notify_rejection", "type": "task", "component": "notifier", "next": "end"},
    {"name": "request_revisions", "type": "task", "component": "revision_requester", "next": "end"},
    {"name": "end", "type": "end"}
  ]
}
```

**Implementation**:
```java
public class LegalReviewStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        String docId = context.getProcessVariables().getString("document_id");
        
        // Pend for human review
        return new TaskResponse(
            StepResponseType.OK_PEND,
            "",
            "legal_review_queue"
        );
    }
}

public class ReviewAggregatorStep implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        // All reviews are complete - aggregate results
        Boolean legalApproved = context.getProcessVariables()
            .getBoolean("legal_approved");
        Boolean complianceApproved = context.getProcessVariables()
            .getBoolean("compliance_approved");
        Boolean technicalApproved = context.getProcessVariables()
            .getBoolean("technical_approved");
        
        boolean allApproved = Boolean.TRUE.equals(legalApproved) &&
                             Boolean.TRUE.equals(complianceApproved) &&
                             Boolean.TRUE.equals(technicalApproved);
        
        context.getProcessVariables().setValue(
            "all_reviews_approved",
            WorkflowVariableType.BOOLEAN,
            allApproved
        );
        
        return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    }
}

public class DecisionRouter implements InvokableRoute {
    @Override
    public RouteResponse executeRoute() {
        Boolean allApproved = context.getProcessVariables()
            .getBoolean("all_reviews_approved");
        
        String branch = Boolean.TRUE.equals(allApproved) ? "approved" : "rejected";
        
        return new RouteResponse(
            StepResponseType.OK_PROCEED,
            Collections.singletonList(branch),
            null
        );
    }
}
```

### Example 3: Multi-Region Data Processing

```java
public class RegionalProcessingRoute implements InvokableRoute {
    @Override
    public RouteResponse executeRoute() {
        // Get regions that need processing
        @SuppressWarnings("unchecked")
        List<String> regions = (List<String>) context.getProcessVariables()
            .getValue("active_regions", WorkflowVariableType.LIST_OF_OBJECT);
        
        log.info("Processing {} regions in parallel", regions.size());
        
        return new RouteResponse(
            StepResponseType.OK_PROCEED,
            regions,
            null
        );
    }
}

public class RegionalDataProcessor implements InvokableTask {
    @Override
    public TaskResponse executeStep() {
        String execPath = context.getExecPathName();
        
        // Extract region from execution path
        // e.g., ".regional_processing.us_east." → "us_east"
        String region = extractRegionFromPath(execPath);
        
        log.info("Processing data for region: {}", region);
        
        try {
            // Process region data
            List<Record> records = fetchRecordsForRegion(region);
            processRecords(records);
            
            // Store result in process variables
            context.getProcessVariables().setValue(
                "region_" + region + "_count",
                WorkflowVariableType.INTEGER,
                records.size()
            );
            
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
            
        } catch (ServiceException e) {
            log.error("Failed to process region {}: {}", region, e.getMessage());
            
            return new TaskResponse(
                StepResponseType.ERROR_PEND,
                "Error processing region: " + e.getMessage(),
                "error_queue"
            );
        }
    }
    
    private String extractRegionFromPath(String execPath) {
        // ".regional_processing.us_east." → "us_east"
        String[] parts = execPath.split("\\.");
        return parts[parts.length - 2];
    }
}
```

## Summary

### Key Takeaways

1. **Execution paths are threads**: Each path represents an independent thread of execution
2. **Naming is hierarchical**: Path names encode parent-child relationships
3. **Depth matters**: Used for selecting pend path and determining relationships
4. **Synchronization at joins**: All sibling paths must complete before proceeding
5. **Process variables for coordination**: Share data between parallel branches safely
6. **Crash recovery aware**: Paths are persisted and can be recovered

### Best Practices Checklist

- ✅ Keep parallel nesting to 3 levels maximum
- ✅ Use meaningful route and branch names (no dots!)
- ✅ Synchronize access to process variables in parallel paths
- ✅ Make steps idempotent for crash recovery
- ✅ Log execution path names for debugging
- ✅ Monitor thread pool usage
- ✅ Set appropriate join timeouts
- ✅ Test parallel scenarios thoroughly

### Common Patterns

| Pattern | Use Case | Example |
|---------|----------|---------|
| Fan-out/Fan-in | Process items in parallel, aggregate results | Order processing |
| Barrier sync | Wait for all branches at checkpoint | Multi-stage approval |
| Critical section | Execute code only once across branches | Database update |
| Conditional branching | Variable number of branches | Regional processing |
| Nested parallel | Hierarchical parallel processing | Multi-level aggregation |

### Related Documentation

- [Parallel Processing Guide](./parallel-processing.md) - Detailed parallel route patterns
- [Crash Recovery](./crash-recovery.md) - How paths survive crashes
- [Process Variables](./process-variables.md) - Sharing data between paths
- [Work Baskets](./work-baskets.md) - Managing pended paths
- [Debugging Guide](../troubleshooting/debugging.md) - Troubleshooting path issues

## Quick Reference

### Path Name Format
```
.<route_name>.<branch_name>.<route_name>.<branch_name>.
```

### Depth Calculation
```java
int depth = StringUtils.getCount(execPathName, '.');
```

### Parent Path
```java
String parent = execPath.getParentExecPathName();
```

### Sibling Check
```java
boolean areSiblings = path1.isSibling(path2);
```

### Status Values
- `STARTED` - Currently executing or pended
- `COMPLETED` - Finished execution

### Response Types
- `OK_PROCEED` - Continue to next step
- `OK_PEND` - Pause, resume from next step
- `OK_PEND_EOR` - Pause, resume from same step (re-execute)
- `ERROR_PEND` - Pause with error

---

This documentation should give you a complete understanding of execution paths in Simple Workflow. For specific scenarios not covered here, refer to the related documentation or the source code comments.
