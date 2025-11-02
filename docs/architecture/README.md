# Architecture Overview

This document provides a comprehensive overview of the Simple Workflow engine architecture, design decisions, and internal workings.

## High-Level Architecture

```
┌────────────────────────────────────────────────────────┐
│                  Application Layer                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │   Steps     │  │   Routes    │  │   Events    │     │
│  │   (Your     │  │   (Your     │  │   (Your     │     │
│  │    Code)    │  │    Code)    │  │    Code)    │     │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘     │
└─────────┼────────────────┼────────────────┼────────────┘
          │                │                │
┌─────────▼────────────────▼────────────────▼────────────┐
│              Workflow Service Layer                    │
│  ┌─────────────────────────────────────────────────┐   │
│  │          WorkflowService (Singleton)            │   │
│  └────┬───────────────┬────────────────┬───────────┘   │
│       │               │                │               │
│  ┌────▼─────┐  ┌──────▼──────┐  ┌──────▼──────┐        │
│  │ Runtime  │  │    Work     │  │     SLA     │        │
│  │ Service  │  │  Management │  │  Management │        │
│  │          │  │   Service   │  │   Service   │        │
│  └────┬─────┘  └──────┬──────┘  └──────┬──────┘        │
└───────┼────────────────┼────────────────┼──────────────┘
        │                │                │
┌───────▼────────────────▼────────────────▼──────────────┐
│              Persistence Layer                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │         CommonService (DAO Interface)           │   │
│  └────┬──────────────┬─────────────┬────────────┬──┘   │
│       │              │             │            │      │
│  ┌────▼─────┐  ┌─────▼────┐  ┌────▼─────┐  ┌────▼───┐  │
│  │ FileDao  │  │ RDBMS    │  │ MongoDB  │  │ Custom │  │
│  │          │  │ Dao      │  │ Dao      │  │  Dao   │  │
│  └──────────┘  └──────────┘  └──────────┘  └────────┘  │
└────────────────────────────────────────────────────────┘
          │              │             │             │
┌─────────▼──────────────▼─────────────▼─────────────▼───┐
│                Storage Layer                           │
│   Files    PostgreSQL   MongoDB    DynamoDB    Redis   │
└────────────────────────────────────────────────────────┘
```

## Core Components

### 1. WorkflowService

The central service managing the entire workflow engine.

**Responsibilities:**
- Singleton initialization and lifecycle management
- Thread pool management for step execution
- Service orchestration (Runtime, Work Management, SLA)
- Configuration management

**Key Methods:**
```java
WorkflowService.init(poolSize, timeout, delimiter)  // Initialize
WorkflowService.instance()                           // Get singleton
service.getRunTimeService(...)                       // Create runtime service
service.getWorkManagementService(...)                // Create work mgmt service
service.getSlaManagementService(...)                 // Create SLA service
```

**Design Pattern:** Singleton with lazy initialization

### 2. RuntimeService

Manages workflow execution and lifecycle.

**Responsibilities:**
- Starting new workflow instances
- Executing workflow steps
- Managing execution paths (sequential and parallel)
- Crash recovery
- Process state management
- Event notification

**Key Components:**
- **ProcessExecutor**: Orchestrates step execution across paths
- **StepProcessor**: Executes individual steps
- **PathCoordinator**: Manages parallel execution paths
- **RecoveryManager**: Handles crash recovery

**Execution Flow:**
1. Parse workflow definition JSON
2. Create process entity
3. Initialize execution paths
4. Schedule steps for execution
5. Execute steps via thread pool
6. Update state after each step
7. Coordinate parallel paths
8. Complete process when all paths finish

### 3. WorkManagementService

Manages human task queues and assignments.

**Responsibilities:**
- Creating work items
- Managing work baskets
- Handling work item lifecycle (claim, release, complete)
- Routing work to appropriate baskets
- User assignment tracking

**Key Components:**
- **WorkBasketManager**: Manages basket collections
- **WorkItemTracker**: Tracks work item states
- **AssignmentEngine**: Handles user assignments

### 4. SlaManagementService

Tracks milestones and SLA compliance.

**Responsibilities:**
- Defining and tracking milestones
- Detecting SLA breaches
- Calculating time metrics
- Historical SLA reporting

