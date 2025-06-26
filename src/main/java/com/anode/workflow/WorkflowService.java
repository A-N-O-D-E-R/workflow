package com.anode.workflow;

import com.anode.workflow.exceptions.WorkflowRuntimeException;
import com.anode.workflow.service.EventHandler;
import com.anode.workflow.service.SlaQueueManager;
import com.anode.workflow.service.WorkflowComponantFactory;
import com.anode.workflow.service.runtime.RejectedItemHandler;
import com.anode.workflow.service.runtime.RuntimeService;
import com.anode.workflow.thread.BlockOnOfferQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/*
 * @author Deepak Arora
 */
public class WorkflowService {

    private static WorkflowService singleton = new WorkflowService();
    private int maxThreads = 10;
    private int idleTimeout = 30000;
    private String errorWorkbasket = null;
    private ExecutorService es = null;
    private volatile boolean writeAuditLog = true;
    private volatile boolean writeProcessInfoAfterEachStep = true;

    /**
     * @return an instance of Flowret
     */
    public static WorkflowService instance() {
        return singleton;
    }

    /**
     * Get the run time service of Flowret
     *
     * @param dao      An object called on by Flowret for persisting the state of the process to the data store
     * @param factory  An object called upon by Flowret to get an instance of an object on which to invoke step and route execute methods
     * @param listener An object on which the application call back events are passed
     * @param slaQm    An object on which the SLA enqueue and dequeue events are passed
     * @return
     */
    public RuntimeService getRunTimeService(
            CommonDao dao,
            WorkflowComponantFactory factory,
            EventHandler listener,
            SlaQueueManager slaQm) {
        return new RuntimeService(dao, factory, listener, slaQm);
    }

    /**
     * Get the work manager service of Flowret
     *
     * @param dao   An object called on by Flowret for persisting the state of the process to the data store
     * @param wm    An object whose methods are called by Flowret to do work management functions
     * @param slaQm An object on which the SLA enqueue and dequeue events are passed
     * @return
     */
    public WorkflowManagerServices getWorkManagementService(
            CommonDao dao, WorkManager workManager, SlaQueueManager slaQm) {
        return new WorkflowManagerServices(dao, workManager, slaQm);
    }

    private WorkflowService() {}

    public static void init(int idleTimeout, String typeIdSep) {
        init(0, idleTimeout, typeIdSep, "workflow_error");
    }

    public static void init(int idleTimeout, String typeIdSep, String errorWorkbasket) {
        init(0, idleTimeout, typeIdSep, errorWorkbasket);
    }

    public static void init(int maxThreads, int idleTimeout, String typeIdSep) {
        init(maxThreads, idleTimeout, typeIdSep, "workflow_error");
    }

    public static void init(
            int maxThreads, int idleTimeout, String typeIdSep, String errorWorkbasket) {
        WorkflowService service = instance();
        service.maxThreads = maxThreads;
        service.idleTimeout = idleTimeout;

        if (maxThreads > 0) {
            BlockOnOfferQueue<Runnable> q =
                    new BlockOnOfferQueue(new ArrayBlockingQueue<>(service.maxThreads * 2));
            service.es =
                    new ThreadPoolExecutor(
                            service.maxThreads,
                            service.maxThreads,
                            service.idleTimeout,
                            TimeUnit.MILLISECONDS,
                            q,
                            new RejectedItemHandler());
        }

        RuntimeService.SEP = typeIdSep;
        service.errorWorkbasket = errorWorkbasket;
    }

    /**
     * Method that is used to close Flowret
     */
    public static void close() {
        if (singleton.es != null) {
            singleton.es.shutdown();
            try {
                singleton.es.awaitTermination(5, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                // should never happen
                throw new WorkflowRuntimeException("Unexpected exception encountered", e);
            }
            singleton.es = null;
        }
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public ExecutorService getExecutorService() {
        return es;
    }

    public void setWriteAuditLog(boolean writeAuditLog) {
        this.writeAuditLog = writeAuditLog;
    }

    public void setWriteProcessInfoAfterEachStep(boolean writeProcessInfoAfterEachStep) {
        this.writeProcessInfoAfterEachStep = writeProcessInfoAfterEachStep;
    }

    public boolean isWriteAuditLog() {
        return writeAuditLog;
    }

    public boolean isWriteProcessInfoAfterEachStep() {
        return writeProcessInfoAfterEachStep;
    }

    public String getErrorWorkbasket() {
        return errorWorkbasket;
    }
}
