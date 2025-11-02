package com.anode.workflow.entities.workflows;

import com.anode.workflow.entities.events.EventType;
import com.anode.workflow.entities.steps.Step.StepType;
import com.anode.workflow.service.ErrorHandler;
import com.anode.workflow.service.runtime.RuntimeService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Context object providing access to workflow state and metadata during execution.
 *
 * <p>The {@code WorkflowContext} is passed to workflow components (tasks, routes) and event
 * handlers, providing read-only access to the current workflow state, process variables, and
 * execution metadata. It serves as the primary interface for components to interact with the
 * workflow engine.
 *
 * <h2>Available Information</h2>
 * The context provides access to:
 * <ul>
 *   <li><b>Identification</b> - Journey name, case ID, step name</li>
 *   <li><b>Component Information</b> - Component name, type, user-defined data</li>
 *   <li><b>Process Variables</b> - All variables associated with this workflow instance</li>
 *   <li><b>Execution State</b> - Current execution path, pause/pend information</li>
 *   <li><b>Error Information</b> - Error details if workflow is paused due to failure</li>
 * </ul>
 *
 * <h2>Usage in Workflow Components</h2>
 * <pre>{@code
 * public class MyTask implements InvokableTask {
 *     {@literal @}Override
 *     public TaskResponse invoke(WorkflowContext context) {
 *         // Access workflow information
 *         String caseId = context.getCaseId();
 *         String journeyName = context.getJourneyName();
 *
 *         // Read process variables
 *         WorkflowVariables vars = context.getProcessVariables();
 *         String orderId = vars.get("orderId").getValue();
 *         Double amount = Double.parseDouble(vars.get("amount").getValue());
 *
 *         // Perform business logic
 *         processOrder(orderId, amount);
 *
 *         return TaskResponse.ok();
 *     }
 * }
 * }</pre>
 *
 * <h2>Usage in Event Handlers</h2>
 * <pre>{@code
 * {@literal @}Override
 * public void invoke(EventType event, WorkflowContext context) {
 *     switch (event) {
 *         case ON_PROCESS_START:
 *             log.info("Started workflow {} for case {}",
 *                 context.getJourneyName(), context.getCaseId());
 *             break;
 *
 *         case ON_PROCESS_PEND:
 *             String workBasket = context.getPendWorkBasket();
 *             ErrorHandler error = context.getPendErrorTuple();
 *             if (error.getErrorCode() > 0) {
 *                 log.error("Workflow paused due to error at basket {}: {}",
 *                     workBasket, error.getErrorMessage());
 *             }
 *             break;
 *     }
 * }
 * }</pre>
 *
 * <h2>Factory Method Pattern</h2>
 * Contexts are typically created using static factory methods rather than constructors:
 * <ul>
 *   <li>{@link #forEvent(EventType, RuntimeService, String)} - Create for event handling</li>
 *   <li>{@link #forWorkflowMmanagerService(WorkflowDefinition, WorkflowInfo)} - Create for workflow manager</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * Context instances are not thread-safe and should not be shared between threads. Each
 * workflow execution receives its own context instance.
 *
 * @see EventHandler
 * @see WorkflowVariables
 * @see ErrorHandler
 * @since 0.0.1
 */
@NoArgsConstructor
@Getter
@ToString
public class WorkflowContext {

    /** Name of the workflow definition (journey). */
    private String journeyName = null;

    /** Unique identifier for this workflow instance. */
    private String caseId = null;

    /** Name of the current step being executed. */
    private String stepName = null;

    /** Name of the current component (task/route) being executed. */
    private String compName = null;

    /** User-defined data associated with the current step. */
    private String userData = null;

    /** Type of the current component (TASK, ROUTE, etc.). */
    private StepType compType = null;

    /** All process variables for this workflow instance. */
    private WorkflowVariables processVariables = null;

    /** Name of the current execution path. */
    private String execPathName = null;

    /** Work basket where workflow is currently paused (null if not paused). */
    private String pendWorkBasket = null;

    /** Work basket where workflow was last paused. */
    private String lastPendWorkBasket = null;

    /** Step where workflow was last paused. */
    private String lastPendStep = null;

    /** Error information if workflow is paused due to failure. */
    private ErrorHandler pendErrorTuple = new ErrorHandler(); // only valid for pend event

    /** Indicates if workflow is paused at the same step it was previously paused. */
    private boolean isPendAtSameStep = false;

    /** Ticket/reference identifier for reopen operations. */
    private String ticketName = null;

    public WorkflowContext(
            String journeyName,
            String caseId,
            String stepName,
            String compName,
            String userData,
            StepType compType,
            WorkflowVariables processVariables,
            String execPathName) {
        init(
                journeyName,
                caseId,
                stepName,
                compName,
                userData,
                compType,
                processVariables,
                execPathName,
                null,
                null,
                null);
    }

    public WorkflowContext(
            String journeyName,
            String caseId,
            String stepName,
            String compName,
            String userData,
            StepType compType,
            WorkflowVariables processVariables,
            String execPathName,
            String lastPendWorkBasket,
            String lastPendStep,
            Boolean isPendAtSameStep) {
        init(
                journeyName,
                caseId,
                stepName,
                compName,
                userData,
                compType,
                processVariables,
                execPathName,
                lastPendWorkBasket,
                lastPendStep,
                isPendAtSameStep);
    }

    private void init(
            String journeyName,
            String caseId,
            String stepName,
            String compName,
            String userData,
            StepType compType,
            WorkflowVariables processVariables,
            String execPathName,
            String lastPendWorkBasket,
            String lastPendStep,
            Boolean isPendAtSameStep) {
        this.journeyName = journeyName;
        this.caseId = caseId;
        this.stepName = stepName;
        this.compName = compName;
        this.userData = userData;
        this.compType = compType;
        if (processVariables != null) {
            this.processVariables = processVariables;
        }
        this.execPathName = execPathName;
        this.lastPendWorkBasket = lastPendWorkBasket;
        this.lastPendStep = lastPendStep;
        if (isPendAtSameStep != null) {
            this.isPendAtSameStep = isPendAtSameStep;
        }
    }

    /**
     * Factory method to create a workflow context for event handling.
     *
     * <p>This method constructs a context appropriate for the given event type, populating
     * it with relevant information from the runtime service and current execution state.
     *
     * <h3>Event-Specific Context Population</h3>
     * Different event types populate different context fields:
     * <ul>
     *   <li>{@code ON_PROCESS_START} - Basic journey and case information</li>
     *   <li>{@code ON_PROCESS_PEND} - Includes step, component, and pend work basket info</li>
     *   <li>{@code ON_PROCESS_RESUME} - Includes resume location and previous pend info</li>
     *   <li>{@code ON_TICKET_RAISED} - Includes ticket name and step information</li>
     *   <li>{@code ON_PROCESS_REOPEN} - Includes reopen step information</li>
     * </ul>
     *
     * @param eventType the type of event for which this context is being created
     * @param rts the runtime service containing workflow state
     * @param execPathName the name of the execution path
     * @return a new {@code WorkflowContext} populated for the given event type
     *
     * @see EventType
     * @see RuntimeService
     */
    public static WorkflowContext forEvent(
            EventType eventType, RuntimeService rts, String execPathName) {
        WorkflowContext workflowContext = new WorkflowContext();
        WorkflowDefinition workflowDefnition = rts.getWorkflowDefinition();
        WorkflowInfo workflowInfo = rts.getWorkflowInfo();

        workflowContext.journeyName = workflowDefnition.getName();
        workflowContext.caseId = workflowInfo.getCaseId();
        workflowContext.execPathName = execPathName;
        workflowContext.processVariables = workflowInfo.getWorkflowVariables();
        workflowContext.compName = "";
        workflowContext.isPendAtSameStep = workflowInfo.isPendAtSameStep;

        switch (eventType) {
            case ON_PROCESS_START:
                break;

            case ON_PERSIST:
                break;

            case ON_PROCESS_COMPLETE:
                break;

            case ON_PROCESS_PEND:
                workflowContext.stepName =
                        workflowInfo.getExecPath(workflowInfo.getPendExecPath()).getStep();
                workflowContext.compName =
                        workflowDefnition.getStep(workflowContext.stepName).getComponentName();
                workflowContext.userData =
                        workflowDefnition.getStep(workflowContext.stepName).getUserData();
                workflowContext.compType =
                        workflowDefnition.getStep(workflowContext.stepName).getType();
                workflowContext.pendWorkBasket = workflowInfo.getPendWorkBasket();
                workflowContext.pendErrorTuple = workflowInfo.getPendErrorHandler();
                workflowContext.lastPendWorkBasket = rts.getLastPendWorkBasket();
                workflowContext.lastPendStep = rts.getLastPendStep();
                break;

            case ON_PROCESS_RESUME:
                workflowContext.stepName =
                        workflowInfo.getExecPath(workflowInfo.getPendExecPath()).getStep();
                workflowContext.compName =
                        workflowDefnition.getStep(workflowContext.stepName).getComponentName();
                workflowContext.pendWorkBasket = workflowInfo.getPendWorkBasket();
                workflowContext.lastPendWorkBasket = rts.getLastPendWorkBasket();
                workflowContext.lastPendStep = rts.getLastPendStep();
                rts.setLastPendWorkBasket(workflowContext.pendWorkBasket);
                rts.setLastPendStep(workflowContext.stepName);
                break;

            case ON_TICKET_RAISED:
                workflowContext.ticketName = workflowInfo.getTicket();
                workflowContext.stepName = workflowInfo.getExecPath(execPathName).getStep();
                workflowContext.compName =
                        workflowDefnition.getStep(workflowContext.stepName).getComponentName();
                workflowContext.userData =
                        workflowDefnition.getStep(workflowContext.stepName).getUserData();
                workflowContext.compType =
                        workflowDefnition.getStep(workflowContext.stepName).getType();
                break;

            case ON_PROCESS_REOPEN:
                workflowContext.stepName = workflowInfo.getExecPath(execPathName).getStep();
                workflowContext.compName =
                        workflowDefnition.getStep(workflowContext.stepName).getComponentName();
                break;
        }

        return workflowContext;
    }

    /**
     * Factory method to create a workflow context for workflow manager service.
     *
     * <p>This method constructs a context from workflow definition and runtime information,
     * typically used by workflow management operations that need access to current workflow
     * state without being tied to a specific event.
     *
     * @param workflowDefnition the workflow definition containing step and route configurations
     * @param workflowInfo the current runtime state and variables
     * @return a new {@code WorkflowContext} populated with workflow manager information
     *
     * @see WorkflowDefinition
     * @see WorkflowInfo
     */
    public static WorkflowContext forWorkflowMmanagerService(
            WorkflowDefinition workflowDefnition, WorkflowInfo workflowInfo) {
        WorkflowContext workflowContext = new WorkflowContext();
        workflowContext.journeyName = workflowDefnition.getName();
        workflowContext.caseId = workflowInfo.getCaseId();
        workflowContext.execPathName = workflowInfo.getPendExecPath();
        workflowContext.processVariables = workflowInfo.getWorkflowVariables();
        workflowContext.isPendAtSameStep = workflowInfo.isPendAtSameStep;
        workflowContext.stepName =
                workflowInfo.getExecPath(workflowInfo.getPendExecPath()).getStep();
        workflowContext.compName =
                workflowDefnition.getStep(workflowContext.stepName).getComponentName();
        workflowContext.userData =
                workflowDefnition.getStep(workflowContext.stepName).getUserData();
        workflowContext.compType = workflowDefnition.getStep(workflowContext.stepName).getType();
        workflowContext.pendWorkBasket = workflowInfo.getPendWorkBasket();
        workflowContext.pendErrorTuple = workflowInfo.getPendErrorHandler();
        workflowContext.lastPendWorkBasket =
                workflowInfo.getExecPath(workflowInfo.getPendExecPath()).getPrevPendWorkBasket();
        return workflowContext;
    }
}
