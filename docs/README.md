# Simple Workflow Documentation

Complete documentation for the Simple Workflow orchestration engine.

## Quick Navigation

- **New to Simple Workflow?** Start with [Getting Started](./getting-started.md)
- **Need a specific feature?** Check the [Core Concepts](#core-concepts) section
- **Building something?** Browse [Examples](../examples/)
- **Running into issues?** See [Troubleshooting](#troubleshooting)

## Documentation Structure

### 📚 Getting Started
- [Getting Started Guide](./getting-started.md) - Your first workflow in 30 minutes
- [Installation & Setup](./getting-started.md#installation)
- [Quick Start Example](./getting-started.md#your-first-workflow)

### 🔧 Core Concepts
Essential concepts you need to understand:

- [Execution Paths](./concepts/execution-paths.md) - Sequential vs parallel execution
- [Process Variables](./concepts/process-variables.md) - Storing and sharing workflow data
- [Crash Recovery](./concepts/crash-recovery.md) - Automatic recovery from failures
- [Work Baskets](./concepts/work-baskets.md) - Human task management
- [SLA Management](./concepts/sla-management-doc.md) - Milestone tracking and deadlines

### 🏗️ Architecture
Deep dive into how the engine works:

- [Architecture Overview](./architecture/README.md) - System design and components
- [Service Layer](./architecture/README.md#service-layer) - Core services explained
- [Persistence Layer](./architecture/README.md#persistence-layer) - Data storage
- [Threading Model](./architecture/README.md#threading-model) - Concurrency handling

### 💾 Persistence
Configuring data storage:

- [Custom DAO Implementation](./persistence/custom-dao.md) - Build your own persistence backend
- [File-Based Storage](./persistence/custom-dao.md#file-based-dao) - Development and testing
- [RDBMS Integration](./persistence/custom-dao.md#rdbms-dao) - PostgreSQL, MySQL, etc.
- [NoSQL Integration](./persistence/custom-dao.md#nosql-dao) - MongoDB, DynamoDB, etc.

### 🎯 Patterns & Best Practices
Proven patterns for common scenarios:

- [Error Handling](./patterns/error-handling.md) - Comprehensive error management strategies
- [Idempotency](./patterns/idempotency.md) - Building reliable, repeatable operations
- [Compensation Handling](./patterns/error-handling.md#compensation-patterns) - Rollback strategies
- [Saga Pattern](./patterns/error-handling.md#saga-pattern) - Long-running transactions
- [Best Practices](./best-practices/README.md) - Guidelines and recommendations

### 📖 API Reference
Detailed API documentation:

- [API Overview](./api/README.md) - Complete API reference
- [WorkflowService](./api/README.md#workflowservice) - Service initialization
- [RuntimeService](./api/README.md#runtimeservice) - Workflow execution
- [WorkManagementService](./api/README.md#workmanagementservice) - Work baskets
- [SlaManagementService](./api/README.md#slamanagementservice) - SLA tracking

### 🎓 Tutorials
Step-by-step guides:

- [Your First Workflow](./tutorials/first-workflow.md) - Basic workflow tutorial
- [Parallel Execution](./tutorials/parallel-execution.md) - Building parallel workflows
- [Human Tasks](./tutorials/human-tasks.md) - Integrating manual steps
- [Error Recovery](./tutorials/error-recovery.md) - Handling failures gracefully

### 🧪 Testing
Testing your workflows:

- [Testing Guide](./testing/README.md) - How to test workflows
- [Unit Testing](./testing/README.md#unit-testing) - Testing individual steps
- [Integration Testing](./testing/README.md#integration-testing) - End-to-end tests
- [Mocking & Stubs](./testing/README.md#mocking) - Test doubles for dependencies

### 🚀 Operations
Running in production:

- [Deployment Guide](./operations/README.md) - Production deployment
- [Monitoring](./operations/README.md#monitoring) - Tracking workflow health
- [Performance Tuning](./operations/performance.md) - Optimization strategies
- [Troubleshooting](./operations/README.md#troubleshooting) - Common issues

## Quick Reference

### Starting a Workflow

```java
// Initialize
WorkflowService.init(10, 30000, "-");
CommonService dao = new FileDao("./workflow-data");
RuntimeService rts = WorkflowService.instance()
    .getRunTimeService(dao, factory, handler, null);

// Start
rts.startCase(caseId, workflowJson, variables, null);
```

### Creating a Step

```java
public class MyStep implements WorkflowStep {
    @Override
    public void execute(WorkflowContext context) throws Exception {
        // Get data
        String orderId = context.getProcessVariable("orderId");

        // Do work
        processOrder(orderId);

        // Store result
        context.setProcessVariable("status", "PROCESSED");
    }
}
```

### Workflow Definition

```json
{
  "processDefinitionId": "my-workflow",
  "processDefinitionVersion": "1.0",
  "steps": [
    {
      "stepId": "step1",
      "stepType": "myStep",
      "executionPath": "main",
      "order": 1
    }
  ]
}
```

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| Steps not executing | Check component factory returns non-null instances |
| Variables not persisting | Ensure DAO is properly initialized |
| Process not starting | Validate JSON workflow definition |
| Crash recovery failing | Check persistence layer configuration |
| Performance issues | Review [Performance Tuning](./operations/performance.md) |

### Getting Help

- **Questions**: Check the [FAQ](./faq.md) first
- **Bugs**: Report on GitHub Issues
- **Discussions**: GitHub Discussions
- **Examples**: Browse the [examples directory](../examples/)

## Documentation By Use Case

### I want to...

**Build my first workflow**
→ [Getting Started Guide](./getting-started.md)

**Run steps in parallel**
→ [Execution Paths](./concepts/execution-paths.md)

**Handle human tasks**
→ [Work Baskets](./concepts/work-baskets.md)

**Recover from crashes**
→ [Crash Recovery](./concepts/crash-recovery.md)

**Track SLAs and deadlines**
→ [SLA Management](./concepts/sla-management-doc.md)

**Handle errors gracefully**
→ [Error Handling Patterns](./patterns/error-handling.md)

**Make operations idempotent**
→ [Idempotency Patterns](./patterns/idempotency.md)

**Use my own database**
→ [Custom DAO](./persistence/custom-dao.md)

**Optimize performance**
→ [Performance Tuning](./operations/performance.md)

**Deploy to production**
→ [Deployment Guide](./operations/README.md)

## Examples

Complete working examples:

- [Order Processing](../examples/order-processing/) - E-commerce order workflow
- [Approval Workflow](../examples/approval-workflow/) - Multi-level approvals
- [Parallel Processing](../examples/parallel-processing/) - Parallel execution demo
- [Saga Pattern](../examples/saga-pattern/) - Distributed transaction example
- [Custom Persistence](../examples/custom-persistence/) - Custom DAO implementation

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) for guidelines on contributing to the project.

## Changelog

See [CHANGELOG.md](../CHANGELOG.md) for version history and release notes.

## License

[License information]

---

**Need help?** Open an issue or start a discussion on GitHub.
