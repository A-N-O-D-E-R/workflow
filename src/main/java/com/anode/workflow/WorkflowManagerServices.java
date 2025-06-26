package com.anode.workflow;

import com.anode.workflow.entities.sla.Milestone;
import com.anode.workflow.entities.sla.Milestone.Setup;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.entities.workflows.WorkflowInfo;
import com.anode.workflow.entities.workflows.paths.ExecPath;
import com.anode.workflow.exceptions.WorkflowRuntimeException;
import com.anode.workflow.service.AuditLogService;
import com.anode.workflow.service.SlaQueueManager;
import com.anode.workflow.service.SlaQueueService;
import com.anode.workflow.service.WorkflowInfoUtils;
import com.anode.workflow.service.runtime.RuntimeService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowManagerServices {
    protected CommonDao dao = null;
    protected WorkManager workManager = null;
    protected SlaQueueManager slaQm = null;
    protected WorkflowInfo workflowInfo = null;
    protected WorkflowDefinition workflowDefinition = null;
    protected List<Milestone> sla = null;

    protected WorkflowManagerServices(
            CommonDao dao, WorkManager workManager, SlaQueueManager slaQm) {
        this.dao = dao;
        this.workManager = workManager;
        this.slaQm = slaQm;
    }

    public void changeWorkBasket(String caseId, String newWb) {
        setup(caseId);

        // update process info
        ExecPath execPath = workflowInfo.getExecPath(workflowInfo.getPendExecPath());
        String prevWb = execPath.getPrevPendWorkBasket();
        String currWb = execPath.getPendWorkBasket();
        String toBeClearedSlaWorkBasket = execPath.getTbcSlaWorkBasket();
        execPath.setPrevPendWorkBasket(currWb);
        execPath.setPendWorkBasket(newWb);

        // get pc as per updated workflowInfo
        WorkflowContext pc =
                WorkflowContext.forWorkflowMmanagerService(workflowDefinition, workflowInfo);

        try {
            // call the work manager on the application
            if (workManager != null) {
                workManager.changeWorkBasket(pc, currWb, newWb);
            }
        } catch (Exception e) {
            log.error(
                    "Error encountered while invoking work manager in the application. Case id ->"
                            + " {}, error message -> {}",
                    workflowInfo.getCaseId(),
                    e.getMessage());

            // undo the changes
            execPath.setPrevPendWorkBasket(prevWb);
            execPath.setPendWorkBasket(currWb);

            throw e;
        }

        // enqueue / dequeue as required
        if (currWb.equals(newWb) == false) {
            if (currWb.equals(toBeClearedSlaWorkBasket) == false) {
                if (slaQm != null) {
                    SlaQueueService.dequeueWorkBasketMilestones(pc, currWb, sla, slaQm);
                }
            }

            if (newWb.equals(toBeClearedSlaWorkBasket) == false) {
                if ((sla != null) && (slaQm != null)) {
                    SlaQueueService.enqueueWorkBasketMilestones(
                            pc, Setup.work_basket_entry, newWb, sla, slaQm);
                }
            }
        }

        // copy the new work basket into prev work basket
        execPath.setPrevPendWorkBasket(newWb);

        // write audit log
        AuditLogService.writeAuditLog(dao, workflowInfo, null, null, "WorkflowManagerService");

        // process info
        dao.saveOrUpdate(
                RuntimeService.WORKFLOW_INFO + RuntimeService.SEP + workflowInfo.getCaseId(),
                workflowInfo);
    }

    private void setup(String caseId) {
        String key = RuntimeService.JOURNEY + RuntimeService.SEP + caseId;

        if (dao.get(WorkflowDefinition.class, key) == null) {
            throw new WorkflowRuntimeException(
                    "Journey file for case id " + caseId + " does not exist");
        }

        // read the process definition and get process info
        workflowDefinition = dao.get(WorkflowDefinition.class, key);
        workflowInfo = WorkflowInfoUtils.getWorkflowInfo(dao, caseId, workflowDefinition);

        key = RuntimeService.JOURNEY_SLA + RuntimeService.SEP + caseId;
        sla = dao.get(List.class, key);
    }

    public String getPendWorkbasket(String caseId) {
        setup(caseId);
        return workflowInfo.getPendWorkBasket();
    }
}
