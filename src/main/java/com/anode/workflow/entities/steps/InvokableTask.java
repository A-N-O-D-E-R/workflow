package com.anode.workflow.entities.steps;

import com.anode.workflow.entities.steps.responses.TaskResponse;

/**
 * Represents a task that can be invoked by the workflow engine.
 *
 * <p>Implementations must be thread-safe if used in parallel routes.
 * The executeStep method will be called by the workflow engine when
 * this step is reached in the workflow execution.
 *
 * <h2>Idempotency Considerations</h2>
 * <p>If using OK_PEND_EOR response type, implementations should
 * be idempotent as the step may be executed multiple times in crash
 * recovery scenarios.
 *
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * public class EmailStep implements InvokableTask {
 *     private WorkflowContext context;
 *     
 *     public EmailStep(WorkflowContext context) {
 *         this.context = context;
 *     }
 *     
 *     @Override
 *     public TaskResponse executeStep() {
 *         // Check if already sent (idempotency)
 *         if (emailAlreadySent()) {
 *             return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
 *         }
 *         
 *         // Send email
 *         sendEmail();
 *         
 *         return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
 *     }
 * }
 * }</pre>
 *
 * @see TaskResponse
 */
public interface InvokableTask {

    public TaskResponse executeStep();
}
