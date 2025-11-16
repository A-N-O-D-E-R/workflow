package com.anode.workflow.entities.workflows;

import com.anode.workflow.entities.steps.Step;
import com.anode.workflow.entities.tickets.Ticket;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;

/**
 * Defines the structure and behavior of a workflow process.
 *
 * <p>The {@code WorkflowDefinition} entity represents the blueprint for a workflow - a reusable
 * template that defines the steps, routes, tickets, and variables for a business process. Multiple
 * {@link WorkflowInfo} instances can execute from the same definition, similar to how multiple
 * object instances can be created from a class definition.
 *
 * <h2>Core Components</h2>
 * A workflow definition consists of:
 * <ul>
 *   <li><b>Name</b> - Unique identifier for this workflow type (e.g., "orderProcessing", "loanApproval")</li>
 *   <li><b>Steps</b> - Ordered map of {@link Step} objects defining tasks, routes, branches, and joins</li>
 *   <li><b>Tickets</b> - Map of {@link Ticket} definitions for issue/notification management</li>
 *   <li><b>Workflow Variables</b> - Template variables that can be initialized for each instance</li>
 * </ul>
 *
 * <h2>Building a Workflow Definition</h2>
 * <pre>{@code
 * // Create workflow definition
 * WorkflowDefinition orderWorkflow = new WorkflowDefinition();
 * orderWorkflow.setName("orderProcessing");
 *
 * // Add steps
 * Step validateStep = new Step("validateOrder", StepType.TASK);
 * validateStep.setComponentName("validateOrderTask");
 * orderWorkflow.addStep(validateStep);
 *
 * Step creditCheckStep = new Step("checkCredit", StepType.TASK);
 * creditCheckStep.setComponentName("creditCheckTask");
 * orderWorkflow.addStep(creditCheckStep);
 *
 * Step approvalRoute = new Step("approvalRoute", StepType.ROUTE);
 * approvalRoute.setComponentName("approvalDecisionRoute");
 * orderWorkflow.addStep(approvalRoute);
 *
 * // Define tickets
 * Ticket approvalTicket = new Ticket("APPROVAL_REQUIRED");
 * approvalTicket.setDescription("Order requires manual approval");
 * orderWorkflow.setTicket(approvalTicket);
 *
 * // Set template variables
 * List<WorkflowVariable> templateVars = new ArrayList<>();
 * templateVars.add(new WorkflowVariable("orderId", VariableType.STRING, ""));
 * templateVars.add(new WorkflowVariable("amount", VariableType.NUMBER, "0"));
 * orderWorkflow.setWorkflowVariables(templateVars);
 * }</pre>
 *
 * <h2>Using with Runtime Service</h2>
 * <pre>{@code
 * // Load workflow definition
 * WorkflowDefinition definition = commonService.get(
 *     WorkflowDefinition.class,
 *     "orderProcessing"
 * );
 *
 * // Start new instance from definition
 * WorkflowVariables initialVars = new WorkflowVariables();
 * initialVars.set("orderId", "ORD-001");
 * initialVars.set("amount", "1500.00");
 *
 * WorkflowContext context = runtimeService.startCase(
 *     "ORDER-123",
 *     definition,
 *     initialVars,
 *     null // SLAs
 * );
 * }</pre>
 *
 * <h2>Step Navigation</h2>
 * Steps are connected through their configuration:
 * <ul>
 *   <li>Tasks specify next step via {@link Step#getNext()}</li>
 *   <li>Routes return the name of the next step based on routing logic</li>
 *   <li>Branches create parallel execution paths</li>
 *   <li>Joins synchronize parallel paths before continuing</li>
 * </ul>
 *
 * <h2>Persistence</h2>
 * As a JPA entity, workflow definitions are persisted and can be:
 * <ul>
 *   <li>Versioned (create new definition with version suffix: "orderProcessing_v2")</li>
 *   <li>Shared across multiple workflow instances</li>
 *   <li>Modified without affecting running instances (they use cached definition)</li>
 *   <li>Cloned to create variations using {@link com.anode.tool.service.CommonService#makeClone}</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * Workflow definitions should be treated as immutable once deployed. Multiple concurrent
 * {@link WorkflowInfo} instances may read from the same definition, so modifications during
 * active execution are not recommended.
 *
 * @see WorkflowInfo
 * @see Step
 * @see Ticket
 * @see com.anode.workflow.service.runtime.RuntimeService#startCase
 * @since 0.0.1
 */
@Entity
@Getter
@Setter
@Table(name = "workflow_definition")
public class WorkflowDefinition implements Serializable {
    @Id private Long hibid;

    @Column(name = "name", nullable = false)
    private String name = null;

