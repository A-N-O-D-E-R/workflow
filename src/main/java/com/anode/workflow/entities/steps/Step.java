package com.anode.workflow.entities.steps;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.Getter;

import java.io.Serializable;

/**
 * Abstract base class representing a single step in a workflow definition.
 *
 * <p>A {@code Step} is a node in the workflow graph that defines a specific action or decision
 * point. Steps are connected together to form the complete workflow process flow. Each step has
 * a unique name within the workflow and a type that determines its behavior.
 *
 * <h2>Step Types</h2>
 * The workflow engine supports several types of steps (see {@link StepType}):
 * <ul>
 *   <li><b>TASK</b> - Executes business logic via {@link Task} components</li>
 *   <li><b>PAUSE</b> - Pauses workflow execution at a work basket for human intervention</li>
 *   <li><b>PERSIST</b> - Explicitly persists workflow state to storage</li>
 *   <li><b>S_ROUTE</b> - Sequential routing - makes a decision about next step</li>
 *   <li><b>P_ROUTE</b> - Parallel routing - creates multiple execution paths</li>
 *   <li><b>P_ROUTE_DYNAMIC</b> - Dynamic parallel routing - number of branches determined at runtime</li>
 *   <li><b>P_JOIN</b> - Parallel join - waits for all parallel paths to complete</li>
 * </ul>
 *
 * <h2>Concrete Implementations</h2>
 * <ul>
 *   <li>{@link Task} - Executes business logic and returns {@link com.anode.workflow.entities.steps.responses.TaskResponse}</li>
 *   <li>{@link Pause} - Pauses execution at a specific work basket</li>
 *   <li>{@link Persist} - Forces workflow state persistence</li>
 *   <li>{@link Route} - Makes routing decisions (sequential or parallel)</li>
 *   <li>{@link Join} - Synchronizes parallel execution paths</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create a task step
 * Step validateStep = new Task("validateOrder", StepType.TASK);
 * validateStep.setComponentName("validateOrderTask");
 * validateStep.setNext("checkCredit");
 *
 * // Create a sequential routing step
 * Step approvalRoute = new Route("approvalDecision", StepType.S_ROUTE);
 * approvalRoute.setComponentName("approvalDecisionRoute");
 *
 * // Create a pause step for human intervention
 * Step manualApproval = new Pause("manualApproval", StepType.PAUSE);
 * manualApproval.setWorkBasket("MANAGER_QUEUE");
 *
 * // Add steps to workflow definition
 * WorkflowDefinition workflow = new WorkflowDefinition();
 * workflow.addStep(validateStep);
 * workflow.addStep(approvalRoute);
 * workflow.addStep(manualApproval);
 * }</pre>
 *
 * <h2>Persistence</h2>
 * Steps are persisted as part of the {@link com.anode.workflow.entities.workflows.WorkflowDefinition}
 * entity using JPA single-table inheritance strategy. All step subclasses are stored in the same
 * database table with a discriminator column to identify the concrete type.
 *
 * <h2>Polymorphism</h2>
 * The abstract methods {@link #getComponentName()} and {@link #getUserData()} must be implemented
 * by concrete subclasses to provide step-specific behavior and configuration.
 *
 * @see Task
 * @see Route
 * @see Pause
 * @see Join
 * @see Persist
 * @see StepType
 * @since 0.0.1
 */
@Entity
@Getter
@Table(name = "step")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "step_class", discriminatorType = DiscriminatorType.STRING)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type") // Tells Jackson to add type info to the JSON
@JsonSubTypes({
    @JsonSubTypes.Type(value = Task.class, name = "TASK"),
    @JsonSubTypes.Type(value = Pause.class, name = "PAUSE"),
    @JsonSubTypes.Type(value = Persist.class, name = "PERSIST"),
    @JsonSubTypes.Type(value = Join.class, name = "P_JOIN"),
    @JsonSubTypes.Type(value = Route.class, name = "P_ROUTE"),
    @JsonSubTypes.Type(value = Route.class, name = "S_ROUTE"),
    @JsonSubTypes.Type(value = Route.class, name = "P_ROUTE_DYNAMIC")
})
public abstract class Step implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long hibid;

    @Column(name = "name", nullable = false)
    private String name = null;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private StepType type = null;

    /**
     * Constructs a new step with the specified name and type.
     *
     * <p>This constructor is protected and intended to be called by concrete subclass
     * constructors only.
     *
     * @param name the unique name of this step within the workflow; must not be null
     * @param type the type of step determining its execution behavior; must not be null
     */
    protected Step(String name, StepType type) {
        this.name = name;
        this.type = type;
    }

    /**
     * Returns the unique name of this step.
     *
     * @return the step name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the type of this step.
     *
     * @return the step type indicating its behavior
     */
    public StepType getType() {
        return type;
    }

    /**
     * Returns the name of the component that implements this step's logic.
     *
     * <p>For {@link Task} steps, this is the name of the task bean/class to invoke.
     * For {@link Route} steps, this is the name of the routing logic bean/class.
     * For other step types, the meaning is implementation-specific.
     *
     * @return the component name, or null if not applicable
     */
    public abstract String getComponentName();

    /**
     * Returns user-defined metadata associated with this step.
     *
     * <p>This field can contain arbitrary string data for custom step configuration,
     * such as JSON configuration, comma-separated values, or any other format needed
     * by the step implementation.
     *
     * @return the user data string, or null if not set
     */
    public abstract String getUserData();

    /**
     * Enumeration of workflow step types.
     *
     * <p>Each step type determines how the workflow engine processes the step and
     * what behavior it exhibits during workflow execution.
     *
     * <h3>Step Type Descriptions</h3>
     * <ul>
     *   <li><b>TASK</b> - Executes business logic by invoking a task component. The task
     *       returns a {@link com.anode.workflow.entities.steps.responses.TaskResponse}
     *       indicating success, failure, or pause.</li>
     *
     *   <li><b>PAUSE</b> - Pauses workflow execution at a specified work basket, typically
     *       for human task processing. The workflow remains paused until explicitly resumed.</li>
     *
     *   <li><b>PERSIST</b> - Forces the workflow state to be persisted to storage. Useful
     *       for ensuring state is saved before long-running operations.</li>
     *
     *   <li><b>S_ROUTE</b> - Sequential routing step that makes a decision about the next
     *       single step to execute. Returns one step name based on routing logic.</li>
     *
     *   <li><b>P_ROUTE</b> - Parallel routing step that creates multiple concurrent execution
     *       paths. Returns multiple step names that will execute in parallel.</li>
     *
     *   <li><b>P_ROUTE_DYNAMIC</b> - Dynamic parallel routing where the number of branches
     *       is determined at runtime based on data (e.g., process each order line).</li>
     *
     *   <li><b>P_JOIN</b> - Parallel join step that waits for all parallel execution paths
     *       to complete before continuing. Synchronizes concurrent branches.</li>
     * </ul>
     *
     * @see Step
     * @see Task
     * @see Route
     * @see Pause
     * @see Join
     */
    public enum StepType {
        /** Executes business logic via a task component */
        TASK,

        /** Pauses execution at a work basket for human intervention */
        PAUSE,

        /** Sequential routing - selects one next step */
        S_ROUTE,

        /** Parallel routing - creates multiple execution paths */
        P_ROUTE,

        /** Dynamic parallel routing - number of branches determined at runtime */
        P_ROUTE_DYNAMIC,

        /** Parallel join - synchronizes multiple execution paths */
        P_JOIN,

        /** Explicitly persists workflow state */
        PERSIST
    }
}
