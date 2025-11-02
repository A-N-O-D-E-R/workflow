# Operations Guide

Production deployment and operational guidelines for Simple Workflow engine.

## Table of Contents

1. [Deployment](#deployment)
2. [Configuration](#configuration)
3. [Monitoring](#monitoring)
4. [Troubleshooting](#troubleshooting)
5. [Backup and Recovery](#backup-and-recovery)
6. [Scaling](#scaling)

## Deployment

### Prerequisites

- Java 17+ JRE
- Sufficient disk space for persistence
- Database (if using RDBMS DAO)
- Monitoring infrastructure

### Deployment Checklist

- [ ] Configure appropriate thread pool size
- [ ] Set up persistence backend
- [ ] Configure logging
- [ ] Set up monitoring and alerts
- [ ] Configure backup strategy
- [ ] Test crash recovery
- [ ] Document runbooks
- [ ] Plan capacity

### Spring Boot Deployment

```java
@Configuration
public class WorkflowConfiguration {

    @Value("${workflow.thread-pool-size:10}")
    private int threadPoolSize;

    @Value("${workflow.step-timeout:30000}")
    private long stepTimeout;

    @Bean
    public WorkflowService workflowService() {
        WorkflowService.init(threadPoolSize, stepTimeout, "-");
        return WorkflowService.instance();
    }

    @Bean
    public RuntimeService runtimeService(
            CommonService dao,
            WorkflowComponantFactory factory,
            EventHandler eventHandler) {
        return workflowService().getRunTimeService(
            dao, factory, eventHandler, null);
    }

    @Bean
    public CommonService dao(DataSource dataSource) {
        return new PostgresDao(dataSource);
    }
}
```

### Docker Deployment

```dockerfile
FROM openjdk:17-slim

WORKDIR /app

COPY target/my-workflow-app.jar app.jar

# Persistence directory
VOLUME /app/workflow-data

# Health check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: workflow-app
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: workflow-app
        image: my-workflow-app:latest
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        env:
        - name: WORKFLOW_THREAD_POOL_SIZE
          value: "10"
        - name: DB_URL
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: url
        volumeMounts:
        - name: workflow-data
          mountPath: /app/workflow-data
      volumes:
      - name: workflow-data
        persistentVolumeClaim:
          claimName: workflow-pvc
```

## Configuration

### Thread Pool Sizing

```java
// CPU-bound workloads
int cpuCores = Runtime.getRuntime().availableProcessors();
WorkflowService.init(cpuCores, 30000, "-");

// I/O-bound workloads
WorkflowService.init(cpuCores * 2, 60000, "-");

// Mixed workloads
WorkflowService.init(cpuCores + 4, 45000, "-");
```

### Logging Configuration

```xml
<!-- logback.xml -->
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/workflow.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/workflow-%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="com.anode.workflow" level="INFO"/>
    <logger name="com.myapp.workflow.steps" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

### Database Configuration

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workflow
    username: workflow_user
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

workflow:
  thread-pool-size: 10
  step-timeout: 30000
```

## Monitoring

### Health Check Endpoint

```java
@RestController
public class WorkflowHealthController {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private CommonService dao;

    @GetMapping("/health/workflow")
    public WorkflowHealth checkHealth() {
        WorkflowHealth health = new WorkflowHealth();

        try {
            // Check active processes
            List<ProcessEntity> active = dao.loadActiveProcesses();
            health.setActiveProcesses(active.size());

            // Check failed processes
            List<ProcessEntity> failed = dao.loadFailedProcesses();
            health.setFailedProcesses(failed.size());

            // Check oldest pending
            Optional<ProcessEntity> oldest = active.stream()
                .min(Comparator.comparing(ProcessEntity::getStartTime));
            oldest.ifPresent(p -> {
                long age = System.currentTimeMillis() -
                    p.getStartTime().getTime();
                health.setOldestPendingAgeMs(age);
            });

            health.setStatus(failed.isEmpty() ? "UP" : "DEGRADED");

        } catch (Exception e) {
            health.setStatus("DOWN");
            health.setError(e.getMessage());
        }

        return health;
    }
}
```

### Metrics Collection

```java
@Component
public class WorkflowMetricsCollector {

    private final MeterRegistry meterRegistry;
    private final CommonService dao;

    public WorkflowMetricsCollector(MeterRegistry meterRegistry,
                                   CommonService dao) {
        this.meterRegistry = meterRegistry;
        this.dao = dao;

        // Register gauges
        Gauge.builder("workflow.processes.active", this,
            c -> getActiveProcessCount())
            .register(meterRegistry);

        Gauge.builder("workflow.processes.failed",
 this,
            c -> getFailedProcessCount())
            .register(meterRegistry);
    }

    private int getActiveProcessCount() {
        try {
            return dao.countActiveProcesses();
        } catch (Exception e) {
            return -1;
        }
    }

    private int getFailedProcessCount() {
        try {
            return dao.countFailedProcesses();
        } catch (Exception e) {
            return -1;
        }
    }

    public void recordStepExecution(String stepType, long durationMs,
                                   boolean success) {
        Timer.builder("workflow.step.duration")
            .tag("stepType", stepType)
            .tag("success", String.valueOf(success))
            .register(meterRegistry)
            .record(Duration.ofMillis(durationMs));
    }
}
```

### Alerts

Set up alerts for:

1. **High failure rate**: > 5% of workflows failing
2. **Stuck workflows**: Workflows running > 24 hours
3. **Thread pool exhaustion**: All threads busy
4. **Database issues**: Connection failures
5. **Disk space**: < 10% free space

### Dashboards

Key metrics to monitor:

- Active workflows count
- Completed workflows/hour
- Failed workflows count
- Average workflow duration
- Step execution times
- Thread pool utilization
- Database connection pool usage

## Troubleshooting

### Common Issues

#### Workflows Not Starting

**Symptoms:**
- `startCase()` returns but no processing happens

**Diagnosis:**
```java
// Check thread pool status
ThreadPoolExecutor executor = (ThreadPoolExecutor)
    workflowService.getExecutor();
System.out.println("Active threads: " + executor.getActiveCount());
System.out.println("Queue size: " + executor.getQueue().size());
```

**Solutions:**
- Increase thread pool size
- Check for deadlocks
- Review step timeouts

#### Steps Timing Out

**Symptoms:**
- Steps fail with timeout errors
- Logs show "Step execution timed out"

**Solutions:**
```java
// Increase timeout
WorkflowService.init(10, 60000, "-");  // 60 second timeout

// Or optimize step logic
public class OptimizedStep implements WorkflowStep {
    public void execute(WorkflowContext context) {
        // Use async operations
        // Batch database calls
        // Cache results
    }
}
```

#### High Memory Usage

**Symptoms:**
- OutOfMemoryError
- Slow performance
- Frequent garbage collection

**Diagnosis:**
```bash
# Check heap usage
jmap -heap <pid>

# Dump heap for analysis
jmap -dump:format=b,file=heap.bin <pid>
```

**Solutions:**
- Reduce variable size
- Increase heap: `-Xmx2g`
- Clean up completed processes
- Implement variable cleanup

#### Database Connection Issues

**Symptoms:**
- "Connection pool exhausted"
- Slow workflow execution

**Solutions:**
```yaml
# Increase connection pool
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
```

### Debug Mode

Enable detailed logging:

```java
@Bean
public EventHandler debugEventHandler() {
    return new EventHandler() {
        @Override
        public void onStepStart(StepEntity step) {
            log.debug("Step starting: {} ({})",
                step.getStepId(), step.getStepType());
        }

        @Override
        public void onStepComplete(StepEntity step) {
            long duration = step.getEndTime().getTime() -
                step.getStartTime().getTime();
            log.debug("Step completed: {} in {}ms",
                step.getStepId(), duration);
        }

        @Override
        public void onStepError(StepEntity step, Exception error) {
            log.error("Step error: {}", step.getStepId(), error);
        }

        // Other methods...
    };
}
```

## Backup and Recovery

### Backup Strategy

```java
@Scheduled(cron = "0 0 * * * *")  // Hourly
public void backupWorkflowData() {
    String timestamp = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String backupPath = "/backups/workflow_" + timestamp;

    try {
        dao.backup(backupPath);
        log.info("Backup created: {}", backupPath);

        // Clean old backups (keep 7 days)
        cleanOldBackups("/backups", 7);

    } catch (Exception e) {
        log.error("Backup failed", e);
        alertOps("Backup failure: " + e.getMessage());
    }
}
```

### Disaster Recovery

```java
public class DisasterRecovery {

    public void recoverFromBackup(String backupPath) {
        log.info("Starting recovery from: {}", backupPath);

        try {
            // Stop accepting new workflows
            workflowService.stopAcceptingNew();

            // Wait for active workflows
            waitForActiveWorkflows(Duration.ofMinutes(5));

            // Restore from backup
            dao.restore(backupPath);

            // Recover incomplete processes
            List<ProcessEntity> recovered =
                runtimeService.recoverIncompleteProcesses();

            log.info("Recovery complete. Recovered {} processes",
                recovered.size());

            // Resume normal operations
            workflowService.resumeAcceptingNew();

        } catch (Exception e) {
            log.error("Recovery failed", e);
            throw new RuntimeException("Recovery failed", e);
        }
    }
}
```

## Scaling

### Vertical Scaling

Increase resources on single instance:

```yaml
# Increase memory
java -Xms2g -Xmx4g -jar app.jar

# Increase thread pool
workflow:
  thread-pool-size: 20
```

### Horizontal Scaling

Run multiple instances:

**Requirements:**
- Shared persistence layer (database)
- Optimistic locking for concurrency
- Load balancer for API endpoints

**Configuration:**
```yaml
# Each instance
workflow:
  instance-id: ${HOSTNAME}
  thread-pool-size: 10

spring:
  datasource:
    url: jdbc:postgresql://shared-db:5432/workflow
```

### Auto-Scaling (Kubernetes)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: workflow-app-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: workflow-app
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

## Related Documentation

- [Performance Tuning](./performance.md) - Optimization guide
- [Architecture](../architecture/README.md) - System architecture
- [Best Practices](../best-practices/README.md) - Operational best practices