**Key Components:**
- **MilestoneTracker**: Monitors milestone progress
- **BreachDetector**: Identifies SLA violations
- **MetricsCalculator**: Computes SLA metrics

## Threading Model

### Thread Pool Architecture

```
┌─────────────────────────────────────┐
│      WorkflowService Thread Pool    │
│                                     │
│  ┌────────┐  ┌────────┐  ┌────────┐ │
│  │Thread 1│  │Thread 2│  │Thread N│ │
│  └───┬────┘  └───┬────┘  └───┬────┘ │
└──────┼───────────┼───────────┼──────┘
       │           │           │
       ▼           ▼           ▼
     ┌────┐      ┌────┐      ┌────┐
     │Step│      │Step│      │Step│
     │ A  │      │ B  │      │ C  │
     └────┘      └────┘      └────┘
```

**Configuration:**
```java
WorkflowService.init(
    10,      // Pool size: 10 threads
    30000,   // Timeout: 30 seconds
    "-"      // Path delimiter
);
```

**Thread Safety:**
- Each workflow instance is isolated
- Process variables are synchronized
- DAO operations are thread-safe
- Steps can execute concurrently for different cases

**Concurrency Guarantees:**
- Steps in same execution path execute sequentially
- Steps in different paths execute concurrently
- Variables are consistently persisted before next step
- No lost updates due to synchronization

### Execution Path Processing

**Sequential Execution:**
```
Path "main":
  Step1 → Step2 → Step3 → Step4
  (Single thread processes sequentially)
```

**Parallel Execution:**
```
Path "main":     Step1 → Step2
Path "parallel": Step3 → Step4
Path "async":    Step5 → Step6
(Different threads process concurrently)
```

## Data Flow

### Process Lifecycle

```
START
  │
  ▼
┌────────────────┐
│ Parse JSON     │
│ Definition     │
└────┬───────────┘
     ▼
┌────────────────┐
│ Create Process │
│ Entity         │
└────┬───────────┘
     ▼
┌────────────────┐
│ Initialize     │
│ Variables      │
└────┬───────────┘
     ▼
┌────────────────┐
│ Schedule Steps │
│ for Execution  │
└────┬───────────┘
     ▼
┌────────────────────┐
│ Execute Steps      │
│ (Loop)             │
│  ┌──────────────┐  │
│  │ Execute Step │  │
│  │ Persist State│  │
│  │ Fire Events  │  │
│  │ Next Step    │  │
│  └──────────────┘  │
└────┬───────────────┘
     ▼
┌────────────────┐
│ All Steps Done?│
└────┬─────┬─────┘
     │     │
    No    Yes
     │     ▼
     │   ┌──────────┐
     │   │ Complete │
     │   │ Process  │
     │   └──────────┘
     │        │
     └────────┘
```

### Step Execution Flow

```
┌─────────────────┐
│ Get Next Step   │
└────┬────────────┘
     ▼
┌─────────────────┐
│ Check           │
│ Prerequisites   │
└────┬────────────┘
     ▼
┌─────────────────┐
│ Fire            │
│ onStepStart     │
└────┬────────────┘
     ▼
┌─────────────────┐
│ Execute Step    │
│ Business Logic  │
└────┬────────────┘
     │
     ├─ Success ──────────┐
     │                    ▼
     │           ┌─────────────────┐
     │           │ Persist State   │
     │           └────┬────────────┘
     │                ▼
     │           ┌─────────────────┐
     │           │ Fire            │
     │           │ onStepComplete  │
     │           └────┬────────────┘
     │                ▼
     │           ┌─────────────────┐
     │           │ Schedule Next   │
     │           └─────────────────┘
     │
     └─ Error ───────────┐
                         ▼
                ┌─────────────────┐
                │ Persist Error   │
                └────┬────────────┘
                     ▼
                ┌─────────────────┐
                │ Fire            │
                │ onStepError     │
                └────┬────────────┘
                     ▼
                ┌─────────────────┐
                │ Fail Process    │
                └─────────────────┘
```

## Persistence Architecture

### DAO Interface Design

The `CommonService` interface abstracts all persistence operations:

