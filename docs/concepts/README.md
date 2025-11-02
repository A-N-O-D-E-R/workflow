# Core Concepts

This section explains the fundamental concepts of the Simple Workflow engine. Understanding these concepts is essential for building robust workflows.

## Essential Concepts

### [Execution Paths](./execution-paths.md)
Learn how workflows execute steps sequentially and in parallel. Execution paths determine how work is distributed and coordinated.

**Key Topics:**
- Sequential execution (single path)
- Parallel execution (multiple paths)
- Nested execution paths
- Path synchronization and coordination
- Performance considerations

**When to read:** Building workflows that need parallel processing

### [Process Variables](./process-variables.md)
Understand how data flows through your workflow. Process variables are the workflow's memory.

**Key Topics:**
- Reading and writing variables
- Variable scope and lifecycle
- Data type handling and serialization
- Sharing data between execution paths
- Best practices for variable management

**When to read:** Need to store or share data in workflows

### [Crash Recovery](./crash-recovery.md)
Discover how the engine automatically recovers from failures without losing progress.

**Key Topics:**
- Persistence modes (immediate vs batch)
- Recovery mechanisms
- Checkpoint strategies
- Data consistency guarantees
- Testing crash recovery

**When to read:** Running workflows in production environments

### [Work Baskets](./work-baskets.md)
Learn how to incorporate human tasks into automated workflows.

**Key Topics:**
- Creating and managing work items
- Basket routing strategies
- Claiming and completing work
- Load balancing and escalation
- Integration with workflow execution

**When to read:** Building workflows with manual approval or review steps

### [SLA Management](./sla-management-doc.md)
Track milestones and deadlines to ensure timely workflow completion.

**Key Topics:**
- Defining milestones
- Tracking deadline compliance
- Breach detection and notification
- Historical SLA reporting
- Custom SLA configurations

**When to read:** Need to monitor workflow performance and compliance

## Concept Relationships

Understanding how these concepts work together:

```
┌─────────────────────────────────────────────────────────┐
│                    Workflow Instance                    │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │
│  │ Execution   │  │  Process    │  │    SLA      │      │
│  │   Paths     │◄─┤  Variables  │─►│ Milestones  │      │
│  │             │  │             │  │             │      │
│  └──────┬──────┘  └─────────────┘  └─────────────┘      │
│         │                                               │
│         ▼                                               │
│  ┌─────────────┐                                        │
│  │    Work     │                                        │
│  │   Baskets   │                                        │
│  │             │                                        │
│  └─────────────┘                                        │
│                                                         │
│         Crash Recovery (underlying all components)      │
└─────────────────────────────────────────────────────────┘
```

## Learning Path

### For Beginners
1. Start with [Execution Paths](./execution-paths.md) - understand how workflows execute
2. Read [Process Variables](./process-variables.md) - learn data management
3. Review [Crash Recovery](./crash-recovery.md) - understand reliability

### For Advanced Users
1. Study [Work Baskets](./work-baskets.md) - integrate human tasks
2. Explore [SLA Management](./sla-management-doc.md) - track performance
3. Review [Error Handling Patterns](../patterns/error-handling.md)

## Quick Examples

### Sequential Workflow
```json
{
  "steps": [
    {"stepId": "step1", "executionPath": "main", "order": 1},
    {"stepId": "step2", "executionPath": "main", "order": 2},
    {"stepId": "step3", "executionPath": "main", "order": 3}
  ]
}
```

### Parallel Workflow
```json
{
  "steps": [
    {"stepId": "step1", "executionPath": "path-a", "order": 1},
    {"stepId": "step2", "executionPath": "path-b", "order": 1},
    {"stepId": "step3", "executionPath": "path-c", "order": 1}
  ]
}
```

### Using Process Variables
```java
// Write
context.setProcessVariable("orderId", "ORD-123");

// Read
String orderId = context.getProcessVariable("orderId");
```

### Work Basket
```java
// Create work item
WorkItem item = wms.createWorkItem(caseId, stepId, "approvals");

// Complete work
wms.completeWorkItem(workItemId);
```

## Related Documentation

- [Patterns & Best Practices](../patterns/) - Common workflow patterns
- [API Reference](../api/README.md) - Detailed API documentation
- [Examples](../../examples/) - Working code examples
- [Tutorials](../tutorials/) - Step-by-step guides

## Next Steps

After understanding these concepts:
- Explore [Patterns](../patterns/) for advanced techniques
- Review [Best Practices](../best-practices/README.md) for production readiness
- Check [Examples](../../examples/) for real-world implementations
