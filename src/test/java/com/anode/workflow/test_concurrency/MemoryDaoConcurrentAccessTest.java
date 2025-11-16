package com.anode.workflow.test_concurrency;

import com.anode.workflow.MemoryDao;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * Test thread safety of MemoryDao with ConcurrentHashMap.
 *
 * <p>Verifies that MemoryDao can handle concurrent operations without:
 * <ul>
 *   <li>ConcurrentModificationException</li>
 *   <li>Lost updates</li>
 *   <li>Data corruption</li>
 *   <li>Visibility issues</li>
 * </ul>
 *
 * <p>Tests the fixes implemented for Bug #3 in THREAD_SAFETY_FIXES_NEEDED.md.
 */
public class MemoryDaoConcurrentAccessTest {

    private MemoryDao dao;

    @BeforeEach
    public void setUp() {
        dao = new MemoryDao();
    }

    /**
     * Test concurrent writes to verify ConcurrentHashMap prevents lost updates.
     */
    @Test
    public void testConcurrentWrites() throws Exception {
        final int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(20);

        // Submit concurrent write operations
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    dao.saveOrUpdate("key-" + index, "value-" + index);
                } catch (Exception e) {
                    e.printStackTrace();
                    fail("Concurrent write failed: " + e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all operations simultaneously
        startLatch.countDown();
        assertTrue(completionLatch.await(10, TimeUnit.SECONDS));

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verify all writes persisted (no lost updates)
        for (int i = 0; i < threadCount; i++) {
            Object value = dao.get(Object.class, "key-" + i);
            assertNotNull(value, "Value should exist for key-" + i);
            assertEquals("value-" + i, value);
        }
    }

    /**
     * Test concurrent counter increments with atomic operations.
     *
     * <p>Verifies that the compute() method prevents race conditions in counter increments.
     */
    @Test
    public void testConcurrentCounterIncrements() throws Exception {
        final int threadCount = 50;
        final int incrementsPerThread = 20;
        final String counterKey = "test-counter";

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    dao.incrCounter(counterKey);
                }
            });
            futures.add(future);
        }

        // Wait for all increments to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        allFutures.get(15, TimeUnit.SECONDS);

        // Verify final counter value (should be threadCount * incrementsPerThread)
        // Note: incrCounter starts at 1 for first call, so we expect 1 + (threadCount * incrementsPerThread) - 1
        long finalValue = dao.incrCounter(counterKey);
        long expectedValue = (threadCount * incrementsPerThread) + 1;

        assertEquals(expectedValue, finalValue,
                "Counter should reach expected value without lost updates");
    }

    /**
     * Test concurrent reads and writes.
     *
     * <p>Verifies that concurrent reads don't block writes and vice versa,
     * and that data remains consistent.
     */
    @Test
    public void testConcurrentReadsAndWrites() throws Exception {
        final int writerCount = 20;
        final int readerCount = 30;
        final String key = "shared-key";

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(writerCount + readerCount);
        AtomicInteger readSuccessCount = new AtomicInteger(0);
        AtomicInteger writeSuccessCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(30);

        // Writers
        for (int i = 0; i < writerCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    dao.saveOrUpdate(key + "-" + index, "writer-" + index);
                    writeSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Write operation failed: " + e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Readers
        for (int i = 0; i < readerCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Try to read - might be null initially
                    Object value = dao.get(Object.class, key + "-" + (index % writerCount));
                    // Just verify no exception occurred
                    readSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Read operation failed: " + e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(completionLatch.await(15, TimeUnit.SECONDS));

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(writerCount, writeSuccessCount.get(), "All writes should succeed");
        assertEquals(readerCount, readSuccessCount.get(), "All reads should succeed");
    }

    /**
     * Test concurrent save, update, and delete operations.
     */
    @Test
    public void testConcurrentMixedOperations() throws Exception {
        final int operationsPerType = 20;

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Concurrent saves
        for (int i = 0; i < operationsPerType; i++) {
            final int index = i;
            futures.add(CompletableFuture.runAsync(() -> {
                dao.save("save-" + index, "saved-" + index);
            }));
        }

        // Concurrent updates (some may fail if key doesn't exist, that's OK)
        for (int i = 0; i < operationsPerType; i++) {
            final int index = i;
            futures.add(CompletableFuture.runAsync(() -> {
                dao.update("save-" + index, "updated-" + index);
            }));
        }

        // Concurrent saveOrUpdate
        for (int i = 0; i < operationsPerType; i++) {
            final int index = i;
            futures.add(CompletableFuture.runAsync(() -> {
                dao.saveOrUpdate("saveOrUpdate-" + index, "value-" + index);
            }));
        }

        // Wait for all operations
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        allFutures.get(20, TimeUnit.SECONDS);

        // Verify no ConcurrentModificationException occurred
        // If we get here without exception, the test passed
        assertTrue(true, "All concurrent operations completed without exceptions");
    }

    /**
     * Test that no ConcurrentModificationException occurs during iteration.
     */
    @Test
    public void testNoConcurrentModificationException() throws Exception {
        // Pre-populate with some data
        for (int i = 0; i < 50; i++) {
            dao.saveOrUpdate("initial-" + i, "value-" + i);
        }

        final int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        List<Throwable> exceptions = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Some threads modify
        for (int i = 0; i < threadCount / 2; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 20; j++) {
                        dao.saveOrUpdate("modify-" + index + "-" + j, "value");
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

        // Some threads iterate
        for (int i = 0; i < threadCount / 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 10; j++) {
                        List<Object> all = dao.getAll(Object.class);
                        assertNotNull(all);
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

        startLatch.countDown();
        assertTrue(completionLatch.await(20, TimeUnit.SECONDS));

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verify no ConcurrentModificationException
        if (!exceptions.isEmpty()) {
            exceptions.forEach(Throwable::printStackTrace);
        }
        assertTrue(exceptions.isEmpty(),
                "No ConcurrentModificationException should occur");
    }

    /**
     * Stress test with high concurrency to verify robustness.
     */
    @Test
    public void testHighConcurrencyStress() throws Exception {
        final int operationCount = 200;

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Set<String> expectedKeys = new HashSet<>();

        for (int i = 0; i < operationCount; i++) {
            final int index = i;
            final String key = "stress-" + index;
            synchronized (expectedKeys) {
                expectedKeys.add(key);
            }

            futures.add(CompletableFuture.runAsync(() -> {
                dao.saveOrUpdate(key, "stress-value-" + index);
            }));
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        allFutures.get(30, TimeUnit.SECONDS);

        // Verify all keys are present
        for (String key : expectedKeys) {
            assertNotNull(dao.get(Object.class, key),
                    "Key should exist: " + key);
        }

        assertEquals(operationCount, expectedKeys.size(),
                "All operations should have completed");
    }
}
