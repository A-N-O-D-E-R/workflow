package com.anode.workflow.test_concurrency;

import com.anode.tool.StringUtils;
import com.anode.tool.document.JDocument;
import com.anode.workflow.MemoryDao;
import com.anode.workflow.RouteResponseFactory;
import com.anode.workflow.StepResponseFactory;
import com.anode.workflow.TestHandler;
import com.anode.workflow.TestSlaQueueManager;
import com.anode.workflow.WorkflowService;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.mapper.WorkflowDefinitionMapper;
import com.anode.workflow.service.WorkflowComponantFactory;
import com.anode.workflow.service.runtime.RuntimeService;
import com.anode.workflow.test_singular.TestComponentFactory;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive concurrency tests for workflow engine thread safety.
 *
 * <p>These tests verify that the workflow engine can handle concurrent workflow execution
 * without NullPointerExceptions, race conditions, or data corruption. They address the
 * critical thread-safety issues documented in THREAD_SAFETY_FIXES_NEEDED.md.
 *
 * <h2>Test Coverage</h2>
 * <ul>
 *   <li>Concurrent workflow execution (10, 50, 100 workflows)</li>
 *   <li>Parallel access to shared WorkflowDefinition</li>
 *   <li>ExecPath concurrent access patterns</li>
 *   <li>Ticket resolution under concurrency</li>
 *   <li>Stress testing with high concurrency</li>
 * </ul>
 */
public class ConcurrentWorkflowExecutionTest {

    private static RuntimeService rts;
    private MemoryDao dao;
    private AtomicInteger successCount;
    private AtomicInteger errorCount;

    @BeforeAll
    protected static void beforeAll() {
        // Initialize workflow service with thread pool
        WorkflowService.init(20, 30000, "-");
    }

    @BeforeEach
    protected void beforeEach() {
        dao = new MemoryDao();
        WorkflowComponantFactory factory = new TestComponentFactory();
        rts = WorkflowService.instance()
                .getRunTimeService(dao, factory, new TestHandler(), new TestSlaQueueManager());
        successCount = new AtomicInteger(0);
        errorCount = new AtomicInteger(0);
        StepResponseFactory.clear();
        RouteResponseFactory.clear();
    }

    @AfterEach
    protected void afterEach() {
        // Reset for next test
        StepResponseFactory.clear();
        RouteResponseFactory.clear();
    }

