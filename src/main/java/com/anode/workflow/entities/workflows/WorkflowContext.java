package com.anode.workflow.entities.workflows;

import com.anode.workflow.entities.events.EventType;
import com.anode.workflow.entities.steps.Step.StepType;
import com.anode.workflow.service.ErrorHandler;
import com.anode.workflow.service.runtime.RuntimeService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NoArgsConstructor
@Getter
@ToString
public class WorkflowContext {

    private String journeyName = null;
    private String caseId = null;
    private String stepName = null;
    private String compName = null;
    private String userData = null;
    private StepType compType = null;
    private WorkflowVariables processVariables = null;
    private String execPathName = null;
    private String pendWorkBasket = null;
    private String lastPendWorkBasket = null;
    private String lastPendStep = null;
    private ErrorHandler pendErrorTuple = new ErrorHandler(); // only valid for pend event
    private boolean isPendAtSameStep = false;
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
