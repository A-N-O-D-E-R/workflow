package com.anode.workflow.entities.workflows;

import com.anode.tool.StringUtils;
import com.anode.workflow.entities.steps.Step;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.workflows.paths.ExecPath;
import com.anode.workflow.service.ErrorHandler;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * Runtime state information for an active workflow instance.
 *
 * <p>The {@code WorkflowInfo} entity stores the complete runtime state of a workflow instance,
 * including its execution paths, process variables, completion status, and synchronization
 * mechanisms for concurrent access. This is the primary entity persisted during workflow
 * execution and contains all information needed to resume or query workflow state.
 *
 * <h2>Key Responsibilities</h2>
 * <ul>
 *   <li><b>State Management</b> - Tracks completion status, pending state, and execution paths</li>
 *   <li><b>Variable Storage</b> - Maintains workflow variables shared across all execution paths</li>
 *   <li><b>Execution Paths</b> - Manages multiple concurrent execution paths (for parallel branches)</li>
 *   <li><b>Ticket Management</b> - Handles raised tickets/issues during workflow execution</li>
 *   <li><b>Concurrency Control</b> - Provides locking mechanisms for thread-safe access</li>
 *   <li><b>Persistence</b> - JPA entity automatically persisted to database</li>
 * </ul>
 *
 * <h2>Execution Paths</h2>
 * Workflows can have multiple concurrent execution paths when parallel branches are executing.
 * Each path tracks its own position, pending state, and local context:
 * <ul>
 *   <li>The main path is named "."</li>
 *   <li>Branch paths are named with hierarchical notation (e.g., ".branch1", ".branch1.subbranch")</li>
 *   <li>Each path can be independently paused/pending at different work baskets</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * This class is designed for concurrent access:
 * <ul>
 *   <li>{@code variablesMap} uses {@link ConcurrentHashMap} for thread-safe variable updates</li>
 *   <li>{@code execPaths} uses synchronized {@link TreeMap} for ordered path storage</li>
 *   <li>Critical fields use {@code volatile} modifier for visibility across threads</li>
 *   <li>Provides {@link ReentrantLock} via {@link #getLock()} for external synchronization</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create new workflow instance
 * WorkflowDefinition definition = // ... load definition
 * WorkflowInfo workflowInfo = new WorkflowInfo("ORDER-123", definition);
 *
 * // Set process variables
 * workflowInfo.setWorkflowVariable(
 *     new WorkflowVariable("orderId", VariableType.STRING, "ORD-001")
 * );
 * workflowInfo.setWorkflowVariable(
 *     new WorkflowVariable("amount", VariableType.NUMBER, "1500.00")
 * );
 *
 * // Check workflow state
 * if (workflowInfo.isCaseStarted()) {
 *     System.out.println("Workflow is running");
 * }
 *
 * if (workflowInfo.isCaseCompleted()) {
 *     System.out.println("Workflow completed");
 * }
 *
 * // Access pending information
 * if (!workflowInfo.getPendExecPath().isEmpty()) {
 *     String workBasket = workflowInfo.getPendWorkBasket();
 *     ErrorHandler error = workflowInfo.getPendErrorHandler();
 *     System.out.println("Paused at work basket: " + workBasket);
 * }
 * }</pre>
 *
 * <h2>Persistence</h2>
 * As a JPA entity, this class is automatically persisted:
 * <ul>
 *   <li>Variables stored in {@code info_variables} table</li>
 *   <li>Execution paths stored in {@code info_exec_path} table</li>
 *   <li>Associated with {@link WorkflowDefinition} via many-to-one relationship</li>
 * </ul>
 *
 * @see WorkflowDefinition
 * @see WorkflowVariables
 * @see ExecPath
 * @see ErrorHandler
 * @since 0.0.1
 */
@Getter
@lombok.Setter
@Entity
public class WorkflowInfo implements Serializable {

    @Id private Long hibid;

    @Column private final String caseId;

    @ManyToOne private final WorkflowDefinition workflowDefinition;

    @Transient private ReentrantLock lock = new ReentrantLock(true);

    @Column private Boolean isComplete = false;

