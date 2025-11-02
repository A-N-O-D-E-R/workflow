# Frequently Asked Questions (FAQ)

Common questions and answers about Simple Workflow engine.

## Table of Contents

- [General Questions](#general-questions)
- [Getting Started](#getting-started)
- [Workflow Design](#workflow-design)
- [Execution & Performance](#execution--performance)
- [Persistence & Data](#persistence--data)
- [Error Handling & Recovery](#error-handling--recovery)
- [Production & Operations](#production--operations)
- [Integration & Compatibility](#integration--compatibility)
- [Troubleshooting](#troubleshooting)

## General Questions

### What is Simple Workflow?

Simple Workflow is a lightweight, embeddable workflow orchestration engine for Java applications. It enables you to define, execute, and manage complex workflows with features like parallel execution, crash recovery, and human task management.

### When should I use Simple Workflow?

Use Simple Workflow when you need:
- **Business process orchestration** within your application
- **Long-running processes** that span minutes, hours, or days
- **Complex workflows** with parallel processing or human approvals
- **Crash-proof execution** with automatic recovery
- **Embedded orchestration** without external infrastructure

### When should I NOT use Simple Workflow?

Don't use Simple Workflow for:
- **Simple sequential tasks** - Use plain Java code instead
- **Very short processes** (< 1 second) - Overhead not justified
- **Real-time event processing** - Use event streaming platforms
- **Microservice choreography** - Use message brokers
- **Data pipelines** - Use ETL tools like Apache Airflow

### How does it compare to Camunda or Temporal?

| Feature | Simple Workflow | Camunda | Temporal |
|---------|----------------|---------|----------|
| Embedded | ✅ Yes | ❌ No (server required) | ❌ No (server required) |
| True Parallel | ✅ Yes | ❌ No | ✅ Yes |
| Lightweight | ✅ Yes (~200KB) | ❌ No (heavy) | ❌ No (heavy) |
| Learning Curve | Easy | Moderate | Steep |
| BPMN Support | ❌ No | ✅ Yes | ❌ No |
| External Dependencies | Minimal | Many | Many |

### Is it production-ready?

Yes, but consider:
- **File-based persistence** is for development only
- Use **database persistence** for production
- Implement proper **monitoring and alerting**
- Follow the [deployment guide](./deployment/README.md)
- Review [best practices](./best-practices/README.md)

### What's the license?

[Check LICENSE file for current license information]

## Getting Started

### How do I add Simple Workflow to my project?

Add the Maven dependency:

```xml
<dependency>
    <groupId>com.anode</groupId>
    <artifactId>workflow</artifactId>
    <version>0.0.1</version>
</dependency>
```

See [Getting Started Guide](./getting-started.md) for complete setup.

### What are the minimum requirements?

- **Java**: 17 or higher
- **Maven/Gradle**: For dependency management
- **Storage**: File system or database for persistence
- **Memory**: Depends on workflow complexity (minimum 512MB heap)

### Where do I start learning?

Follow this path:
1. [Getting Started Guide](./getting-started.md) - 30-minute introduction
2. [First Workflow Tutorial](./tutorials/first-workflow.md) - Hands-on example
3. [Core Concepts](./concepts/README.md) - Understand the fundamentals
4. [Examples](../examples/) - Study working code

### Can I see a quick example?

```java
// 1. Initialize
WorkflowService.init(10, 30000, "-");
CommonService dao = new FileDao("./workflow-data");
RuntimeService rts = WorkflowService.instance()
    .getRunTimeService(dao, factory, handler, null);

// 2. Start workflow
rts.startCase("CASE-001", workflowJson, variables, null);

// 3. Check status
ProcessEntity process = rts.getProcess("CASE-001");
System.out.println("Status: " + process.getStatus());
```

Complete example in [Getting Started](./getting-started.md#your-first-workflow).

## Workflow Design

### How do I define a workflow?

Workflows are defined in JSON:

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

See [Workflow Definition Guide](./getting-started.md#workflow-definition).

### How do I create parallel workflows?

Use different `executionPath` values:

```json
{
  "steps": [
    {"stepId": "step1", "executionPath": "path-a", "order": 1},
    {"stepId": "step2", "executionPath": "path-b", "order": 1},
    {"stepId": "step3", "executionPath": "path-c", "order": 1}
  ]
}
```

All three steps execute concurrently. See [Execution Paths](./concepts/execution-paths.md).

### Can I have conditional workflows?

Yes, implement a `WorkflowRoute`:

```java
public class MyRoute implements WorkflowRoute {
    @Override
    public String route(WorkflowContext context) {
        Double amount = context.getProcessVariable("amount");
        if (amount > 1000) {
            return "high-value-path";
        } else {
            return "standard-path";
        }
    }
}
```

See [Error Handling Patterns](./patterns/error-handling.md) for routing examples.

### What's the maximum workflow size?

There's no hard limit, but consider:
- **Steps**: Hundreds of steps are fine, thousands may be unwieldy
- **Variables**: Keep under 1MB per workflow instance
- **Duration**: Can run for days/weeks/months
- **Complexity**: Balance readability vs. functionality

### Can I nest workflows?

Yes, call workflows from within steps:

```java
public class SubWorkflowStep implements WorkflowStep {
    @Override
    public void execute(WorkflowContext context) throws Exception {
        RuntimeService rts = context.getRuntimeService();
        String subWorkflowJson = loadSubWorkflow();
        rts.startCase("SUB-" + UUID.randomUUID(), subWorkflowJson, vars, null);
    }
}
```

### How do I version workflows?

Use `processDefinitionVersion` in JSON:

```json
{
  "processDefinitionId": "order-process",
  "processDefinitionVersion": "2.0.0"
}
```

Best practices:
- Support multiple versions simultaneously
- Migrate old instances carefully
- Document breaking changes

## Execution & Performance

### How many workflows can run concurrently?

Depends on:
- **Thread pool size**: Configure via `WorkflowService.init(poolSize, ...)`
- **System resources**: CPU, memory, database connections
- **Step complexity**: I/O-bound vs CPU-bound

Example sizing:
- **Development**: 5-10 threads, dozens of workflows
- **Production**: 20-50 threads, hundreds/thousands of workflows

### What's the performance overhead?

Typical overhead:
- **Memory**: ~2-5 KB per workflow instance
- **Latency**: 1-5ms per step (excluding business logic)
- **Throughput**: 100-1000+ workflows/sec (depends on hardware)

See [Performance Tuning](./operations/performance.md).

### How do I optimize performance?

1. **Use parallel execution** where possible
2. **Optimize thread pool size** for your workload
3. **Minimize process variable size**
4. **Use batch database operations**
5. **Cache reference data**
6. **Profile slow steps**

See [Performance Guide](./operations/performance.md) for details.

### Can I run workflows across multiple servers?

Yes! Requirements:
- **Shared database** for persistence
- **Optimistic locking** to prevent conflicts
- **Load balancer** for API endpoints

See [Kubernetes Deployment](./deployment/README.md#kubernetes-deployment).

### What happens if a step times out?

Default timeout is 30 seconds (configurable). On timeout:
1. Step is marked as FAILED
2. Workflow is marked as FAILED
3. `onStepError` event is fired
4. You can retry or handle the failure

Configure timeout:
```java
WorkflowService.init(10, 60000, "-");  // 60 second timeout
```

## Persistence & Data

### What persistence options are available?

**Built-in:**
- **FileDao**: File-based (development only)
- **PostgresDao**: PostgreSQL database

**Custom:**
- Implement `CommonService` interface for any storage
- Examples: MongoDB, DynamoDB, Redis, MySQL

See [Custom DAO Guide](./persistence/custom-dao.md).

### Can I use FileDao in production?

**No**. FileDao is for development/testing only because:
- Not suitable for high concurrency
- No ACID guarantees
- Scalability limitations
- No clustering support

Use database persistence for production.

### How do I switch from File to Database persistence?

1. Implement or use a database DAO:
```java
CommonService dao = new PostgresDao(dataSource);
```

2. Update configuration:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workflow
```

3. Migrate existing data (if needed)

See [PostgreSQL Setup](./persistence/postgres-setup-doc.md).

### Where is workflow data stored?

Depends on persistence:

**FileDao:**
```
workflow-data/
├── processes/
│   └── CASE-001.json
├── steps/
│   └── CASE-001-step1.json
└── variables/
    └── CASE-001.json
```

**Database:**
- `processes` table
- `steps` table
- `variables` table
- `work_items` table
- `milestones` table

### How do I backup workflow data?

**FileDao:**
```bash
tar -czf workflow-backup.tar.gz ./workflow-data/
```

**Database:**
```bash
pg_dump workflow > workflow-backup.sql
```

Schedule regular backups. See [Operations Guide](./operations/README.md#backup-and-recovery).

### Can I query workflow data directly?

Yes, if using database persistence:

```sql
-- Get all active workflows
SELECT * FROM processes WHERE status = 'RUNNING';

-- Get failed workflows
SELECT * FROM processes WHERE status = 'FAILED';

-- Get workflow with steps
SELECT p.*, s.*
FROM processes p
LEFT JOIN steps s ON p.case_id = s.case_id
WHERE p.case_id = 'CASE-001';
```

## Error Handling & Recovery

### What happens when a step fails?

1. Exception is caught by workflow engine
2. Step is marked as FAILED
3. Workflow is marked as FAILED
4. `onStepError` and `onProcessError` events fire
5. State is persisted
6. You can retry or compensate

### How do I retry failed steps?

```java
if (process.getStatus() == ProcessStatus.FAILED) {
    runtimeService.retryFailedStep(caseId);
}
```

For automatic retries, implement in your step:

```java
Integer retryCount = context.getProcessVariable("retryCount");
if (retryCount == null) retryCount = 0;

if (retryCount < MAX_RETRIES) {
    context.setProcessVariable("retryCount", retryCount + 1);
    throw new RetryableException();
}
```

See [Error Handling Patterns](./patterns/error-handling.md).

### What is crash recovery?

If your application crashes mid-workflow:
1. Workflow state is persisted at each step
2. On restart, call `recoverIncompleteProcesses()`
3. Workflows resume from last completed step
4. No data loss, no duplicate work

See [Crash Recovery](./concepts/crash-recovery.md).

### How do I test crash recovery?

```java
// Start workflow
runtimeService.startCase(caseId, workflow, vars, null);

// Simulate crash (create new service instance)
RuntimeService newRts = WorkflowService.instance()
    .getRunTimeService(dao, factory, handler, null);

// Recover
List<ProcessEntity> recovered = newRts.recoverIncompleteProcesses();

// Workflow continues automatically
```

### Can I rollback a workflow?

Implement compensation steps:

```java
// Original step
public class BookFlightStep implements WorkflowStep {
    public void execute(WorkflowContext context) {
        String bookingId = bookFlight();
        context.setProcessVariable("flightBookingId", bookingId);
    }
}

// Compensation step
public class CancelFlightStep implements WorkflowStep {
    public void execute(WorkflowContext context) {
        String bookingId = context.getProcessVariable("flightBookingId");
        cancelFlight(bookingId);
    }
}
```

See [Saga Pattern](./patterns/error-handling.md#saga-pattern).

## Production & Operations

### How do I monitor workflows in production?

1. **Health checks**: `/actuator/health`
2. **Metrics**: `/actuator/metrics`
3. **Custom monitoring**:

```java
@Scheduled(fixedRate = 60000)
public void monitorWorkflows() {
    int active = dao.countActiveProcesses();
    int failed = dao.countFailedProcesses();

    metrics.gauge("workflows.active", active);
    metrics.gauge("workflows.failed", failed);

    if (failed > 10) {
        alertOps("High failure rate!");
    }
}
```

See [Operations Guide](./operations/README.md#monitoring).

### What logs should I collect?

Key logs:
- Workflow start/complete
- Step execution times
- Errors and exceptions
- SLA breaches
- Work basket assignments

Configure in `logback.xml`:
```xml
<logger name="com.anode.workflow" level="INFO"/>
```

### How do I handle production incidents?

1. **Check logs**: Identify the failure
2. **Check database**: Query process status
3. **Retry if transient**: Use retry mechanism
4. **Fix data if needed**: Update process variables
5. **Resume workflow**: Call `retryFailedStep()`

See [Troubleshooting Guide](./operations/README.md#troubleshooting).

### What's the upgrade process?

1. Test in staging
2. Backup production data
3. Plan maintenance window
4. Deploy new version
5. Verify health checks
6. Monitor for issues
7. Rollback if needed

See [CHANGELOG](../CHANGELOG.md) for version changes.

### How do I scale horizontally?

1. Use database persistence (not FileDao)
2. Deploy multiple instances
3. Use load balancer
4. Share database connection
5. Monitor each instance

See [Kubernetes Deployment](./deployment/README.md#kubernetes-deployment).

## Integration & Compatibility

### Does it work with Spring Boot?

Yes! Example configuration:

```java
@Configuration
public class WorkflowConfig {
    @Bean
    public WorkflowService workflowService() {
        WorkflowService.init(10, 30000, "-");
        return WorkflowService.instance();
    }

    @Bean
    public RuntimeService runtimeService(CommonService dao) {
        return workflowService().getRunTimeService(
            dao, factory, handler, null);
    }
}
```

See [Spring Boot Deployment](./deployment/README.md#spring-boot-deployment).

### Can I integrate with message queues?

Yes, trigger workflows from messages:

```java
@KafkaListener(topics = "orders")
public void handleOrder(OrderMessage message) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("orderId", message.getOrderId());

    runtimeService.startCase(
        "ORDER-" + message.getOrderId(),
        workflowJson,
        vars,
        null
    );
}
```

### Does it support microservices?

Yes! Use workflows to orchestrate microservice calls:

```java
public class CallServiceStep implements WorkflowStep {
    @Override
    public void execute(WorkflowContext context) {
        String result = restTemplate.postForObject(
            "http://service-a/api/process",
            request,
            String.class
        );
        context.setProcessVariable("serviceAResult", result);
    }
}
```

### Can I use it with Docker/Kubernetes?

Absolutely! See:
- [Docker Deployment](./deployment/README.md#docker-deployment)
- [Kubernetes Deployment](./deployment/README.md#kubernetes-deployment)

### What databases are supported?

Out of the box:
- PostgreSQL (via PostgresDao)
- File system (FileDao, dev only)

With custom DAO:
- MySQL, MariaDB
- MongoDB
- DynamoDB
- Redis
- Any database with Java driver

See [Custom DAO Guide](./persistence/custom-dao.md).

## Troubleshooting

### Workflows aren't starting. What's wrong?

Check:
1. Thread pool not exhausted: `executor.getActiveCount()`
2. No exceptions in logs
3. Workflow definition is valid JSON
4. ComponentFactory returns non-null steps
5. DAO is properly initialized

### Steps execute but variables aren't persisted

Ensure you're calling:
```java
context.setProcessVariable("key", value);
```

NOT just modifying objects:
```java
// ❌ Wrong - changes not persisted
List items = context.getProcessVariable("items");
items.add("new");

// ✅ Correct - explicitly set variable
List items = context.getProcessVariable("items");
items.add("new");
context.setProcessVariable("items", items);
```

### Getting "Unknown step type" errors

Mismatch between JSON and factory:

```json
// JSON has:
"stepType": "validateOrder"

// Factory must have:
case "validateOrder": return new ValidateOrderStep();
```

Ensure exact match (case-sensitive).

### Workflows hang and never complete

Possible causes:
1. Step has infinite loop
2. Step is waiting on external resource
3. Thread pool exhausted
4. Deadlock in step code

Add timeouts and logging to diagnose.

### High memory usage

Causes:
- Too many concurrent workflows
- Large process variables
- Memory leaks in step code

Solutions:
- Limit concurrent workflows
- Clean up temporary variables
- Profile with heap dump

### Database connection errors

Check:
- Database is running: `pg_isready -h host`
- Credentials are correct
- Connection pool not exhausted
- Network connectivity

See [Deployment Guide](./deployment/README.md#database-setup).

## Still Have Questions?

- 📚 [Full Documentation](./README.md)
- 💬 [GitHub Discussions](https://github.com/A-N-O-D-E-R/workflow/discussions)
- 🐛 [Report Issues](https://github.com/A-N-O-D-E-R/workflow/issues)
- 📧 Email: support@example.com

---

**Didn't find your answer?** Ask in [GitHub Discussions](https://github.com/A-N-O-D-E-R/workflow/discussions) or check the [full documentation](./README.md).
