package com.anode.workflow.entities.workflows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Container for workflow process variables providing type-safe access methods.
 *
 * <p>The {@code WorkflowVariables} class is a wrapper around a thread-safe map of
 * {@link WorkflowVariable} objects, providing convenient typed accessor methods for
 * reading and writing workflow variables. Variables are shared across all execution
 * paths within a workflow instance and are automatically persisted as part of the
 * workflow state.
 *
 * <h2>Variable Access</h2>
 * Variables can be accessed using type-specific methods:
 * <ul>
 *   <li>{@link #getString(String)} - Retrieve as String</li>
 *   <li>{@link #getInteger(String)} - Retrieve as Integer</li>
 *   <li>{@link #getLong(String)} - Retrieve as Long</li>
 *   <li>{@link #getBoolean(String)} - Retrieve as Boolean</li>
 *   <li>{@link #getObject(String)} - Retrieve as generic Object (requires casting)</li>
 *   <li>{@link #getListObject(String)} - Retrieve as List of Objects</li>
 * </ul>
 *
 * <h2>Example Usage in Tasks</h2>
 * <pre>{@code
 * public class ValidateOrderTask implements InvokableTask {
 *     {@literal @}Override
 *     public TaskResponse invoke(WorkflowContext context) {
 *         WorkflowVariables vars = context.getProcessVariables();
 *
 *         // Read variables with type safety
 *         String orderId = vars.getString("orderId");
 *         Integer quantity = vars.getInteger("quantity");
 *         Double amount = vars.getObject("amount");
 *         Boolean isPriority = vars.getBoolean("isPriority");
 *
 *         // Validate order
 *         if (orderId == null || orderId.isEmpty()) {
 *             return TaskResponse.fail("Order ID is required");
 *         }
 *
 *         if (amount != null && amount > 10000.0) {
 *             // Set new variable for high-value orders
 *             vars.setValue("requiresApproval",
 *                 WorkflowVariable.WorkflowVariableType.BOOLEAN,
 *                 true);
 *         }
 *
 *         return TaskResponse.ok();
 *     }
 * }
 * }</pre>
 *
 * <h2>Working with Complex Objects</h2>
 * <pre>{@code
 * // Store complex objects
 * Order order = new Order("ORD-123", 1500.00);
 * vars.setValue("orderObject",
 *     WorkflowVariable.WorkflowVariableType.OBJECT,
 *     order);
 *
 * // Retrieve complex objects
 * Order retrievedOrder = vars.getObject("orderObject");
 *
 * // Store lists
 * List<LineItem> items = Arrays.asList(
 *     new LineItem("ITEM-1", 2),
 *     new LineItem("ITEM-2", 3)
 * );
 * vars.setValue("lineItems",
 *     WorkflowVariable.WorkflowVariableType.LIST_OF_OBJECT,
 *     items);
 *
 * // Retrieve lists
 * List<LineItem> retrievedItems = vars.getListObject("lineItems");
 * }</pre>
 *
 * <h2>Variable Lifecycle</h2>
 * <ul>
 *   <li>Variables are initialized when starting a workflow instance</li>
 *   <li>Tasks can read and write variables during execution</li>
 *   <li>Variables persist across workflow pauses and resumes</li>
 *   <li>Variables are shared across parallel execution paths</li>
 *   <li>Variables are stored in the database as part of {@link WorkflowInfo}</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * This class uses {@link ConcurrentHashMap} internally, making it safe for concurrent
 * access from multiple threads. However, individual variable updates are not atomic -
 * use external synchronization if you need to perform atomic read-modify-write operations.
 *
 * @see WorkflowVariable
 * @see WorkflowContext#getProcessVariables()
 * @see WorkflowInfo#getWorkflowVariables()
 * @since 0.0.1
 */
public class WorkflowVariables {
    private Map<String, WorkflowVariable> workflowVariableMap = new ConcurrentHashMap<>();

    /**
     * Constructs an empty workflow variables collection.
     */
    public WorkflowVariables() {}

    /**
     * Constructs a workflow variables collection from an existing variable map.
     *
     * <p>This constructor is used internally when retrieving variables from
     * {@link WorkflowInfo}. The provided map is used directly, not copied.
     *
     * @param workflowVariableMap the map of variables to wrap
     */
    protected WorkflowVariables(Map<String, WorkflowVariable> workflowVariableMap) {
        this.workflowVariableMap = workflowVariableMap;
    }

    public <T> T getObject(String name) {
        WorkflowVariable pv = workflowVariableMap.get(name);
        if (pv == null) {
            return null;
        } else {
            return (T) pv.getValue();
        }
    }

    public <T> List<T> getListObject(String name) {
        WorkflowVariable pv = workflowVariableMap.get(name);
        if (pv == null) {
            return null;
        } else {
            return (List<T>) pv.getValue();
        }
    }

    public Integer getInteger(String name) {
        return getObject(name);
    }

    public Long getLong(String name) {
        return getObject(name);
    }

    public String getString(String name) {
        return getObject(name);
    }

    public Boolean getBoolean(String name) {
        return getObject(name);
    }

    public String getValueAsString(String name) {
        String s = null;
        WorkflowVariable pv = workflowVariableMap.get(name);

        if (pv == null) {
            return null;
        } else {
            Object value = pv.getValue();
            switch (pv.getType()) {
                case BOOLEAN:
                    {
                        Boolean b = (Boolean) value;
                        s = b.toString();
                        break;
                    }

                case LONG:
                    {
                        Long l = (Long) value;
                        s = String.valueOf(l);
                        break;
                    }

                case INTEGER:
                    {
                        Integer i = (Integer) value;
                        s = String.valueOf(i);
                        break;
                    }
                case OBJECT:
                    {
                        s = String.valueOf(value);
                    }

                case LIST_OF_OBJECT:
                    {
                        s = String.valueOf(value);
                    }

                case STRING:
                    {
                        s = (String) value;
                        break;
                    }
            }

            return s;
        }
    }

    public WorkflowVariable.WorkflowVariableType getType(String name) {
        WorkflowVariable pv = workflowVariableMap.get(name);
        if (pv == null) {
            return null;
        } else {
            return pv.getType();
        }
    }

    public void setValue(String name, WorkflowVariable.WorkflowVariableType type, Object value) {
        WorkflowVariable pv = workflowVariableMap.get(name);
        if (pv == null) {
            pv = new WorkflowVariable(name, type, value);
        } else {
            pv.setValue(value);
        }
        workflowVariableMap.put(name, pv);
    }

    public List<WorkflowVariable> getListOfWorkflowVariable() {
        return new ArrayList<>(workflowVariableMap.values());
    }
}