```java
public interface CommonService {
    // CRUD operations for processes, steps, variables
    void saveProcess(ProcessEntity process);
    ProcessEntity loadProcess(String caseId);

    void saveStep(StepEntity step);
    List<StepEntity> loadSteps(String caseId);

    void saveVariables(String caseId, Map<String, Object> vars);
    Map<String, Object> loadVariables(String caseId);

    // Work basket operations
    void saveWorkItem(WorkItem item);
    List<WorkItem> loadWorkItemsByBasket(String basket);

    // SLA operations
    void saveMilestone(Milestone milestone);
    List<Milestone> loadMilestones(String caseId);
}
```

**Design Benefits:**
- Pluggable persistence backends
- Easy to test with mocks
- No vendor lock-in
- Support for multiple storage types

### Persistence Modes

**Immediate Persistence (Default):**
- State saved after each step
- Maximum durability
- Enables crash recovery
- Slight performance overhead

**Batch Persistence (Optional):**
- State saved periodically
- Better performance
- Risk of data loss on crash
- Suitable for non-critical workflows

### Transaction Handling

**File-Based:**
- Atomic file writes
- No explicit transactions
- Directory-level consistency

**RDBMS:**
- JPA/Hibernate transactions
- ACID guarantees
- Row-level locking

**NoSQL:**
- Eventual consistency (MongoDB)
- Atomic document operations
- Conditional writes (DynamoDB)

## Crash Recovery Mechanism

### Recovery Process

```
Application Crash
       ↓
Application Restart
       ↓
Initialize WorkflowService
       ↓
Call recoverIncompleteProcesses()
       ↓
Load all processes from storage
       ↓
Identify incomplete processes
       ↓
For each incomplete:
  - Load process state
  - Load step history
  - Identify last completed step
  - Resume from next step
       ↓
Continue normal execution
```

### Recovery Guarantees

**At-Least-Once Execution:**
- Steps may execute multiple times if crash occurs during step
- Steps must be idempotent for correctness
- Process variables prevent duplicate work

**State Consistency:**
- Process state always consistent
- Variables reflect last committed step
- No partial state corruption

**Recovery Time:**
- Proportional to number of incomplete processes
- Typically < 1 second for dozens of processes
- Parallel recovery for many processes

## Performance Characteristics

### Scalability

**Vertical Scalability:**
- Increase thread pool size
- More CPU cores = more concurrent steps
- Limited by database throughput

**Horizontal Scalability:**
- Multiple application instances
- Shared persistence layer
- Optimistic locking prevents conflicts
- Each instance processes independent cases

### Performance Tuning

**Thread Pool Sizing:**
```java
// CPU-bound workloads
WorkflowService.init(Runtime.getRuntime().availableProcessors(), ...);

// I/O-bound workloads
WorkflowService.init(poolSize * 2, ...);
```

**Persistence Optimization:**
- Batch writes when possible
- Use connection pooling
- Index database properly
- Cache frequently accessed data

**Memory Management:**
- Process variables kept in memory during execution
- Garbage collected after completion
- Limit variable size for large-scale systems

## Design Patterns Used

### 1. Singleton Pattern
- `WorkflowService` is a singleton
- Ensures single thread pool and configuration

### 2. Factory Pattern
- `WorkflowComponantFactory` creates steps and routes
- Decouples workflow definition from implementation

### 3. Strategy Pattern
- Different DAO implementations (File, RDBMS, NoSQL)
- Pluggable execution strategies

### 4. Observer Pattern
- `EventHandler` for lifecycle events
- Loose coupling between engine and application

### 5. Command Pattern
- Each step is a command
- Encapsulates business logic

### 6. Template Method Pattern
- Step execution flow is templated
- Custom logic in `execute()` method

## Extension Points

### 1. Custom Steps
Implement `WorkflowStep` interface

### 2. Custom Routes
Implement `WorkflowRoute` interface

### 3. Custom Persistence
Implement `CommonService` interface

### 4. Custom Events
Implement `EventHandler` interface

### 5. Custom SLA Logic
Extend SLA configuration and tracking

## Related Documentation

- [Crash Recovery](../concepts/crash-recovery.md) - Recovery details
- [Custom DAO](../persistence/custom-dao.md) - Persistence implementation
- [Performance Tuning](../operations/performance.md) - Optimization guide
- [Best Practices](../best-practices/README.md) - Architectural best practices
