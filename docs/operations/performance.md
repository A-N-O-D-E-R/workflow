# Performance Tuning Guide

Comprehensive guide to optimizing Simple Workflow engine performance.

## Table of Contents

1. [Performance Profiling](#performance-profiling)
2. [Thread Pool Optimization](#thread-pool-optimization)
3. [Persistence Optimization](#persistence-optimization)
4. [Memory Management](#memory-management)
5. [Parallel Execution](#parallel-execution)
6. [Caching Strategies](#caching-strategies)
7. [Benchmarking](#benchmarking)

## Performance Profiling

### Identifying Bottlenecks

```java
@Component
public class PerformanceProfiler {

    public void profileWorkflow(String caseId) {
        RuntimeService rts = getRuntimeService();

        // Get step history
        List<StepEntity> steps = rts.getStepHistory(caseId);

        System.out.println("Performance Profile for " + caseId);
        System.out.println("===============================");

        for (StepEntity step : steps) {
            long duration = step.getEndTime().getTime() -
                step.getStartTime().getTime();

            System.out.printf("%s: %dms%n", step.getStepId(), duration);
        }

        // Identify slowest steps
        StepEntity slowest = steps.stream()
            .max(Comparator.comparing(s ->
                s.getEndTime().getTime() - s.getStartTime().getTime()))
            .orElse(null);

        if (slowest != null) {
            long slowestDuration = slowest.getEndTime().getTime() -
                slowest.getStartTime().getTime();
            System.out.printf("Slowest step: %s (%dms)%n",
                slowest.getStepId(), slowestDuration);
        }
    }
}
```

### Measuring Throughput

```java
@Test
public void measureThroughput() throws Exception {
    int workflowCount = 1000;
    CountDownLatch latch = new CountDownLatch(workflowCount);

    long startTime = System.currentTimeMillis();

    for (int i = 0; i < workflowCount; i++) {
        String caseId = "PERF-" + i;
        executor.submit(() -> {
            try {
                runtimeService.startCase(caseId, workflowJson, vars, null);
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();
    long endTime = System.currentTimeMillis();

    double duration = (endTime - startTime) / 1000.0;
    double throughput = workflowCount / duration;

    System.out.printf("Processed %d workflows in %.2fs%n",
        workflowCount, duration);
    System.out.printf("Throughput: %.2f workflows/sec%n", throughput);
}
```

## Thread Pool Optimization

### Sizing Guidelines

**CPU-Bound Workloads:**
```java
// Formula: Number of CPU cores
int optimalSize = Runtime.getRuntime().availableProcessors();
WorkflowService.init(optimalSize, 30000, "-");
```

**I/O-Bound Workloads:**
```java
// Formula: 2 * Number of CPU cores
int optimalSize = Runtime.getRuntime().availableProcessors() * 2;
WorkflowService.init(optimalSize, 60000, "-");
```

**Mixed Workloads:**
```java
// Formula: Cores + (Cores / 2)
int cores = Runtime.getRuntime().availableProcessors();
int optimalSize = cores + (cores / 2);
WorkflowService.init(optimalSize, 45000, "-");
```

### Dynamic Thread Pool Adjustment

```java
public class AdaptiveThreadPool {

    private static final int MIN_THREADS = 5;
    private static final int MAX_THREADS = 50;

    @Scheduled(fixedRate = 60000)  // Every minute
    public void adjustThreadPool() {
        int activeProcesses = dao.countActiveProcesses();
        int currentThreads = getCurrentThreadPoolSize();

        int desiredThreads;
        if (activeProcesses > currentThreads * 2) {
            // Scale up
            desiredThreads = Math.min(activeProcesses, MAX_THREADS);
        } else if (activeProcesses < currentThreads / 2) {
            // Scale down
            desiredThreads = Math.max(activeProcesses + 2, MIN_THREADS);
        } else {
            return;  // No change needed
        }

        if (desiredThreads != currentThreads) {
            log.info("Adjusting thread pool: {} -> {}",
                currentThreads, desiredThreads);
            resizeThreadPool(desiredThreads);
        }
    }
}
```

### Thread Pool Monitoring

```java
public class ThreadPoolMonitor {

    @Scheduled(fixedRate = 10000)  // Every 10 seconds
    public void monitorThreadPool() {
        ThreadPoolExecutor executor = getWorkflowExecutor();

        int activeThreads = executor.getActiveCount();
        int poolSize = executor.getPoolSize();
        int queueSize = executor.getQueue().size();
        long completedTasks = executor.getCompletedTaskCount();

        log.info("Thread Pool Stats: active={}, pool={}, queue={}, completed={}",
            activeThreads, poolSize, queueSize, completedTasks);

        // Alert if thread pool is saturated
        if (activeThreads >= poolSize * 0.9) {
            log.warn("Thread pool near capacity! Consider increasing size.");
        }

        // Alert if queue is backing up
        if (queueSize > 100) {
            log.warn("Work queue backing up: {} items", queueSize);
        }
    }
}
```

## Persistence Optimization

### Batch Persistence

```java
public class BatchPersistenceDao implements CommonService {

    private static final int BATCH_SIZE = 100;
    private final List<ProcessEntity> processBuffer = new ArrayList<>();
    private final List<StepEntity> stepBuffer = new ArrayList<>();

    @Override
    public synchronized void saveProcess(ProcessEntity process) {
        processBuffer.add(process);

        if (processBuffer.size() >= BATCH_SIZE) {
            flushProcesses();
        }
    }

    private void flushProcesses() {
        if (processBuffer.isEmpty()) return;

        // Batch insert
        jdbcTemplate.batchUpdate(
            "INSERT INTO processes (...) VALUES (...)",
            processBuffer,
            BATCH_SIZE,
            (ps, process) -> {
                // Set parameters
            });

        processBuffer.clear();
    }

    @Scheduled(fixedRate = 5000)  // Flush every 5 seconds
    public void scheduledFlush() {
        flushProcesses();
        flushSteps();
    }
}
```

### Connection Pooling

```yaml
spring:
  datasource:
    hikari:
      # Optimize pool size
      maximum-pool-size: 20
      minimum-idle: 5

      # Connection lifecycle
      max-lifetime: 1800000  # 30 minutes
      idle-timeout: 600000   # 10 minutes

      # Connection testing
      connection-test-query: SELECT 1
      validation-timeout: 5000

      # Performance tuning
      leak-detection-threshold: 60000
```

### Database Indexing

```sql
-- Index for loading processes by status
CREATE INDEX idx_processes_status ON processes(status);

-- Index for loading steps by case
CREATE INDEX idx_steps_case_id ON steps(case_id);

-- Index for work items by basket
CREATE INDEX idx_work_items_basket ON work_items(basket_name)
    WHERE claimed_by IS NULL;

-- Composite index for SLA queries
CREATE INDEX idx_milestones_case_breach
    ON milestones(case_id, is_breached);
```

### Query Optimization

```java
// ❌ Bad: N+1 queries
public List<ProcessEntity> loadProcessesWithSteps() {
    List<ProcessEntity> processes = loadAllProcesses();
    for (ProcessEntity process : processes) {
        List<StepEntity> steps = loadSteps(process.getCaseId());
        process.setSteps(steps);
    }
    return processes;
}

// ✅ Good: Join query
public List<ProcessEntity> loadProcessesWithSteps() {
    return jdbcTemplate.query(
        "SELECT p.*, s.* FROM processes p " +
        "LEFT JOIN steps s ON p.case_id = s.case_id",
        new ProcessStepRowMapper()
    );
}
```

## Memory Management

### Minimize Variable Size

```java
// ❌ Bad: Storing large objects
public class BadStep implements WorkflowStep {
    public void execute(WorkflowContext context) {
        byte[] largeData = loadLargeFile();  // 100 MB
        context.setProcessVariable("data", largeData);
    }
}

// ✅ Good: Store reference
public class GoodStep implements WorkflowStep {
    public void execute(WorkflowContext context) {
        byte[] largeData = loadLargeFile();

        // Upload to external storage
        String fileId = storageService.upload(largeData);

        // Store only reference
        context.setProcessVariable("fileId", fileId);
        context.setProcessVariable("fileSize", largeData.length);
    }
}
```

### Variable Cleanup

```java
public class CleanupStep implements WorkflowStep {
    public void execute(WorkflowContext context) {
        // Remove temporary variables
        context.removeProcessVariable("temp_apiResponse");
        context.removeProcessVariable("temp_validationResult");
        context.removeProcessVariable("temp_intermediateData");

        // Keep only essential data
        Map<String, Object> essentialData = new HashMap<>();
        essentialData.put("orderId",
            context.getProcessVariable("orderId"));
        essentialData.put("finalStatus",
            context.getProcessVariable("status"));

        // Clear all and set essential
        context.clearProcessVariables();
        context.setAllProcessVariables(essentialData);
    }
}
```

### Memory Monitoring

```java
@Scheduled(fixedRate = 30000)  // Every 30 seconds
public void monitorMemory() {
    Runtime runtime = Runtime.getRuntime();

    long maxMemory = runtime.maxMemory();
    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    long usedMemory = totalMemory - freeMemory;

    double usedPercent = (usedMemory * 100.0) / maxMemory;

    log.info("Memory: used={}MB, free={}MB, total={}MB, max={}MB ({}%)",
        usedMemory / 1024 / 1024,
        freeMemory / 1024 / 1024,
        totalMemory / 1024 / 1024,
        maxMemory / 1024 / 1024,
        String.format("%.2f", usedPercent));

    // Alert if memory usage is high
    if (usedPercent > 90) {
        log.warn("High memory usage: {}%", usedPercent);
        // Trigger garbage collection
        System.gc();
    }
}
```

## Parallel Execution

### Optimize Execution Paths

```json
{
  "steps": [
    // Sequential prerequisite
    {"stepId": "load-order", "executionPath": "main", "order": 1},

    // Parallel independent operations
    {"stepId": "validate-customer", "executionPath": "validation", "order": 2},
    {"stepId": "check-inventory", "executionPath": "inventory", "order": 2},
    {"stepId": "calculate-shipping", "executionPath": "shipping", "order": 2},
    {"stepId": "apply-discounts", "executionPath": "pricing", "order": 2},

    // Sequential final operation
    {"stepId": "confirm-order", "executionPath": "main", "order": 3}
  ]
}
```

### Avoid False Parallelism

```java
// ❌ Bad: Steps have hidden dependencies
{
  "steps": [
    {"stepId": "step1", "executionPath": "path-a", "order": 1},
    {"stepId": "step2", "executionPath": "path-b", "order": 1}
  ]
}

// Step2 reads variable set by Step1
// This creates race condition!

// ✅ Good: Make dependency explicit
{
  "steps": [
    {"stepId": "step1", "executionPath": "main", "order": 1},
    {"stepId": "step2", "executionPath": "main", "order": 2}
  ]
}
```

### Measure Parallel Speedup

```java
@Test
public void measureParallelSpeedup() throws Exception {
    // Test sequential execution
    long sequentialTime = measureWorkflow("sequential-workflow.json");

    // Test parallel execution
    long parallelTime = measureWorkflow("parallel-workflow.json");

    double speedup = (double) sequentialTime / parallelTime;

    System.out.printf("Sequential: %dms%n", sequentialTime);
    System.out.printf("Parallel: %dms%n", parallelTime);
    System.out.printf("Speedup: %.2fx%n", speedup);

    // Theoretical maximum is number of parallel paths
    int parallelPaths = 4;
    double efficiency = speedup / parallelPaths * 100;
    System.out.printf("Parallel efficiency: %.2f%%%n", efficiency);
}
```

## Caching Strategies

### Cache Workflow Definitions

```java
@Component
public class WorkflowDefinitionCache {

    private final LoadingCache<String, String> cache = CacheBuilder.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build(new CacheLoader<String, String>() {
            @Override
            public String load(String definitionId) throws Exception {
                return loadWorkflowDefinition(definitionId);
            }
        });

    public String getDefinition(String definitionId) {
        try {
            return cache.get(definitionId);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to load definition", e);
        }
    }
}
```

### Cache Reference Data

```java
public class ReferenceDataStep implements WorkflowStep {

    @Cacheable(value = "customerData", key = "#customerId")
    private Customer loadCustomer(String customerId) {
        return customerService.load(customerId);
    }

    @Override
    public void execute(WorkflowContext context) {
        String customerId = context.getProcessVariable("customerId");

        // Load from cache (or database if not cached)
        Customer customer = loadCustomer(customerId);

        context.setProcessVariable("customerName", customer.getName());
        context.setProcessVariable("customerTier", customer.getTier());
    }
}
```

### Local Step-Level Caching

```java
public class OptimizedStep implements WorkflowStep {

    // Cache expensive calculations within step execution
    private final Map<String, Object> localCache = new ConcurrentHashMap<>();

    @Override
    public void execute(WorkflowContext context) {
        String key = "expensiveCalculation";

        Object result = localCache.computeIfAbsent(key, k -> {
            // Expensive operation only executed once
            return performExpensiveCalculation();
        });

        context.setProcessVariable("result", result);
    }
}
```

## Benchmarking

### Comprehensive Benchmark Suite

```java
@State(Scope.Benchmark)
public class WorkflowBenchmark {

    private RuntimeService runtimeService;
    private String workflowJson;

    @Setup
    public void setup() {
        WorkflowService.init(10, 30000, "-");
        CommonService dao = new InMemoryDao();
        runtimeService = WorkflowService.instance()
            .getRunTimeService(dao, factory, handler, null);
        workflowJson = loadWorkflowDefinition();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void testWorkflowThroughput() throws Exception {
        String caseId = "BENCH-" + UUID.randomUUID();
        runtimeService.startCase(caseId, workflowJson, variables, null);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testWorkflowLatency() throws Exception {
        String caseId = "BENCH-" + UUID.randomUUID();
        runtimeService.startCase(caseId, workflowJson, variables, null);

        // Wait for completion
        await().until(() -> isComplete(caseId));
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(WorkflowBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(5)
            .measurementIterations(10)
            .build();

        new Runner(opt).run();
    }
}
```

### Performance Baseline

Establish performance baselines:

```
Baseline Performance (2023-11-02):
- Simple workflow (5 sequential steps): 50ms
- Parallel workflow (4 paths, 3 steps each): 150ms
- Complex workflow (10 steps, 2 paths): 250ms
- Throughput: 200 workflows/sec (10 threads)
- Memory per workflow: ~2 KB
```

## Related Documentation

- [Architecture](../architecture/README.md) - Understanding the engine
- [Best Practices](../best-practices/README.md) - Performance best practices
- [Operations Guide](./README.md) - Deployment and operations
