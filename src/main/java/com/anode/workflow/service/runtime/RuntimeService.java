package com.anode.workflow.service.runtime;

import com.anode.tool.StringUtils;
import com.anode.workflow.CommonDao;
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

@Slf4j
public class RuntimeService {

    public static String SEP = "_";
    public static final String AUDIT_LOG = "audit_log";
    public static final String WORKFLOW_INFO = "worflow_info";
    public static final String JOURNEY = "journey";
    public static final String JOURNEY_SLA = "journey_sla";

    // variables are protected so that they can be accessed by classes in the same package
    protected CommonDao dao = null;
    protected WorkflowComponantFactory factory = null;
    protected EventHandler eventHandler = null;
    protected WorkflowDefinition workflowDefinition = null;
    protected WorkflowInfo workflowInfo = null;
    protected SlaQueueManager slaQm = null;
    protected String lastPendWorkBasket = null;
    protected String lastPendStep = null;
    private List<Milestone> sla = null;

    public RuntimeService(
            CommonDao dao,
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

    public WorkflowContext resumeCase(String caseId) {
        return resumeCase(caseId, true, null);
    }

    public WorkflowContext resumeCase(String caseId, WorkflowVariables pvs) {
        return resumeCase(caseId, true, pvs);
    }

    public WorkflowContext reopenCase(
            String caseId, String ticket, boolean pendBeforeResume, String pendWb) {
        return reopenCase(caseId, ticket, pendBeforeResume, pendWb, null);
    }

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

    public WorkflowDefinition getWorkflowDefinition() {
        return workflowDefinition;
    }

    public WorkflowInfo getWorkflowInfo() {
        return workflowInfo;
    }

    public void setLastPendStep(String stepName) {
        this.lastPendStep = stepName;
    }

    public void setLastPendWorkBasket(String pendWorkBasket) {
        this.lastPendWorkBasket = pendWorkBasket;
    }

    public String getLastPendStep() {
        return lastPendStep;
    }

    public String getLastPendWorkBasket() {
        return lastPendWorkBasket;
    }
}
