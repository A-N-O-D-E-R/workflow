package com.anode.workflow.service;

import com.anode.workflow.entities.events.EventType;
import com.anode.workflow.entities.workflows.WorkflowContext;

/**
 * Interface for handling workflow lifecycle events.
 *
 * <p>The {@code EventHandler} receives notifications about significant workflow lifecycle events
 * such as process start, completion, step execution, and errors. Implementations can use these
 * events to trigger business logic, send notifications, update external systems, or perform
 * logging and monitoring.
 *
 * <h2>Event Types</h2>
 * The following events are supported:
 * <ul>
 *   <li>{@link EventType#ON_PROCESS_START} - Workflow instance started</li>
 *   <li>{@link EventType#ON_PROCESS_COMPLETE} - Workflow instance completed successfully</li>
 *   <li>{@link EventType#ON_PROCESS_PEND} - Workflow paused (e.g., waiting for human task)</li>
 *   <li>{@link EventType#ON_PROCESS_RESUME} - Workflow resumed from paused state</li>
 *   <li>{@link EventType#ON_PROCESS_REOPEN} - Completed workflow reopened</li>
 *   <li>{@link EventType#ON_PERSIST} - Workflow state persisted</li>
 *   <li>{@link EventType#ON_TICKET_RAISED} - Ticket/issue raised during execution</li>
 * </ul>
 *
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * public class MyEventHandler implements EventHandler {
 *     {@literal @}Override
 *     public void invoke(EventType event, WorkflowContext context) {
 *         switch (event) {
 *             case ON_PROCESS_START:
 *                 log.info("Workflow started: {}", context.getCaseId());
 *                 notificationService.sendStartNotification(context);
 *                 break;
 *
 *             case ON_PROCESS_COMPLETE:
 *                 log.info("Workflow completed: {}", context.getCaseId());
 *                 notificationService.sendCompletionNotification(context);
 *                 break;
 *
 *             case ON_PROCESS_PEND:
 *                 log.info("Workflow paused at: {}", context.getPendWorkBasket());
 *                 workBasketService.notifyAssignees(context);
 *                 break;
 *
 *             default:
 *                 log.debug("Event received: {}", event);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * Implementations should be thread-safe as the same instance may be called concurrently
 * for different workflow instances.
 *
 * <h2>Performance Considerations</h2>
 * Event handlers are called synchronously during workflow execution. Long-running operations
 * should be executed asynchronously to avoid blocking workflow progression.
 *
 * @see EventType
 * @see WorkflowContext
 * @since 0.0.1
 */
public interface EventHandler {

    /**
     * Handles a workflow lifecycle event.
     *
     * <p>This method is invoked by the workflow engine when significant lifecycle events occur.
     * The implementation should process the event and perform any necessary actions such as
     * logging, notifications, or state updates.
     *
     * <p><b>Important:</b> This method is called synchronously during workflow execution.
     * Long-running operations should be delegated to background threads or asynchronous
     * processing to avoid blocking workflow progression.
     *
     * @param event the type of event that occurred (e.g., process start, step complete)
     * @param workflowContext context information about the workflow instance, including case ID,
     *                        process variables, current step, and execution path
     * @throws RuntimeException if event handling fails critically; non-critical errors should
     *                         be logged but not thrown to avoid disrupting workflow execution
     *
     * @see EventType for available event types
     * @see WorkflowContext for available context information
     */
    public void invoke(EventType event, WorkflowContext workflowContext);

  }
