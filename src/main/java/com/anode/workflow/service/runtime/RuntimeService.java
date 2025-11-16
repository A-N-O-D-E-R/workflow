package com.anode.workflow.service.runtime;

import com.anode.tool.StringUtils;
import com.anode.tool.service.CommonService;
import com.anode.workflow.entities.events.EventType;
import com.anode.workflow.entities.sla.Milestone;
import com.anode.workflow.entities.sla.Milestone.Setup;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.entities.workflows.WorkflowInfo;
import com.anode.workflow.entities.workflows.WorkflowVariable;
import com.anode.workflow.entities.workflows.WorkflowVariables;
import com.anode.workflow.entities.workflows.paths.ExecPath;
import com.anode.workflow.exceptions.WorkflowRuntimeException;
import com.anode.workflow.service.EventHandler;
import com.anode.workflow.service.SlaQueueManager;
import com.anode.workflow.service.SlaQueueService;
import com.anode.workflow.service.WorkflowComponantFactory;
import com.anode.workflow.service.WorkflowInfoUtils;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Core service for managing workflow execution lifecycle.
 *
 * <p>The {@code RuntimeService} is the primary interface for starting, resuming, and managing
 * workflow instances. It coordinates workflow execution, handles persistence, manages SLA tracking,
 * and fires lifecycle events. Each workflow instance is identified by a unique case ID and
 * progresses through steps defined in the workflow definition.
 *
 * <h2>Key Responsibilities</h2>
 * <ul>
 *   <li><b>Workflow Lifecycle Management</b> - Start, resume, reopen workflow instances</li>
 *   <li><b>State Persistence</b> - Coordinate data persistence through DAO layer</li>
 *   <li><b>Event Coordination</b> - Fire events to {@link EventHandler} at lifecycle milestones</li>
 *   <li><b>SLA Management</b> - Track and manage milestone-based SLAs</li>
 *   <li><b>Crash Recovery</b> - Resume workflows after application restart</li>
 * </ul>
 *
 * <h2>Typical Usage</h2>
 * <pre>{@code
 * // Initialize the runtime service
 * CommonService dao = new FileDao("./workflow-data");
 * WorkflowComponantFactory factory = new MyComponentFactory();
 * EventHandler handler = new MyEventHandler();
 *
 * RuntimeService runtimeService = new RuntimeService(dao, factory, handler, null);
 *
 * // Start a new workflow
 * WorkflowDefinition workflow = loadWorkflowDefinition();
 * WorkflowVariables variables = new WorkflowVariables();
 * variables.add("orderId", "ORD-12345");
 *
 * WorkflowContext context = runtimeService.startCase(
 *     "CASE-001",
 *     workflow,
 *     variables,
 *     null
 * );
 *
 * // Resume a paused workflow
 * context = runtimeService.resumeCase("CASE-001");
 *
 * // Check if workflow was already started
 * boolean started = runtimeService.isCaseStarted("CASE-001");
 * }</pre>
 *
 * <h2>Workflow States</h2>
 * Workflows progress through the following states:
 * <ul>
 *   <li><b>Not Started</b> - Case ID not yet used</li>
 *   <li><b>Running</b> - Actively executing steps</li>
 *   <li><b>Pending</b> - Paused, waiting for external input (e.g., human task)</li>
 *   <li><b>Completed</b> - All steps executed successfully</li>
 *   <li><b>Failed</b> - Execution stopped due to error</li>
 * </ul>
 *
 * <h2>Crash Recovery</h2>
 * The service supports automatic recovery from application crashes:
 * <pre>{@code
 * // After application restart, recover incomplete workflows
 * RuntimeService runtimeService = new RuntimeService(dao, factory, handler, null);
 *
 * // This will resume all workflows that were interrupted
 * List<String> caseIds = dao.getAllIncompleteCaseIds();
 * for (String caseId : caseIds) {
 *     runtimeService.resumeCase(caseId);
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * This class is thread-safe for operations on different case IDs. Operations on the same
 * case ID should be serialized externally to avoid race conditions.
 *
 * @see WorkflowDefinition
 * @see WorkflowContext
 * @see EventHandler
 * @see WorkflowComponantFactory
 * @since 0.0.1
 */
@Slf4j
public class RuntimeService {

    /** Separator used in persistence keys. */
    public static String SEP = "_";

    /** Key prefix for audit log entries. */
    public static final String AUDIT_LOG = "audit_log";

    /** Key prefix for workflow info storage. */
    public static final String WORKFLOW_INFO = "worflow_info";

    /** Key prefix for workflow definition (journey) storage. */
    public static final String JOURNEY = "journey";

    /** Key prefix for SLA configuration storage. */
    public static final String JOURNEY_SLA = "journey_sla";

    // variables are protected so that they can be accessed by classes in the same package
    protected CommonService dao = null;
    protected WorkflowComponantFactory factory = null;
    protected EventHandler eventHandler = null;
    protected WorkflowDefinition workflowDefinition = null;
    protected WorkflowInfo workflowInfo = null;
    protected SlaQueueManager slaQm = null;
    // Made volatile for visibility across threads in concurrent workflow execution
    protected volatile String lastPendWorkBasket = null;
    protected volatile String lastPendStep = null;
    private List<Milestone> sla = null;

    /**
     * Constructs a new RuntimeService with required dependencies.
     *
     * @param dao the data access object for persisting workflow state
     * @param factory the factory for creating workflow component instances
     * @param eventHandler the handler for workflow lifecycle events (may be null)
     * @param slaQm the SLA queue manager for tracking milestones (may be null)
     */
    public RuntimeService(
            CommonService dao,
            WorkflowComponantFactory factory,
            EventHandler eventHandler,
            SlaQueueManager slaQm) {
        this.dao = dao;
        this.factory = factory;
        this.eventHandler = eventHandler;
        this.slaQm = slaQm;
    }

    private void invokeEvent(EventType event, WorkflowContext workflowContext) {
        if (eventHandler == null) {
            return;
        }

        String wb = workflowContext.getPendWorkBasket();
        wb = (wb == null) ? "" : wb;
        log.info(
                "Case id -> {}, raising event -> {}, comp name -> {}, work basket -> {}",
                workflowInfo.getCaseId(),
                event.name(),
                workflowContext.getCompName(),
                wb);

        // invoke event on application
        switch (event) {
            case ON_PROCESS_START:
            case ON_PROCESS_RESUME:
            case ON_PROCESS_COMPLETE:
            case ON_PROCESS_PEND:
            case ON_PROCESS_REOPEN:
                eventHandler.invoke(event, workflowContext);
                break;

            case ON_PERSIST:
            case ON_TICKET_RAISED:
                try {
                    workflowInfo.getLock().lock();
                    eventHandler.invoke(event, workflowContext);
                } finally {
                    workflowInfo.getLock().unlock();
                }
                break;
        }
    }

    private void raiseSla(EventType event, WorkflowContext workflowContext) {
        if ((sla == null) || (slaQm == null)) {
            return;
        }

        switch (event) {
            case ON_PROCESS_START:
            case ON_PROCESS_RESUME:
            case ON_PROCESS_COMPLETE:
            case ON_PROCESS_PEND:
            case ON_PROCESS_REOPEN:
                raiseSlaEvent(event, workflowContext);
                break;

            case ON_PERSIST:
            case ON_TICKET_RAISED:
                break;
        }
    }

    private void setPrevPendWorkbasket(EventType event) {
        if (event == EventType.ON_PROCESS_PEND) {
            // set the prev pend work basket
            ExecPath ep = workflowInfo.getExecPath(workflowInfo.getPendExecPath());
            ep.setPrevPendWorkBasket(ep.getPendWorkBasket());
        }
    }

    protected void invokeEventHandler(EventType event, WorkflowContext workflowContext) {
        invokeEvent(event, workflowContext);
        raiseSla(event, workflowContext);
        setPrevPendWorkbasket(event);
    }

    /**
     * Checks if a workflow case has already been started.
     *
     * <p>This method queries the persistence layer to determine if a case with the given ID
     * has been previously started. Use this to prevent duplicate case creation or to verify
     * case existence before attempting resume operations.
     *
     * @param caseId the unique identifier of the workflow case to check
     * @return {@code true} if the case exists (started), {@code false} otherwise
     *
     * @see #startCase(String, WorkflowDefinition, WorkflowVariables, List)
     */
    public boolean isCaseStarted(String caseId) {
        String key = WORKFLOW_INFO + SEP + caseId;
        Object d = dao.get(Object.class, key);
        if (d == null) {
            return false;
        } else {
            return true;
        }
    }

    private void abortIfStarted(String caseId) {
        String key = WORKFLOW_INFO + SEP + caseId;

        Object d = dao.get(Object.class, key);
        if (d != null) {
            throw new WorkflowRuntimeException(
                    "Cannot start a case which is already started. Case Id -> " + caseId);
        }
    }

    /**
     * Starts a new workflow instance with the specified configuration.
     *
     * <p>This method initiates a new workflow execution by:
     * <ol>
     *   <li>Validating that the case ID is not already in use</li>
     *   <li>Persisting the workflow definition and initial variables</li>
     *   <li>Setting up SLA milestones if provided</li>
     *   <li>Firing the {@link EventType#ON_PROCESS_START} event</li>
     *   <li>Beginning step execution</li>
     * </ol>
     *
     * <p>The workflow will execute asynchronously in the background. Steps are processed
     * according to their execution paths and order. The workflow may complete immediately
     * or pause at steps requiring human intervention.
     *
     * <h3>Example</h3>
     * <pre>{@code
     * // Load workflow definition
     * WorkflowDefinition workflow = WorkflowDefinition.fromJson(jsonString);
     *
     * // Prepare initial variables
     * WorkflowVariables variables = new WorkflowVariables();
     * variables.add("orderId", "ORD-12345");
     * variables.add("customerId", "CUST-001");
     * variables.add("amount", 299.99);
     *
     * // Configure SLA milestones
     * List<Milestone> sla = Arrays.asList(
     *     new Milestone("processing-complete", 60), // 60 minutes
     *     new Milestone("order-shipped", 24 * 60)   // 24 hours
     * );
     *
     * // Start the workflow
     * WorkflowContext context = runtimeService.startCase(
     *     "CASE-" + UUID.randomUUID(),
     *     workflow,
     *     variables,
     *     sla
     * );
     *
     * System.out.println("Workflow started: " + context.getCaseId());
     * }</pre>
     *
     * <h3>Crash Recovery</h3>
     * If the application crashes after the workflow definition is persisted but before the
     * first step executes, the next call to {@code startCase} with the same case ID will
     * detect the existing definition and resume execution from the beginning.
     *
     * @param caseId unique identifier for this workflow instance; must not already exist
     * @param journey the workflow definition specifying steps, routes, and execution paths
     * @param pvs initial process variables available to all steps (may be null or empty)
     * @param journeySla list of SLA milestones to track (may be null for no SLA tracking)
     * @return the {@link WorkflowContext} representing the current state after initial execution
     *
     * @throws WorkflowRuntimeException if the case ID is already in use
     * @throws WorkflowRuntimeException if workflow execution fails critically
     *
     * @see #resumeCase(String)
     * @see #isCaseStarted(String)
     * @see WorkflowDefinition
     * @see WorkflowVariables
     * @see Milestone
     */
    public WorkflowContext startCase(
            String caseId,
            WorkflowDefinition journey,
            WorkflowVariables pvs,
            List<Milestone> journeySla) {
        if (pvs == null) {
            pvs = new WorkflowVariables();
        }

        abortIfStarted(caseId);

        // check if the journey definition file exists. If it does, then we need to treat it as a
        // case of
        // crash where the process was successfully started but the first step could not be executed
        String key = JOURNEY + SEP + caseId;
        boolean hasAlreadyStarted = false;
        if (dao.get(WorkflowDefinition.class, key) != null) {
            hasAlreadyStarted = true;
        }

        // read the process definition and get process info
        workflowDefinition = journey;
        // read the process definition and get process info
        dao.saveOrUpdate(JOURNEY + SEP + caseId, workflowDefinition);
        workflowInfo = WorkflowInfoUtils.getWorkflowInfo(dao, caseId, workflowDefinition);

        // write and get the sla configuration
        if (journeySla != null) {
            sla = journeySla;
            dao.saveOrUpdate(JOURNEY_SLA + SEP + caseId, sla);
        }

        // uworkflowDefinitionate process variables
        List<WorkflowVariable> list = pvs.getListOfWorkflowVariable();
        for (WorkflowVariable pv : list) {
            workflowInfo.setWorkflowVariable(pv);
        }

        WorkflowContext workflowContext = null;
        if (hasAlreadyStarted == false) {
            log.info("Case id -> " + workflowInfo.getCaseId() + ", successfully created case");
            workflowContext = WorkflowContext.forEvent(EventType.ON_PROCESS_START, this, ".");
            invokeEventHandler(EventType.ON_PROCESS_START, workflowContext);
        }

        workflowContext = resumeCase(caseId, false, null);
        return workflowContext;
    }

    private WorkflowContext resumeCase(
            String caseId, boolean raiseResumeEvent, WorkflowVariables workflowVariables) {
        if (raiseResumeEvent == true) {
            // we are being called on our own
            // read process definition
            String key = JOURNEY + SEP + caseId;
            if (dao.get(WorkflowDefinition.class, key) == null) {
                throw new WorkflowRuntimeException(
                        "Could not resume case. No process definition found. Case id -> " + caseId);
            }
            workflowDefinition = dao.get(WorkflowDefinition.class, key);
            workflowInfo = WorkflowInfoUtils.getWorkflowInfo(dao, caseId, workflowDefinition);
            workflowInfo.isPendAtSameStep = true;

            // uworkflowDefinitionate process variables. We will add or uworkflowDefinitionate the
            // ones passed in but not delete any
            if (workflowVariables != null) {
                List<WorkflowVariable> list = workflowVariables.getListOfWorkflowVariable();
                for (WorkflowVariable workflowVariable : list) {
                    workflowInfo.setWorkflowVariable(workflowVariable);
                }
            }

            // read sla configuration
            key = JOURNEY_SLA + SEP + caseId;
            sla = dao.get(List.class, key);
        }

        // check if we have already completed
        if (workflowInfo.isCaseCompleted() == true) {
            throw new WorkflowRuntimeException(
                    "Cannot resume a case that has already completed. Case id -> "
                            + workflowInfo.getCaseId());
        }

        WorkflowContext workflowContext = null;
        if (raiseResumeEvent) {
            workflowContext =
                    WorkflowContext.forEvent(
                            EventType.ON_PROCESS_RESUME, this, workflowInfo.getPendExecPath());
            invokeEventHandler(EventType.ON_PROCESS_RESUME, workflowContext);
        }

        if (workflowInfo.getTicket().isEmpty() == false) {
            workflowContext =
                    WorkflowContext.forEvent(
                            EventType.ON_TICKET_RAISED, this, workflowInfo.getPendExecPath());
            invokeEventHandler(EventType.ON_TICKET_RAISED, workflowContext);
        }

        // initiate on the current thread
        ExecThreadTask task = new ExecThreadTask(this);
        workflowContext = task.execute();

        return workflowContext;
    }

    /**
     * Resumes a paused workflow instance.
     *
     * <p>This method continues execution of a workflow that was previously paused, typically
     * due to a human task or external event requirement. The workflow resumes from the step
     * where it was paused and continues executing subsequent steps.
     *
     * <h3>When to Use</h3>
     * Call this method when:
     * <ul>
     *   <li>A human task in a work basket has been completed</li>
     *   <li>External data required by the workflow becomes available</li>
     *   <li>A timeout or scheduled event triggers workflow continuation</li>
     *   <li>Recovering from application restart (crash recovery)</li>
     * </ul>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * // Workflow paused waiting for approval
     * // ... user completes approval in work basket ...
     *
     * // Resume the workflow
     * WorkflowContext context = runtimeService.resumeCase("CASE-12345");
     *
     * if (context.getCompName().isEmpty()) {
     *     System.out.println("Workflow completed");
     * } else {
     *     System.out.println("Workflow paused at: " + context.getPendWorkBasket());
     * }
     * }</pre>
     *
     * @param caseId the unique identifier of the workflow instance to resume
     * @return the {@link WorkflowContext} representing the state after resumption
     *
     * @throws WorkflowRuntimeException if the case ID does not exist
     * @throws WorkflowRuntimeException if the workflow has already completed
     * @throws WorkflowRuntimeException if workflow execution fails
     *
     * @see #resumeCase(String, WorkflowVariables)
     * @see #startCase(String, WorkflowDefinition, WorkflowVariables, List)
     */
    public WorkflowContext resumeCase(String caseId) {
        return resumeCase(caseId, true, null);
    }

    /**
     * Resumes a paused workflow instance with updated process variables.
     *
     * <p>This method is similar to {@link #resumeCase(String)} but allows updating or adding
     * process variables before resuming execution. This is useful when external data collected
     * during the pause needs to be injected into the workflow.
     *
     * <h3>Variable Handling</h3>
     * <ul>
     *   <li>Existing variables with matching names are updated with new values</li>
     *   <li>New variables are added to the workflow context</li>
     *   <li>Variables not specified in {@code pvs} remain unchanged</li>
     * </ul>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * // Workflow paused for manual review
     * // Reviewer provides additional information
     *
     * WorkflowVariables updates = new WorkflowVariables();
     * updates.add("reviewerComments", "Approved with conditions");
     * updates.add("reviewedBy", "john.doe");
     * updates.add("reviewDate", LocalDate.now());
     *
     * // Resume with updated variables
     * WorkflowContext context = runtimeService.resumeCase("CASE-12345", updates);
     * }</pre>
     *
     * @param caseId the unique identifier of the workflow instance to resume
     * @param pvs process variables to add or update before resuming (may be null)
     * @return the {@link WorkflowContext} representing the state after resumption
     *
     * @throws WorkflowRuntimeException if the case ID does not exist
     * @throws WorkflowRuntimeException if the workflow has already completed
     * @throws WorkflowRuntimeException if workflow execution fails
     *
     * @see #resumeCase(String)
     * @see WorkflowVariables
     */
    public WorkflowContext resumeCase(String caseId, WorkflowVariables pvs) {
        return resumeCase(caseId, true, pvs);
    }

    /**
     * Reopens a completed workflow instance for additional processing.
     *
     * <p>This method allows restarting a workflow that has already completed, useful for
     * scenarios like reopening a closed case, processing amendments, or handling exceptions
     * that require revisiting completed workflows.
     *
     * @param caseId the unique identifier of the completed workflow to reopen
     * @param ticket a ticket/reference identifier for the reopen operation
     * @param pendBeforeResume if {@code true}, pause at specified work basket before resuming
     * @param pendWb the work basket where the workflow should pause (required if pendBeforeResume is true)
     * @return the {@link WorkflowContext} representing the state after reopening
     *
     * @throws WorkflowRuntimeException if the case has not completed
     * @throws WorkflowRuntimeException if pendBeforeResume is true but pendWb is null/empty
     * @throws WorkflowRuntimeException if ticket is null or empty
     *
     * @see #reopenCase(String, String, boolean, String, WorkflowVariables)
     */
    public WorkflowContext reopenCase(
            String caseId, String ticket, boolean pendBeforeResume, String pendWb) {
        return reopenCase(caseId, ticket, pendBeforeResume, pendWb, null);
    }

    /**
     * Reopens a completed workflow instance with updated variables.
     *
     * <p>This method reopens a completed workflow and optionally adds or updates process
     * variables before resuming execution. The workflow can be configured to pause at a
     * specific work basket or continue execution immediately.
     *
     * <h3>Example - Reopen for Amendment</h3>
     * <pre>{@code
     * // Order completed and shipped, customer requests changes
     * WorkflowVariables updates = new WorkflowVariables();
     * updates.add("amendmentReason", "Customer changed delivery address");
     * updates.add("newAddress", updatedAddress);
     * updates.add("reopenedBy", "customer-service");
     *
     * // Reopen and pause for manual review
     * WorkflowContext context = runtimeService.reopenCase(
     *     "CASE-12345",
     *     "TICKET-999",
     *     true,                    // Pause before resuming
     *     "amendment-review",      // Work basket for review
     *     updates
     * );
     * }</pre>
     *
     * @param caseId the unique identifier of the completed workflow to reopen
     * @param ticket a ticket/reference identifier for the reopen operation
     * @param pendBeforeResume if {@code true}, pause at specified work basket before resuming
     * @param pendWb the work basket where the workflow should pause (required if pendBeforeResume is true)
     * @param pvs process variables to add or update (may be null)
     * @return the {@link WorkflowContext} representing the state after reopening
     *
     * @throws WorkflowRuntimeException if the case has not completed
     * @throws WorkflowRuntimeException if pendBeforeResume is true but pendWb is null/empty
     * @throws WorkflowRuntimeException if ticket is null or empty
     * @throws WorkflowRuntimeException if the case ID does not exist
     *
     * @see #reopenCase(String, String, boolean, String)
     */
    public WorkflowContext reopenCase(
            String caseId,
            String ticket,
            boolean pendBeforeResume,
            String pendWb,
            WorkflowVariables pvs) {
        if (pendBeforeResume == true) {
            if (StringUtils.isNullOrEmpty(pendWb)) {
                throw new WorkflowRuntimeException(
                        caseId + " -> pending work basket cannot be null or empty");
            }
        }
        if (StringUtils.isNullOrEmpty(ticket)) {
            throw new WorkflowRuntimeException(caseId + " -> ticket cannot be null or empty");
        }

        // read journey file, process definition, process info and sla file
        String key = JOURNEY + SEP + caseId;
        if (dao.get(WorkflowDefinition.class, key) == null) {
            throw new WorkflowRuntimeException(
                    "Could not resume case. No process definition found. Case id -> " + caseId);
        }
        workflowDefinition = dao.get(WorkflowDefinition.class, key);
        workflowInfo = WorkflowInfoUtils.getWorkflowInfo(dao, caseId, workflowDefinition);
        workflowInfo.isPendAtSameStep = false;

        // uworkflowDefinitionate process variables. We will add or uworkflowDefinitionate the ones
        // passed in but not delete any
        if (pvs != null) {
            List<WorkflowVariable> list = pvs.getListOfWorkflowVariable();
            for (WorkflowVariable pv : list) {
                workflowInfo.setWorkflowVariable(pv);
            }
        }

        key = JOURNEY_SLA + SEP + caseId;
        sla = dao.get(List.class, key);

        // check that the case should be completed
        if (workflowInfo.isCaseCompleted() == false) {
            throw new WorkflowRuntimeException(
                    "Case id " + caseId + "-> cannot reopen a case which has not yet completed");
        }

        // uworkflowDefinitionate relevant fields in the process info
        workflowInfo.getSetter().setPendExecPath(".");
        workflowInfo.setCaseCompleted(false);
        ExecPath ep = workflowInfo.getExecPath(".");
        if (pendBeforeResume == true) {
            ep.setPendWorkBasket(pendWb);
            ep.setStepResponseType(StepResponseType.OK_PEND);
        }
        ep.setTicket(ticket);
        workflowInfo.getSetter().setTicket(ticket);

        // write back the process info
        workflowInfo.setHibid(null);
        dao.saveOrUpdate(WORKFLOW_INFO + SEP + caseId, workflowInfo);

        WorkflowContext workflowContext = null;

        // invoke event handler
        workflowContext = WorkflowContext.forEvent(EventType.ON_PROCESS_REOPEN, this, ".");
        invokeEventHandler(EventType.ON_PROCESS_REOPEN, workflowContext);

        if (pendBeforeResume == true) {
            workflowContext = WorkflowContext.forEvent(EventType.ON_PROCESS_PEND, this, ".");
            invokeEventHandler(EventType.ON_PROCESS_PEND, workflowContext);
        }

        // resume the case if required
        if (pendBeforeResume == false) {
            workflowContext = resumeCase(caseId, true, null);
        }

        return workflowContext;
    }

    private void raiseSlaEvent(EventType event, WorkflowContext workflowContext) {
        switch (event) {
            case ON_PROCESS_START:
                {
                    SlaQueueService.enqueueCaseStartMilestones(workflowContext, sla, slaQm);
                    break;
                }

            case ON_PROCESS_REOPEN:
                {
                    SlaQueueService.enqueueCaseRestartMilestones(workflowContext, sla, slaQm);
                    break;
                }

            case ON_PROCESS_PEND:
                {
                    ExecPath ep = workflowInfo.getExecPath(workflowInfo.getPendExecPath());
                    String prevPendWorkBasket = ep.getPrevPendWorkBasket();
                    String pendWorkBasket = ep.getPendWorkBasket();
                    String tbcWorkBasket = ep.getTbcSlaWorkBasket();

                    if (workflowInfo.isPendAtSameStep == false) {
                        if (prevPendWorkBasket.equals(tbcWorkBasket)) {
                            SlaQueueService.dequeueWorkBasketMilestones(
                                    workflowContext, prevPendWorkBasket, sla, slaQm);
                        } else {
                            SlaQueueService.dequeueWorkBasketMilestones(
                                    workflowContext, prevPendWorkBasket, sla, slaQm);
                            SlaQueueService.dequeueWorkBasketMilestones(
                                    workflowContext, tbcWorkBasket, sla, slaQm);
                        }
                        SlaQueueService.enqueueWorkBasketMilestones(
                                workflowContext,
                                Setup.work_basket_exit,
                                prevPendWorkBasket,
                                sla,
                                slaQm);
                        SlaQueueService.enqueueWorkBasketMilestones(
                                workflowContext,
                                Setup.work_basket_entry,
                                pendWorkBasket,
                                sla,
                                slaQm);
                        ep.setTbcSlaWorkBasket("");
                        break;
                    }

                    // handling is_pend_at_same_step
                    if (prevPendWorkBasket.equals(pendWorkBasket) == false) {
                        // means that the first pend at this step was a pend_eor or error pend
                        if (ep.getStepResponseType() == StepResponseType.ERROR_PEND) {
                            if (prevPendWorkBasket.equals(tbcWorkBasket)) {
                                SlaQueueService.enqueueWorkBasketMilestones(
                                        workflowContext,
                                        Setup.work_basket_entry,
                                        pendWorkBasket,
                                        sla,
                                        slaQm);
                            } else {
                                SlaQueueService.dequeueWorkBasketMilestones(
                                        workflowContext, prevPendWorkBasket, sla, slaQm);
                                SlaQueueService.enqueueWorkBasketMilestones(
                                        workflowContext,
                                        Setup.work_basket_exit,
                                        prevPendWorkBasket,
                                        sla,
                                        slaQm);
                                SlaQueueService.enqueueWorkBasketMilestones(
                                        workflowContext,
                                        Setup.work_basket_entry,
                                        pendWorkBasket,
                                        sla,
                                        slaQm);
                            }
                        } else if (ep.getStepResponseType() == StepResponseType.OK_PEND_EOR) {
                            if (prevPendWorkBasket.equals(tbcWorkBasket)) {
                                SlaQueueService.dequeueWorkBasketMilestones(
                                        workflowContext, prevPendWorkBasket, sla, slaQm);
                                SlaQueueService.enqueueWorkBasketMilestones(
                                        workflowContext,
                                        Setup.work_basket_exit,
                                        prevPendWorkBasket,
                                        sla,
                                        slaQm);
                                SlaQueueService.enqueueWorkBasketMilestones(
                                        workflowContext,
                                        Setup.work_basket_entry,
                                        pendWorkBasket,
                                        sla,
                                        slaQm);
                                ep.setTbcSlaWorkBasket(pendWorkBasket);
                            } else {
                                SlaQueueService.dequeueWorkBasketMilestones(
                                        workflowContext, prevPendWorkBasket, sla, slaQm);
                                SlaQueueService.enqueueWorkBasketMilestones(
                                        workflowContext,
                                        Setup.work_basket_exit,
                                        prevPendWorkBasket,
                                        sla,
                                        slaQm);

                                if (pendWorkBasket.equals(tbcWorkBasket) == false) {
                                    SlaQueueService.dequeueWorkBasketMilestones(
                                            workflowContext, tbcWorkBasket, sla, slaQm);
                                    SlaQueueService.enqueueWorkBasketMilestones(
                                            workflowContext,
                                            Setup.work_basket_exit,
                                            tbcWorkBasket,
                                            sla,
                                            slaQm);
                                    SlaQueueService.enqueueWorkBasketMilestones(
                                            workflowContext,
                                            Setup.work_basket_entry,
                                            pendWorkBasket,
                                            sla,
                                            slaQm);
                                    ep.setTbcSlaWorkBasket(pendWorkBasket);
                                } else {
                                    // nothing to do
                                }
                            }
                        } else if (ep.getStepResponseType() == StepResponseType.OK_PEND) {
                            // this situation cannot happen
                        }
                    } else {
                        // nothing to do
                    }

                    break;
                }

            case ON_PROCESS_RESUME:
                {
                    ExecPath ep = workflowInfo.getExecPath(workflowInfo.getPendExecPath());
                    String pendWorkBasket = ep.getPendWorkBasket();
                    StepResponseType urt = ep.getStepResponseType();
                    if (urt == StepResponseType.OK_PEND_EOR) {
                        // set it to be used in the next pend or when the process moves ahead
                        ep.setTbcSlaWorkBasket(pendWorkBasket);
                    }
                    break;
                }

            case ON_PROCESS_COMPLETE:
                slaQm.dequeueAll(workflowContext);
                break;
        }
    }

    /**
     * Returns the workflow definition for the currently executing workflow.
     *
     * @return the {@link WorkflowDefinition} containing workflow structure and configuration
     */
    public WorkflowDefinition getWorkflowDefinition() {
        return workflowDefinition;
    }

    /**
     * Returns the workflow runtime information for the currently executing workflow.
     *
     * @return the {@link WorkflowInfo} containing current execution state and variables
     */
    public WorkflowInfo getWorkflowInfo() {
        return workflowInfo;
    }

    /**
     * Sets the name of the last step where the workflow was paused.
     *
     * @param stepName the step name where the workflow paused
     */
    public void setLastPendStep(String stepName) {
        this.lastPendStep = stepName;
    }

    /**
     * Sets the name of the last work basket where the workflow was paused.
     *
     * @param pendWorkBasket the work basket name where the workflow paused
     */
    public void setLastPendWorkBasket(String pendWorkBasket) {
        this.lastPendWorkBasket = pendWorkBasket;
    }

    /**
     * Returns the name of the last step where the workflow was paused.
     *
     * @return the step name, or null if never paused
     */
    public String getLastPendStep() {
        return lastPendStep;
    }

    /**
     * Returns the name of the last work basket where the workflow was paused.
     *
     * @return the work basket name, or null if never paused
     */
    public String getLastPendWorkBasket() {
        return lastPendWorkBasket;
    }
}
