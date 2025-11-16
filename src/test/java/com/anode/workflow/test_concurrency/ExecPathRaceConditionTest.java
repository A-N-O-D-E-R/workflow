package com.anode.workflow.test_concurrency;

import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.entities.workflows.WorkflowInfo;
import com.anode.workflow.entities.workflows.paths.ExecPath;
import com.anode.workflow.entities.workflows.paths.ExecPath.ExecPathStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ExecPath race conditions and thread safety.
 *
 * <p>This test class addresses the race conditions documented in
 * THREAD_SAFETY_FIXES_NEEDED.md, specifically:
 * <ul>
 *   <li>Scenario 1: ExecPath Deletion Race</li>
 *   <li>Concurrent access to execution paths</li>
 *   <li>Clear paths vs. get paths race conditions</li>
 * </ul>
 *
 * <p>The fixes ensure that null checks are in place and that
 * WorkflowInfo uses thread-safe collections.
 */
public class ExecPathRaceConditionTest {

    private WorkflowInfo workflowInfo;
    private WorkflowDefinition workflowDefinition;

    @BeforeEach
    public void setUp() {
        workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setName("test-workflow");
        workflowInfo = new WorkflowInfo("TEST-CASE-001", workflowDefinition);
    }

    /**
     * Test concurrent access to execution paths.
     *
     * <p>Verifies that multiple threads can safely access execution paths
     * without causing race conditions or NullPointerExceptions.
     */
    @Test
    public void testConcurrentExecPathAccess() throws Exception {
        // Set up initial execution path
        ExecPath mainPath = new ExecPath(".");
        mainPath.set(ExecPathStatus.STARTED, "step1", StepResponseType.OK_PROCEED);
        workflowInfo.setExecPath(mainPath);

        final int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> exceptions = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(10);

        // Half the threads read paths
        for (int i = 0; i < threadCount / 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    // Attempt to read the main path
                    ExecPath path = workflowInfo.getExecPath(".");
                    if (path != null) {
                        assertNotNull(path.getName());
                        assertNotNull(path.getStep());
                        successCount.incrementAndGet();
                    }
                } catch (Throwable e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Other half modify paths
        for (int i = 0; i < threadCount / 2; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    // Create new execution paths
                    ExecPath branchPath = new ExecPath(".branch" + index);
                    branchPath.set(ExecPathStatus.STARTED, "step" + index, StepResponseType.OK_PROCEED);
                    workflowInfo.setExecPath(branchPath);
                    successCount.incrementAndGet();
                } catch (Throwable e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(completionLatch.await(15, TimeUnit.SECONDS));

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        if (!exceptions.isEmpty()) {
            System.err.println("Exceptions encountered:");
            exceptions.forEach(Throwable::printStackTrace);
        }

        assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent path access");
        assertTrue(successCount.get() > 0, "Some operations should succeed");
    }

    /**
     * Test the race condition between getPendExecPath and getExecPath.
     *
     * <p>This simulates the scenario where one thread reads pendExecPath
     * and another thread clears it before getExecPath is called.
     */
    @Test
    public void testPendExecPathRaceCondition() throws Exception {
        // Set up pended execution path
        ExecPath pendPath = new ExecPath(".");
        pendPath.set(ExecPathStatus.STARTED, "pendedStep", StepResponseType.OK_PEND);
        pendPath.setPendWorkBasket("PENDING_BASKET");
        workflowInfo.setExecPath(pendPath);
        workflowInfo.getSetter().setPendExecPath(".");

        final int iterations = 50;
        AtomicInteger nullExecPaths = new AtomicInteger(0);
        AtomicInteger validExecPaths = new AtomicInteger(0);
        List<Throwable> exceptions = new ArrayList<>();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            // Thread 1: Read pendExecPath then getExecPath
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    String pendExecPathName = workflowInfo.getPendExecPath();
                    if (pendExecPathName != null && !pendExecPathName.isEmpty()) {
                        ExecPath ep = workflowInfo.getExecPath(pendExecPathName);
                        if (ep == null) {
                            nullExecPaths.incrementAndGet();
                            // This is now handled with proper null checks
                            // No NPE should occur
                        } else {
                            validExecPaths.incrementAndGet();
                        }
                    }
                } catch (Throwable e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            }));