    // Shared variables that will be updated by threads
    @ElementCollection
    @CollectionTable(name = "info_variables", joinColumns = @JoinColumn(name = "info_hibid"))
    @MapKeyColumn(name = "variable_key")
    @Column(name = "variable_value")
    private Map<String, WorkflowVariable> variablesMap = new ConcurrentHashMap<>();

    // Ticket raised
    @Column private volatile String ticket = "";

    @Column
    // Whether the component that raised the ticket returned an OK_PEND or not
    private volatile StepResponseType ticketUrt = StepResponseType.OK_PROCEED;

    @Column
    // Pend exec path
    private volatile String pendExecPath = "";

    // Variables which are thread specific
    @ElementCollection
    @CollectionTable(name = "info_exec_path", joinColumns = @JoinColumn(name = "info_hibid"))
    @MapKeyColumn(name = "exec_path_key")
    @Column(name = "exec_path_value")
    private Map<String, ExecPath> execPaths = Collections.synchronizedSortedMap(new TreeMap<>());

    // This variable is populated while writing and is for troubleshooting and information only
    // This variable is not used while reading
    @Column private volatile Step lastUnitExecuted = null;

    @Column public volatile boolean isPendAtSameStep = false;

    @Transient private Setter setter = null;

    /**
     * Constructs a new workflow instance with the specified case ID and definition.
     *
     * <p>This constructor initializes the workflow runtime state with empty execution paths
     * and variables. The workflow is not started until the runtime service begins execution.
     *
     * @param caseId the unique identifier for this workflow instance; must not be null
     * @param workflowDefinition the workflow definition this instance will execute; must not be null
     */
    public WorkflowInfo(String caseId, WorkflowDefinition workflowDefinition) {
        this.caseId = caseId;
        this.workflowDefinition = workflowDefinition;
        setter = new Setter();
    }

    private void setLastUnitExecuted(Step lastUnitExecuted) {
        this.lastUnitExecuted = lastUnitExecuted;
    }

    public Step getLastUnitExecuted() {
        return lastUnitExecuted;
    }

    public StepResponseType getTicketUrt() {
        return ticketUrt;
    }

    private void setTicketUrt(StepResponseType ticketUrt) {
        this.ticketUrt = ticketUrt;
    }

