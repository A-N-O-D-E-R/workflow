# Testing Guide

Comprehensive guide to testing workflows built with Simple Workflow engine.

## Table of Contents

1. [Testing Strategy](#testing-strategy)
2. [Unit Testing](#unit-testing)
3. [Integration Testing](#integration-testing)
4. [Testing Patterns](#testing-patterns)
5. [Mocking and Stubs](#mocking-and-stubs)
6. [Test Data Management](#test-data-management)
7. [Performance Testing](#performance-testing)

## Testing Strategy

### Testing Pyramid for Workflows

```
         ▲
        ╱ ╲
       ╱ E2E╲         End-to-end tests (few)
      ╱─────╲
     ╱       ╲
    ╱  Integ. ╲       Integration tests (some)
   ╱───────────╲
  ╱             ╲
 ╱  Unit Tests   ╲    Unit tests (many)
╱─────────────────╲
```

**Unit Tests (70%):**
- Test individual steps in isolation
- Test routes and business logic
- Fast execution, no external dependencies

**Integration Tests (25%):**
- Test complete workflows end-to-end
- Test with real persistence layer
- Test error recovery scenarios

**End-to-End Tests (5%):**
- Test complete system integration
- Test with external dependencies
- Test production-like scenarios

## Unit Testing

### Testing Workflow Steps

```java
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class ValidateOrderStepTest {

    @Test
    public void testValidOrder() throws Exception {
        // Arrange
        ValidateOrderStep step = new ValidateOrderStep();
        WorkflowContext context = mock(WorkflowContext.class);

        Order order = new Order("ORD-123", 299.99);
        order.setCustomerId("CUST-001");
        order.setItems(List.of(new OrderItem("ITEM-001", 2)));

        when(context.getProcessVariable("order")).thenReturn(order);

        // Act
        step.execute(context);

        // Assert
        verify(context).setProcessVariable("orderValid", true);
        verify(context, never()).setProcessVariable(eq("orderValid"), eq(false));
    }

    @Test
    public void testInvalidOrder_MissingCustomer() throws Exception {
        // Arrange
        ValidateOrderStep step = new ValidateOrderStep();
        WorkflowContext context = mock(WorkflowContext.class);

        Order order = new Order("ORD-123", 299.99);
        // Missing customer ID

        when(context.getProcessVariable("order")).thenReturn(order);

        // Act & Assert
        assertThrows(ValidationException.class, () -> {
            step.execute(context);
        });
    }

    @Test
    public void testOrderValidation_StoresErrors() throws Exception {
        // Arrange
        ValidateOrderStep step = new ValidateOrderStep();
        WorkflowContext context = mock(WorkflowContext.class);

        Order order = new Order("ORD-123", 0.0);  // Invalid amount

        when(context.getProcessVariable("order")).thenReturn(order);

        // Act
        step.execute(context);

        // Assert
        verify(context).setProcessVariable("orderValid", false);
        verify(context).setProcessVariable(eq("validationErrors"), anyList());
    }
}
```

### Testing Routes

```java
public class ApprovalRouteTest {

    @Test
    public void testHighValueOrder_ManagerApproval() throws Exception {
        // Arrange
        ApprovalRoute route = new ApprovalRoute();
        WorkflowContext context = mock(WorkflowContext.class);

        when(context.getProcessVariable("orderAmount")).thenReturn(10000.0);

        // Act
        String path = route.route(context);

        // Assert
        assertEquals("manager-approval", path);
    }

    @Test
    public void testLowValueOrder_AutoApprove() throws Exception {
        // Arrange
        ApprovalRoute route = new ApprovalRoute();
        WorkflowContext context = mock(WorkflowContext.class);

        when(context.getProcessVariable("orderAmount")).thenReturn(500.0);

        // Act
        String path = route.route(context);

        // Assert
        assertEquals("auto-approve", path);
    }
}
```

### Testing Event Handlers

```java
public class OrderEventHandlerTest {

    @Test
    public void testProcessComplete_SendsNotification() {
        // Arrange
        NotificationService notificationService = mock(NotificationService.class);
        OrderEventHandler handler = new OrderEventHandler(notificationService);

        ProcessEntity process = new ProcessEntity();
        process.setProcessId("CASE-001");
        process.setStatus(ProcessStatus.COMPLETED);

        Map<String, Object> vars = new HashMap<>();
        vars.put("customerEmail", "customer@example.com");
        vars.put("orderId", "ORD-123");
        process.setProcessVariables(vars);

        // Act
        handler.onProcessComplete(process);

        // Assert
        verify(notificationService).sendEmail(
            eq("customer@example.com"),
            contains("ORD-123")
        );
    }
}
```

## Integration Testing

### Testing Complete Workflows

```java
@SpringBootTest
public class OrderWorkflowIntegrationTest {

    private RuntimeService runtimeService;
    private CommonService dao;

    @BeforeEach
    public void setup() {
        // Initialize workflow service
        WorkflowService.init(5, 30000, "-");

        // Use in-memory DAO for testing
        dao = new InMemoryDao();

        WorkflowComponantFactory factory = new OrderComponentFactory();
        EventHandler handler = new OrderEventHandler();

        runtimeService = WorkflowService.instance()
            .getRunTimeService(dao, factory, handler, null);
    }

    @Test
    public void testSuccessfulOrderWorkflow() throws Exception {
        // Arrange
        String caseId = "TEST-CASE-" + UUID.randomUUID();
        String workflowJson = loadWorkflowDefinition("order-workflow.json");

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", "ORD-123");
        variables.put("customerId", "CUST-001");
        variables.put("orderAmount", 299.99);

        // Act
        runtimeService.startCase(caseId, workflowJson, variables, null);

        // Wait for completion
        await().atMost(10, SECONDS)
            .until(() -> isWorkflowComplete(caseId));

        // Assert
        ProcessEntity process = runtimeService.getProcess(caseId);
        assertEquals(ProcessStatus.COMPLETED, process.getStatus());

        // Verify expected variables
        Map<String, Object> resultVars = process.getProcessVariables();
        assertTrue((Boolean) resultVars.get("orderValid"));
        assertTrue((Boolean) resultVars.get("paymentProcessed"));
        assertNotNull(resultVars.get("shipmentId"));
    }

    @Test
    public void testOrderWorkflow_PaymentFailure() throws Exception {
        // Arrange
        String caseId = "TEST-CASE-" + UUID.randomUUID();
        String workflowJson = loadWorkflowDefinition("order-workflow.json");

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", "ORD-999");  // This order will fail payment
        variables.put("orderAmount", 999999.99);

        // Act
        runtimeService.startCase(caseId, workflowJson, variables, null);

        // Wait for failure
        await().atMost(10, SECONDS)
            .until(() -> isWorkflowFailed(caseId));

        // Assert
        ProcessEntity process = runtimeService.getProcess(caseId);
        assertEquals(ProcessStatus.FAILED, process.getStatus());
        assertNotNull(process.getErrorMessage());
    }

    private boolean isWorkflowComplete(String caseId) {
        try {
            ProcessEntity process = runtimeService.getProcess(caseId);
            return process.getStatus() == ProcessStatus.COMPLETED;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### Testing Parallel Execution

```java
@Test
public void testParallelExecution() throws Exception {
    // Arrange
    String caseId = "PARALLEL-TEST-" + UUID.randomUUID();
    String workflowJson = loadWorkflowDefinition("parallel-workflow.json");

    Map<String, Object> variables = new HashMap<>();
    variables.put("orderId", "ORD-123");

    long startTime = System.currentTimeMillis();

    // Act
    runtimeService.startCase(caseId, workflowJson, variables, null);

    await().atMost(15, SECONDS)
        .until(() -> isWorkflowComplete(caseId));

    long duration = System.currentTimeMillis() - startTime;

    // Assert
    ProcessEntity process = runtimeService.getProcess(caseId);
    assertEquals(ProcessStatus.COMPLETED, process.getStatus());

    // Verify parallel execution saved time
    // (3 steps at 5 seconds each = 5 seconds parallel, not 15 sequential)
    assertTrue(duration < 8000, "Should complete in ~5 seconds, not 15");

    // Verify all paths completed
    List<StepEntity> steps = runtimeService.getStepHistory(caseId);
    assertEquals(3, steps.size());
    assertTrue(steps.stream().allMatch(s -> s.getStatus() == StepStatus.COMPLETED));
}
```

### Testing Crash Recovery

```java
@Test
public void testCrashRecovery() throws Exception {
    // Arrange - Start a long-running workflow
    String caseId = "CRASH-TEST-" + UUID.randomUUID();
    String workflowJson = loadWorkflowDefinition("long-workflow.json");

    runtimeService.startCase(caseId, workflowJson, new HashMap<>(), null);

    // Wait for first step to complete
    Thread.sleep(2000);

    // Act - Simulate crash by creating new service instance
    WorkflowService.init(5, 30000, "-");
    RuntimeService newRuntimeService = WorkflowService.instance()
        .getRunTimeService(dao, factory, handler, null);

    // Recover
    List<ProcessEntity> recovered = newRuntimeService.recoverIncompleteProcesses();

    // Wait for completion
    await().atMost(20, SECONDS)
        .until(() -> isWorkflowComplete(caseId));

    // Assert
    assertTrue(recovered.size() > 0);
    ProcessEntity process = newRuntimeService.getProcess(caseId);
    assertEquals(ProcessStatus.COMPLETED, process.getStatus());
}
```

## Testing Patterns

### Using Test Fixtures

```java
public class WorkflowTestFixtures {

    public static Map<String, Object> createOrderVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("orderId", "ORD-" + UUID.randomUUID());
        vars.put("customerId", "CUST-001");
        vars.put("orderAmount", 299.99);
        vars.put("items", List.of(
            Map.of("sku", "ITEM-001", "quantity", 2),
            Map.of("sku", "ITEM-002", "quantity", 1)
        ));
        return vars;
    }

    public static String loadWorkflowDefinition(String name) throws IOException {
        return Files.readString(
            Paths.get("src/test/resources/workflows/" + name));
    }

    public static WorkflowContext createMockContext() {
        WorkflowContext context = mock(WorkflowContext.class);
        when(context.getCaseId()).thenReturn("TEST-CASE-001");
        return context;
    }
}
```

### Builder Pattern for Test Data

```java
public class OrderBuilder {
    private String orderId = "ORD-123";
    private String customerId = "CUST-001";
    private double amount = 100.0;
    private List<OrderItem> items = new ArrayList<>();

    public OrderBuilder withOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public OrderBuilder withAmount(double amount) {
        this.amount = amount;
        return this;
    }

    public OrderBuilder withItem(String sku, int quantity) {
        items.add(new OrderItem(sku, quantity));
        return this;
    }

    public Order build() {
        Order order = new Order(orderId, amount);
        order.setCustomerId(customerId);
        order.setItems(items);
        return order;
    }
}

// Usage in tests
@Test
public void testWithBuilder() {
    Order order = new OrderBuilder()
        .withOrderId("ORD-999")
        .withAmount(5000.0)
        .withItem("ITEM-001", 2)
        .withItem("ITEM-002", 3)
        .build();

    // Test with order
}
```

## Mocking and Stubs

### Mocking External Services

```java
public class PaymentStepTest {

    private PaymentService paymentService;
    private PaymentStep step;

    @BeforeEach
    public void setup() {
        paymentService = mock(PaymentService.class);
        step = new PaymentStep(paymentService);
    }

    @Test
    public void testSuccessfulPayment() throws Exception {
        // Arrange
        WorkflowContext context = WorkflowTestFixtures.createMockContext();
        when(context.getProcessVariable("orderAmount")).thenReturn(299.99);

        when(paymentService.charge(any(), anyDouble()))
            .thenReturn("PAYMENT-123");

        // Act
        step.execute(context);

        // Assert
        verify(paymentService).charge(any(), eq(299.99));
        verify(context).setProcessVariable("paymentId", "PAYMENT-123");
    }

    @Test
    public void testPaymentFailure() throws Exception {
        // Arrange
        WorkflowContext context = WorkflowTestFixtures.createMockContext();
        when(context.getProcessVariable("orderAmount")).thenReturn(299.99);

        when(paymentService.charge(any(), anyDouble()))
            .thenThrow(new PaymentDeclinedException("Insufficient funds"));

        // Act & Assert
        assertThrows(PaymentDeclinedException.class, () -> {
            step.execute(context);
        });

        verify(context).setProcessVariable("paymentError", "Insufficient funds");
    }
}
```

### Stub DAO for Testing

```java
public class InMemoryDao implements CommonService {

    private Map<String, ProcessEntity> processes = new ConcurrentHashMap<>();
    private Map<String, List<StepEntity>> steps = new ConcurrentHashMap<>();
    private Map<String, Map<String, Object>> variables = new ConcurrentHashMap<>();

    @Override
    public void saveProcess(ProcessEntity process) {
        processes.put(process.getCaseId(), process);
    }

    @Override
    public ProcessEntity loadProcess(String caseId) {
        return processes.get(caseId);
    }

    @Override
    public void saveStep(StepEntity step) {
        steps.computeIfAbsent(step.getCaseId(), k -> new ArrayList<>()).add(step);
    }

    @Override
    public List<StepEntity> loadSteps(String caseId) {
        return steps.getOrDefault(caseId, Collections.emptyList());
    }

    // Implement other methods...

    // Test helper methods
    public void clear() {
        processes.clear();
        steps.clear();
        variables.clear();
    }

    public int getProcessCount() {
        return processes.size();
    }
}
```

## Test Data Management

### Using Test Containers (for RDBMS)

```java
@Testcontainers
public class DatabaseWorkflowTest {

    @Container
    public static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName("workflow_test")
            .withUsername("test")
            .withPassword("test");

    private DataSource dataSource;
    private CommonService dao;

    @BeforeEach
    public void setup() {
        dataSource = createDataSource(postgres);
        dao = new PostgresDao(dataSource);
    }

    @Test
    public void testWithRealDatabase() throws Exception {
        // Test with actual PostgreSQL
        RuntimeService rts = WorkflowService.instance()
            .getRunTimeService(dao, factory, handler, null);

        // Run tests...
    }
}
```

### Cleaning Up Test Data

```java
@AfterEach
public void cleanup() {
    // Clean up test data
    dao.deleteAllProcesses();

    // Reset mocks
    Mockito.reset(paymentService, emailService);

    // Clear caches
    cacheService.clearAll();
}
```

## Performance Testing

### Load Testing Workflows

```java
@Test
public void testConcurrentWorkflows() throws Exception {
    int concurrentWorkflows = 100;
    CountDownLatch latch = new CountDownLatch(concurrentWorkflows);

    ExecutorService executor = Executors.newFixedThreadPool(20);

    long startTime = System.currentTimeMillis();

    for (int i = 0; i < concurrentWorkflows; i++) {
        final int index = i;
        executor.submit(() -> {
            try {
                String caseId = "LOAD-TEST-" + index;
                runtimeService.startCase(caseId, workflowJson, variables, null);
            } finally {
                latch.countDown();
            }
        });
    }

    // Wait for all to complete
    latch.await(60, TimeUnit.SECONDS);

    long duration = System.currentTimeMillis() - startTime;

    System.out.println(concurrentWorkflows + " workflows completed in " +
        duration + "ms");
    System.out.println("Throughput: " +
        (concurrentWorkflows * 1000.0 / duration) + " workflows/sec");

    // Verify all completed
    for (int i = 0; i < concurrentWorkflows; i++) {
        String caseId = "LOAD-TEST-" + i;
        ProcessEntity process = runtimeService.getProcess(caseId);
        assertEquals(ProcessStatus.COMPLETED, process.getStatus());
    }
}
```

## Related Documentation

- [Best Practices](../best-practices/README.md) - Testing best practices
- [Examples](../../examples/) - Example test suites
- [Performance Tuning](../operations/performance.md) - Performance optimization