            // Thread 2: Modify execution paths
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    // Simulate path modification
                    ExecPath newPath = new ExecPath(".");
                    newPath.set(ExecPathStatus.STARTED, "newStep", StepResponseType.OK_PROCEED);
                    workflowInfo.setExecPath(newPath);
                } catch (Throwable e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            }));
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        allFutures.get(30, TimeUnit.SECONDS);

        System.out.println("Valid exec paths: " + validExecPaths.get());
        System.out.println("Null exec paths: " + nullExecPaths.get());

        if (!exceptions.isEmpty()) {
            System.err.println("Exceptions encountered:");
            exceptions.forEach(Throwable::printStackTrace);
        }

        // With proper null checks, no NullPointerException should occur
        assertTrue(exceptions.isEmpty(), "No NullPointerException should occur with proper null checks");
    }

    /**
     * Test concurrent clearExecPaths operations.
     *
     * <p>Verifies that clearing execution paths doesn't cause issues
     * when other threads are accessing them.
     */
    @Test
    public void testConcurrentClearExecPaths() throws Exception {
        // Set up multiple execution paths
        for (int i = 0; i < 10; i++) {
            ExecPath path = new ExecPath(".branch" + i);
            path.set(ExecPathStatus.STARTED, "step" + i, StepResponseType.OK_PROCEED);
            workflowInfo.setExecPath(path);
        }

        final int threadCount = 20;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger clearCount = new AtomicInteger(0);
        AtomicInteger readCount = new AtomicInteger(0);
        List<Throwable> exceptions = new ArrayList<>();

        // Some threads clear paths
        for (int i = 0; i < threadCount / 2; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    workflowInfo.clearExecPaths();
                    clearCount.incrementAndGet();
                } catch (Throwable e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            }));
        }

        // Some threads read paths
        for (int i = 0; i < threadCount / 2; i++) {
            final int index = i;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    ExecPath path = workflowInfo.getExecPath(".branch" + (index % 10));
                    // Path might be null after clear, that's OK
                    readCount.incrementAndGet();
                } catch (Throwable e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            }));
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        allFutures.get(15, TimeUnit.SECONDS);

        if (!exceptions.isEmpty()) {
            System.err.println("Exceptions during clearExecPaths test:");
            exceptions.forEach(Throwable::printStackTrace);
        }

        assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent clear operations");
        assertEquals(threadCount / 2, clearCount.get(), "All clear operations should complete");
        assertEquals(threadCount / 2, readCount.get(), "All read operations should complete");
    }

    /**
     * Test concurrent access to sibling execution paths.
     */
    @Test
    public void testConcurrentSiblingPathAccess() throws Exception {
        // Create parent path with multiple children
        ExecPath parentPath = new ExecPath(".parent");
        parentPath.set(ExecPathStatus.STARTED, "parentStep", StepResponseType.OK_PROCEED);
        workflowInfo.setExecPath(parentPath);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        final int siblingCount = 20;

        // Create sibling paths concurrently
        for (int i = 0; i < siblingCount; i++) {
            final int index = i;
            futures.add(CompletableFuture.runAsync(() -> {
                ExecPath siblingPath = new ExecPath(".parent.sibling" + index);
                siblingPath.set(ExecPathStatus.STARTED, "siblingStep" + index, StepResponseType.OK_PROCEED);
                workflowInfo.setExecPath(siblingPath);
            }));
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        allFutures.get(10, TimeUnit.SECONDS);

        // Verify parent path still exists
        ExecPath parent = workflowInfo.getExecPath(".parent");
        assertNotNull(parent, "Parent path should still exist");

        // Count how many sibling paths were created
        int siblingPathCount = 0;
        for (int i = 0; i < siblingCount; i++) {
            ExecPath sibling = workflowInfo.getExecPath(".parent.sibling" + i);
            if (sibling != null) {
                siblingPathCount++;
            }
        }

        assertTrue(siblingPathCount > 0, "At least some sibling paths should be created");
        System.out.println("Created " + siblingPathCount + " sibling paths out of " + siblingCount);
    }

    /**
     * Stress test with high concurrency on execution paths.
     */
    @Test
    public void testExecPathStressTest() throws Exception {
        final int operationCount = 100;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger operationSuccessCount = new AtomicInteger(0);
        List<Throwable> exceptions = new ArrayList<>();

        for (int i = 0; i < operationCount; i++) {
            final int index = i;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    // Mix of operations
                    if (index % 3 == 0) {
                        // Create new path
                        ExecPath path = new ExecPath(".stress" + index);
                        path.set(ExecPathStatus.STARTED, "step" + index, StepResponseType.OK_PROCEED);
                        workflowInfo.setExecPath(path);
                    } else if (index % 3 == 1) {
                        // Read path
                        workflowInfo.getExecPath(".stress" + (index - 1));
                    } else {
                        // Update path status
                        ExecPath path = workflowInfo.getExecPath(".stress" + (index - 2));
                        if (path != null) {
                            path.set(ExecPathStatus.COMPLETED, "step" + index, StepResponseType.OK_PROCEED);
                        }
                    }
                    operationSuccessCount.incrementAndGet();
                } catch (Throwable e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            }));
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        allFutures.get(30, TimeUnit.SECONDS);

        if (!exceptions.isEmpty()) {
            System.err.println("Exceptions during stress test:");
            exceptions.forEach(Throwable::printStackTrace);
        }

        assertTrue(exceptions.isEmpty(), "No exceptions should occur during stress test");
        assertEquals(operationCount, operationSuccessCount.get(),
                "All operations should complete successfully");
    }
}