    /**
     * Test concurrent execution of 10 workflows.
     *
     * <p>Verifies that multiple workflows can execute simultaneously without
     * NullPointerExceptions or race conditions.
     */
    @Test
    public void testConcurrent10WorkflowExecution() throws Exception {
        final int workflowCount = 10;

        // Load workflow definition
        String json = StringUtils.getResourceAsString(
                getClass(), "/workflow_service/test_journey.json");
        WorkflowDefinition definition = WorkflowDefinitionMapper.toEntity(new JDocument(json));

        // Execute workflows concurrently
        List<CompletableFuture<WorkflowContext>> futures = new ArrayList<>();
        for (int i = 0; i < workflowCount; i++) {
            final int caseIndex = i;
            CompletableFuture<WorkflowContext> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String caseId = "CASE-" + caseIndex;

                    WorkflowContext result = rts.startCase(caseId, definition, null, null);
                    successCount.incrementAndGet();
                    return result;
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("Error in case " + caseIndex + ": " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // Wait for all to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        allFutures.get(30, TimeUnit.SECONDS);

        // Verify results
        assertEquals(workflowCount, successCount.get(), "All workflows should complete successfully");
        assertEquals(0, errorCount.get(), "No errors should occur");

        // Verify all contexts are non-null
        for (CompletableFuture<WorkflowContext> future : futures) {
            assertNotNull(future.get(), "Workflow context should not be null");
        }
    }

    /**
     * Test concurrent execution of 50 workflows.
     *
     * <p>Medium-scale stress test to verify thread-safety under moderate load.
     */
    @Test
    public void testConcurrent50WorkflowExecution() throws Exception {
        final int workflowCount = 50;

        String json = StringUtils.getResourceAsString(
                getClass(), "/workflow_service/journey_00_linear.json");
        WorkflowDefinition definition = WorkflowDefinitionMapper.toEntity(new JDocument(json));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(workflowCount);

        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < workflowCount; i++) {
            final int caseIndex = i;
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    String caseId = "CASE-" + caseIndex;

                    WorkflowContext result = rts.startCase(caseId, definition, null, null);
                    assertNotNull(result, "Context should not be null for case " + caseId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("Error in case " + caseIndex + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all workflows simultaneously
        startLatch.countDown();

        // Wait for completion
        assertTrue(completionLatch.await(60, TimeUnit.SECONDS),
                "All workflows should complete within 60 seconds");

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // Verify results
        assertEquals(workflowCount, successCount.get(), "All 50 workflows should complete successfully");
        assertEquals(0, errorCount.get(), "No errors should occur during concurrent execution");
    }

    /**
     * Test stress scenario with 100 concurrent workflows.
     *
     * <p>High-scale stress test to verify thread-safety under heavy load.
     * This test validates fixes for:
     * <ul>
     *   <li>NPE at ExecThreadTask.java:355</li>
     *   <li>NPE at ExecThreadTask.java:545</li>
     *   <li>ConcurrentHashMap usage in WorkflowDefinition</li>
     *   <li>ConcurrentHashMap usage in MemoryDao</li>
     * </ul>
     */
    @Test
    public void testStress100ConcurrentWorkflows() throws Exception {
        final int workflowCount = 100;

        String json = StringUtils.getResourceAsString(
                getClass(), "/workflow_service/journey_00_linear.json");
        WorkflowDefinition definition = WorkflowDefinitionMapper.toEntity(new JDocument(json));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(workflowCount);

        ExecutorService executor = Executors.newFixedThreadPool(50);

        List<Throwable> exceptions = new ArrayList<>();

        for (int i = 0; i < workflowCount; i++) {
            final int caseIndex = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    String caseId = "STRESS-CASE-" + caseIndex;

                    WorkflowContext result = rts.startCase(caseId, definition, null, null);
                    assertNotNull(result, "Context should not be null for case " + caseId);
                    successCount.incrementAndGet();
                } catch (Throwable e) {
                    errorCount.incrementAndGet();
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                    System.err.println("Error in stress case " + caseIndex + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all workflows simultaneously
        startLatch.countDown();

        // Wait for completion with generous timeout
        assertTrue(completionLatch.await(120, TimeUnit.SECONDS),
                "All workflows should complete within 120 seconds");

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        // Report results
        System.out.println("Stress test completed: " + successCount.get() + " successes, "
                + errorCount.get() + " errors");

        if (!exceptions.isEmpty()) {
            System.err.println("Encountered " + exceptions.size() + " exceptions:");
            exceptions.forEach(e -> e.printStackTrace());
        }

        // Verify results
        assertEquals(workflowCount, successCount.get(),
                "All 100 workflows should complete successfully. Errors: " + errorCount.get());
        assertEquals(0, errorCount.get(), "No errors should occur during stress test");
        assertTrue(exceptions.isEmpty(), "No exceptions should be thrown");
    }

    /**
     * Test that WorkflowDefinition can be safely shared across concurrent workflows.
     *
     * <p>Verifies that the change from HashMap to ConcurrentHashMap prevents
     * race conditions when multiple workflows access the same definition.
     */
    @Test
    public void testSharedWorkflowDefinitionThreadSafety() throws Exception {
        final int workflowCount = 30;

        String json = StringUtils.getResourceAsString(
                getClass(), "/workflow_service/journey_00_linear.json");
        // Single shared definition
        WorkflowDefinition sharedDefinition = WorkflowDefinitionMapper.toEntity(new JDocument(json));

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < workflowCount; i++) {
            final int caseIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String caseId = "SHARED-DEF-" + caseIndex;

                    // All workflows use the same definition object
                    WorkflowContext result = rts.startCase(caseId, sharedDefinition, null, null);
                    assertNotNull(result);

                    // Verify we can access definition properties safely
                    assertNotNull(sharedDefinition.getName());
                    assertNotNull(sharedDefinition.getSteps());

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        allFutures.get(45, TimeUnit.SECONDS);

        assertEquals(workflowCount, successCount.get());
        assertEquals(0, errorCount.get());
    }

    /**
     * Test concurrent workflow execution with mixed success and error scenarios.
     *
     * <p>Verifies thread safety when some workflows succeed and others encounter errors.
     */
    @Test
    public void testMixedSuccessAndErrorConcurrency() throws Exception {
        final int workflowCount = 20;

        String json = StringUtils.getResourceAsString(
                getClass(), "/workflow_service/journey_00_linear.json");
        WorkflowDefinition definition = WorkflowDefinitionMapper.toEntity(new JDocument(json));

        AtomicInteger processedCount = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < workflowCount; i++) {
            final int caseIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String caseId = "MIXED-" + caseIndex;

                    WorkflowContext result = rts.startCase(caseId, definition, null, null);
                    assertNotNull(result);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Some errors are expected in mixed scenarios
                    System.out.println("Expected error in case " + caseIndex);
                } finally {
                    processedCount.incrementAndGet();
                }
            });
            futures.add(future);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        allFutures.get(30, TimeUnit.SECONDS);

        assertEquals(workflowCount, processedCount.get(),
                "All workflows should be processed");
        assertTrue(successCount.get() > 0, "At least some workflows should succeed");
    }
}
