package com.anode.workflow.service;

import com.anode.workflow.entities.workflows.WorkflowContext;

/**
 * Factory interface for creating workflow component instances.
 *
 * <p>The {@code WorkflowComponantFactory} is responsible for instantiating workflow components
 * (tasks, routes, etc.) based on the workflow definition. The workflow engine calls this factory
 * when it needs to execute a step or make a routing decision, passing the workflow context which
 * contains information about which component to create.
 *
 * <h2>Component Types</h2>
 * The factory can create different types of workflow components:
 * <ul>
 *   <li><b>Tasks</b> - Business logic steps that perform work</li>
 *   <li><b>Routes</b> - Decision points that determine workflow flow</li>
 *   <li><b>Branches</b> - Conditional execution paths</li>
 *   <li><b>Joins</b> - Synchronization points for parallel paths</li>
 * </ul>
 *
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * public class MyComponentFactory implements WorkflowComponantFactory {
 *     {@literal @}Override
 *     public Object getObject(WorkflowContext context) {
 *         String componentName = context.getCompName();
 *
 *         switch (context.getCompType()) {
 *             case TASK:
 *                 return createTask(componentName);
 *             case ROUTE:
 *                 return createRoute(componentName);
 *             default:
 *                 throw new IllegalArgumentException("Unknown component type: " +
 *                     context.getCompType());
 *         }
 *     }
 *
 *     private Object createTask(String taskName) {
 *         switch (taskName) {
 *             case "validateOrder":
 *                 return new ValidateOrderTask();
 *             case "processPayment":
 *                 return new ProcessPaymentTask();
 *             default:
 *                 throw new IllegalArgumentException("Unknown task: " + taskName);
 *         }
 *     }
 *
 *     private Object createRoute(String routeName) {
 *         switch (routeName) {
 *             case "approvalRoute":
 *                 return new ApprovalRoute();
 *             default:
 *                 throw new IllegalArgumentException("Unknown route: " + routeName);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Dependency Injection Integration</h2>
 * The factory can integrate with dependency injection frameworks:
 * <pre>{@code
 * {@literal @}Component
 * public class SpringComponentFactory implements WorkflowComponantFactory {
 *     {@literal @}Autowired
 *     private ApplicationContext applicationContext;
 *
 *     {@literal @}Override
 *     public Object getObject(WorkflowContext context) {
 *         String beanName = context.getCompName();
 *         return applicationContext.getBean(beanName);
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * Implementations must be thread-safe as the same factory instance is shared across
 * multiple concurrent workflow executions. If the factory maintains state, proper
 * synchronization is required.
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li>Component creation should be fast to avoid slowing workflow execution</li>
 *   <li>Consider caching or pooling heavy components if appropriate</li>
 *   <li>Avoid expensive initialization in the factory method</li>
 * </ul>
 *
 * @see WorkflowContext
 * @since 0.0.1
 */
public interface WorkflowComponantFactory {

    /**
     * Creates a workflow component instance based on the provided context.
     *
     * <p>This method is called by the workflow engine whenever it needs to instantiate a
     * component for execution. The {@link WorkflowContext} contains all necessary information
     * to determine which component to create, including:
     * <ul>
     *   <li>Component name ({@link WorkflowContext#getCompName()})</li>
     *   <li>Component type ({@link WorkflowContext#getCompType()})</li>
     *   <li>User-defined data ({@link WorkflowContext#getUserData()})</li>
     * </ul>
     *
     * <p><b>Implementation Guidelines:</b>
     * <ul>
     *   <li>Return a new instance or a cached instance based on your design</li>
     *   <li>Throw {@link IllegalArgumentException} for unknown component names/types</li>
     *   <li>Ensure thread-safety if components are reused across workflow instances</li>
     *   <li>Keep initialization lightweight to avoid blocking workflow execution</li>
     * </ul>
     *
     * @param workflowContext the workflow context containing component identification information
     * @return a workflow component instance (task, route, branch, or join) ready for execution
     * @throws IllegalArgumentException if the requested component name or type is not recognized
     * @throws RuntimeException if component instantiation fails
     *
     * @see WorkflowContext#getCompName() for retrieving the component name
     * @see WorkflowContext#getCompType() for retrieving the component type
     */
    public Object getObject(WorkflowContext workflowContext);
}