    public Lock getLock() {
        return lock;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setPendExecPath(String pendExecPath) {
        // go ahead if we are trying to clear
        if (pendExecPath.isEmpty() == true) {
            this.pendExecPath = pendExecPath;
        } else {
            // if the pend is not on "." execution path then
            // we need to set the pend exec path to the one that is deepest in the hierarchy
            // we need to do this so that the unravelling can take place correctly
            // we determine this by counting the number of "."

            // however if the pend happened in the "." execution path, then it means that we have
            // moved ahead of parallel processing in the branches (due to a ticket) and in this case
            // the pends in the previous branches does not matter

            if (pendExecPath.equals(".")) {
                this.pendExecPath = pendExecPath;
            } else {
                int oldDepth = StringUtils.getCount(this.pendExecPath, '.');
                int newDepth = StringUtils.getCount(pendExecPath, '.');

                if (newDepth > oldDepth) {
                    this.pendExecPath = pendExecPath;
                } else {
                    // do nothing
                }
            }
        }
    }

    public String getPendExecPath() {
        return pendExecPath;
    }

    private void setTicket(String ticket) {
        if (ticket.isEmpty() == true) {
            this.ticket = ticket;
        } else {
            // set only if it is already empty
            if (this.ticket.isEmpty() == true) {
                this.ticket = ticket;
            }
        }
    }

    public String getTicket() {
        return ticket;
    }

    protected void removeExecPath(String name) {
        execPaths.remove(name);
    }

    /**
     * Sets or updates a workflow variable.
     *
     * <p>This method creates a new copy of the variable and stores it in the thread-safe
     * variables map. If a variable with the same name already exists, it will be replaced.
     * Variables are shared across all execution paths within this workflow instance.
     *
     * <h3>Example Usage</h3>
     * <pre>{@code
     * WorkflowVariable orderIdVar = new WorkflowVariable(
     *     "orderId",
     *     VariableType.STRING,
     *     "ORD-001"
     * );
     * workflowInfo.setWorkflowVariable(orderIdVar);
     *
     * // Update existing variable
     * WorkflowVariable statusVar = new WorkflowVariable(
     *     "status",
     *     VariableType.STRING,
     *     "APPROVED"
     * );
     * workflowInfo.setWorkflowVariable(statusVar);
     * }</pre>
     *
     * @param variable the variable to set; must not be null
     * @throws NullPointerException if variable is null
     */
    public void setWorkflowVariable(WorkflowVariable variable) {
        WorkflowVariable newVariable =
                new WorkflowVariable(variable.getName(), variable.getType(), variable.getValue());
        variablesMap.put(newVariable.getName(), newVariable);
    }

    /**
     * Retrieves all workflow variables as a {@link WorkflowVariables} collection.
     *
     * <p>Returns a new {@code WorkflowVariables} instance containing all current variables.
     * The returned object is a snapshot of the current state and can be safely used for
     * reading variable values.
     *
     * <h3>Example Usage</h3>
     * <pre>{@code
     * WorkflowVariables vars = workflowInfo.getWorkflowVariables();
     *
     * // Access individual variables
     * WorkflowVariable orderId = vars.get("orderId");
     * String orderIdValue = orderId.getValue();
     *
     * // Iterate all variables
     * for (WorkflowVariable var : vars.getAll()) {
     *     System.out.println(var.getName() + " = " + var.getValue());
     * }
     * }</pre>
     *
     * @return a new WorkflowVariables instance containing all current variables
     */
    public WorkflowVariables getWorkflowVariables() {
        return new WorkflowVariables(variablesMap);
    }

    public ExecPath getExecPath(String name) {
        return execPaths.get(name);
    }

    public List<ExecPath> getExecPaths() {
        synchronized (execPaths) {
            return execPaths.values().stream().collect(Collectors.toList());
        }
    }

    public void setExecPath(ExecPath ep) {
        execPaths.put(ep.getName(), ep);
    }

    public void clearExecPaths() {
        execPaths.clear();
    }

    public String getPendWorkBasket() {
        return execPaths.get(pendExecPath).getPendWorkBasket();
    }

    protected ErrorHandler getPendErrorHandler() {
        return execPaths.get(pendExecPath).getPendError();
    }

    /**
     * Checks if the workflow instance has been started.
     *
     * <p>A workflow is considered started if it has at least one execution path, which
     * is created when {@link com.anode.workflow.service.runtime.RuntimeService#startCase}
     * is called. A workflow that has been created but not yet started will return {@code false}.
     *
     * <h3>Example Usage</h3>
     * <pre>{@code
     * WorkflowInfo workflowInfo = commonService.get(WorkflowInfo.class, caseId);
     *
     * if (!workflowInfo.isCaseStarted()) {
     *     // Workflow exists but hasn't started yet
     *     runtimeService.startCase(caseId, definition, variables, slas);
     * }
     * }</pre>
     *
     * @return {@code true} if the workflow has been started; {@code false} otherwise
     */
    public boolean isCaseStarted() {
        if (execPaths.size() == 0) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Checks if the workflow instance has completed execution.
     *
     * <p>A workflow is marked as complete when all execution paths have finished and
     * the final step has been reached. Completed workflows can be reopened using
     * {@link com.anode.workflow.service.runtime.RuntimeService#reopenCase} if needed.
     *
     * <h3>Example Usage</h3>
     * <pre>{@code
     * WorkflowInfo workflowInfo = commonService.get(WorkflowInfo.class, caseId);
     *
     * if (workflowInfo.isCaseCompleted()) {
     *     System.out.println("Workflow finished successfully");
     *     sendCompletionNotification(caseId);
     * } else if (!workflowInfo.getPendExecPath().isEmpty()) {
     *     System.out.println("Workflow paused at: " +
     *         workflowInfo.getPendWorkBasket());
     * }
     * }</pre>
     *
     * @return {@code true} if the workflow has completed; {@code false} otherwise
     */
    public boolean isCaseCompleted() {
        return isComplete;
    }

    /**
     * Marks the workflow instance as completed.
     *
     * <p>This method is called by the workflow engine when all execution paths finish.
     * Manually calling this method outside the engine may cause inconsistent state.
     */
    public void setCaseCompleted() {
        this.isComplete = true;
    }

    /**
     * Sets the completion status of the workflow instance.
     *
     * <p>This method is called by the workflow engine to mark completion or potentially
     * to revert completion status when reopening. Manually calling this method outside
     * the engine may cause inconsistent state.
     *
     * @param isComplete {@code true} to mark as completed; {@code false} to mark as in-progress
     */
    public void setCaseCompleted(boolean isComplete) {
        this.isComplete = isComplete;
    }

    public void clearPendWorkBaskets() {
        synchronized (execPaths) {
            List<ExecPath> paths = new ArrayList<>(execPaths.values());
            for (ExecPath path : paths) {
                path.setPendWorkBasket("");
            }
        }
    }

    /**
     * Returns the fluent setter interface for updating workflow state.
     *
     * <p>The {@link Setter} provides a fluent API for chaining multiple state updates
     * in a single statement. This is primarily used internally by the workflow engine.
     *
     * @return the Setter instance for this workflow info
     */
    public Setter getSetter() {
        return this.setter;
    }

    /**
     * Fluent setter interface for updating workflow runtime state.
     *
     * <p>This inner class provides a builder-style API for updating multiple workflow
     * state fields in a single chained statement. Each method returns {@code this} to
     * enable method chaining. This pattern is used internally by the workflow engine
     * during execution.
     *
     * <h3>Example Usage</h3>
     * <pre>{@code
     * workflowInfo.getSetter()
     *     .setPendExecPath(".branch1")
     *     .setTicket("TICKET-123")
     *     .setTicketUrt(StepResponseType.OK_PEND)
     *     .setLastStepExecuted(currentStep);
     * }</pre>
     *
     * <h3>Thread Safety</h3>
     * The setter updates volatile and synchronized fields in the parent {@link WorkflowInfo},
     * ensuring visibility across threads. However, the chaining itself is not atomic -
     * external synchronization may be required for consistency across multiple updates.
     */
    public class Setter {

        /**
         * Sets or clears the ticket raised during workflow execution.
         *
         * <p>Tickets represent issues or notifications raised during workflow processing.
         * When clearing a ticket (empty string), the ticket URT is automatically reset
         * to {@code OK_PROCEED}.
         *
         * @param ticket the ticket identifier to set, or empty string to clear
         * @return this Setter for method chaining
         */
        public Setter setTicket(String ticket) {
            WorkflowInfo.this.setTicket(ticket);
            if (ticket.isEmpty() == true) {
                WorkflowInfo.this.setTicketUrt(StepResponseType.OK_PROCEED);
            }
            return this;
        }

        /**
         * Sets the ticket user response type (URT).
         *
         * <p>The URT indicates how the component that raised the ticket responded:
         * {@code OK_PEND} means the workflow should pause, {@code OK_PROCEED} means
         * it should continue.
         *
         * @param ticketUrt the step response type to set
         * @return this Setter for method chaining
         */
        public Setter setTicketUrt(StepResponseType ticketUrt) {
            WorkflowInfo.this.setTicketUrt(ticketUrt);
            return this;
        }

        /**
         * Sets the pending execution path for this workflow.
         *
         * <p>The pending execution path identifies which execution path is currently
         * paused/pending. The path name follows hierarchical notation (e.g., ".",
         * ".branch1", ".branch1.subbranch").
         *
         * @param pendExecPath the execution path name to set as pending
         * @return this Setter for method chaining
         */
        public Setter setPendExecPath(String pendExecPath) {
            WorkflowInfo.this.setPendExecPath(pendExecPath);
            return this;
        }

        /**
         * Sets the last step that was executed in this workflow.
         *
         * <p>This field is used for troubleshooting and information purposes only.
         * It tracks which step most recently completed execution.
         *
         * @param unit the step that was just executed
         * @return this Setter for method chaining
         */
        public Setter setLastStepExecuted(Step unit) {
            WorkflowInfo.this.setLastUnitExecuted(unit);
            return this;
        }
    }
}