    @ElementCollection
    @CollectionTable(name = "workflow_tickets", joinColumns = @JoinColumn(name = "workflow_hibid"))
    @MapKeyColumn(name = "ticket_key")
    @Column(name = "ticket_value")
    private Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "workflow_id")
    private List<WorkflowVariable> workflowVariables = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "workflow_steps", joinColumns = @JoinColumn(name = "workflow_hibid"))
    @MapKeyColumn(name = "step_key")
    @Column(name = "step_value")
    private Map<String, Step> steps = null;

    /**
     * Constructs a new empty workflow definition.
     *
     * <p>Initializes an empty steps map. Steps, tickets, and variables should be added
     * using the respective setter methods.
     */
    public WorkflowDefinition() {
        this.steps = new ConcurrentHashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Adds a step to this workflow definition.
     *
     * <p>Steps are indexed by name for fast lookup during workflow execution. If a step
     * with the same name already exists, it will be replaced.
     *
     * <h3>Example Usage</h3>
     * <pre>{@code
     * WorkflowDefinition workflow = new WorkflowDefinition();
     * workflow.setName("orderProcessing");
     *
     * // Add task step
     * Step validateStep = new Step("validateOrder", StepType.TASK);
     * validateStep.setComponentName("validateOrderTask");
     * validateStep.setNext("checkCredit");
     * workflow.addStep(validateStep);
     *
     * // Add another task
     * Step creditStep = new Step("checkCredit", StepType.TASK);
     * creditStep.setComponentName("creditCheckTask");
     * creditStep.setNext("approvalRoute");
     * workflow.addStep(creditStep);
     *
     * // Add routing step
     * Step approvalRoute = new Step("approvalRoute", StepType.ROUTE);
     * approvalRoute.setComponentName("approvalDecisionRoute");
     * workflow.addStep(approvalRoute);
     * }</pre>
     *
     * @param step the step to add; must not be null and must have a non-null name
     * @throws NullPointerException if step or step name is null
     */
    public void addStep(Step step) {
        steps.put(step.getName(), step);
    }

    /**
     * Retrieves a step from this workflow definition by name.
     *
     * <p>This method is called by the workflow engine during execution to look up
     * the next step to execute. Returns null if no step with the given name exists.
     *
     * <h3>Example Usage</h3>
     * <pre>{@code
     * WorkflowDefinition definition = commonService.get(
     *     WorkflowDefinition.class,
     *     "orderProcessing"
     * );
     *
     * Step validateStep = definition.getStep("validateOrder");
     * if (validateStep != null) {
     *     String componentName = validateStep.getComponentName();
     *     StepType type = validateStep.getType();
     *     System.out.println("Step: " + validateStep.getName() +
     *                       ", Type: " + type +
     *                       ", Component: " + componentName);
     * }
     * }</pre>
     *
     * @param name the name of the step to retrieve; must not be null
     * @return the step with the given name, or null if not found
     */
    public Step getStep(String name) {
        return steps.get(name);
    }

    /**
     * Retrieves a ticket definition by name.
     *
     * <p>Tickets are used to define issues or notifications that can be raised during
     * workflow execution. The workflow engine uses this method to look up ticket
     * definitions when processing raised tickets.
     *
     * <h3>Example Usage</h3>
     * <pre>{@code
     * Ticket approvalTicket = definition.getTicket("APPROVAL_REQUIRED");
     * if (approvalTicket != null) {
     *     String description = approvalTicket.getDescription();
     *     System.out.println("Ticket: " + description);
     * }
     * }</pre>
     *
     * @param name the name of the ticket to retrieve; must not be null
     * @return the ticket with the given name, or null if not found
     */
    public Ticket getTicket(String name) {
        return tickets.get(name);
    }

    /**
     * Adds or updates a ticket definition in this workflow.
     *
     * <p>Tickets define the types of issues or notifications that can be raised
     * during workflow execution. If a ticket with the same name already exists,
     * it will be replaced.
     *
     * <h3>Example Usage</h3>
     * <pre>{@code
     * // Define approval ticket
     * Ticket approvalTicket = new Ticket("APPROVAL_REQUIRED");
     * approvalTicket.setDescription("High-value order requires manager approval");
     * approvalTicket.setWorkBasket("MANAGER_QUEUE");
     * workflow.setTicket(approvalTicket);
     *
     * // Define error ticket
     * Ticket errorTicket = new Ticket("PAYMENT_FAILED");
     * errorTicket.setDescription("Payment gateway returned error");
     * errorTicket.setWorkBasket("OPS_QUEUE");
     * workflow.setTicket(errorTicket);
     * }</pre>
     *
     * @param ticket the ticket to add; must not be null and must have a non-null name
     * @throws NullPointerException if ticket or ticket name is null
     */
    public void setTicket(Ticket ticket) {
        tickets.put(ticket.getName(), ticket);
    }

    /**
     * Sets the template variables for this workflow definition.
     *
     * <p>Template variables define the initial variable schema that new workflow
     * instances will be initialized with. These serve as documentation and can
     * provide default values.
     *
     * @param workflowVariables the list of template variables; may be empty but not null
     */
    public void setWorkflowVariables(List<WorkflowVariable> workflowVariables) {
        this.workflowVariables = workflowVariables;
    }

    /**
     * Retrieves the template variables for this workflow definition.
     *
     * @return the list of template variables; may be empty but never null
     */
    public List<WorkflowVariable> getWorkflowVariables() {
        return workflowVariables;
    }
}
