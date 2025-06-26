package com.anode.workflow.service;

import com.anode.tool.StringUtils;
import com.anode.workflow.CommonDao;
import com.anode.workflow.entities.steps.Step;
import com.anode.workflow.entities.steps.Step.StepType;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.entities.workflows.WorkflowInfo;
import com.anode.workflow.entities.workflows.WorkflowVariable;
import com.anode.workflow.entities.workflows.paths.ExecPath;
import com.anode.workflow.entities.workflows.paths.ExecPath.ExecPathStatus;
import com.anode.workflow.exceptions.WorkflowRuntimeException;
import com.anode.workflow.service.runtime.RuntimeService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowInfoUtils {
    public static WorkflowInfo getWorkflowInfo(
            CommonDao dao, String caseId, WorkflowDefinition pd) {
        WorkflowInfo pi = new WorkflowInfo(caseId, pd);

        WorkflowInfo newWorkflowInfo =
                dao.get(
                        WorkflowInfo.class,
                        RuntimeService.WORKFLOW_INFO + RuntimeService.SEP + caseId);

        if (newWorkflowInfo == null) {
            for (WorkflowVariable pv : pd.getWorkflowVariables()) {
                pi.setWorkflowVariable(pv);
            }
        } else {

            sanitize(newWorkflowInfo, caseId, pd);

            for (WorkflowVariable pv :
                    newWorkflowInfo.getWorkflowVariables().getListOfWorkflowVariable()) {
                pi.setWorkflowVariable(pv);
            }

            newWorkflowInfo.getExecPaths().forEach(ep -> pi.setExecPath(ep));

            String ticket = newWorkflowInfo.getTicket();
            if (ticket == null) {
                ticket = "";
            }

            pi.getSetter().setTicket(ticket);
            String pendingExecPath = newWorkflowInfo.getPendExecPath();
            if (pendingExecPath != null) {
                pi.getSetter().setPendExecPath(pendingExecPath);
            }

            // set is complete
            Boolean isComplete = newWorkflowInfo.getIsComplete();
            if (isComplete != null) {
                if (isComplete == true) {
                    pi.setCaseCompleted();
                }
            }
        }
        return pi;
    }

    private static void setIsComplete(String caseId, WorkflowInfo pid) {
        Boolean isComplete = pid.getIsComplete();
        if (isComplete == null) {
            boolean allPathsComplete = true;

            for (ExecPath execPath : pid.getExecPaths()) {
                ExecPathStatus status = execPath.getStatus();
                String pendingWorkBasket = execPath.getPendWorkBasket();

                if (status.equals(ExecPathStatus.STARTED) || !pendingWorkBasket.isEmpty()) {
                    allPathsComplete = false;
                    break;
                }
            }
            isComplete = allPathsComplete;
            pid.setIsComplete(isComplete);
            log.info(
                    "Case id -> {}, setting $.process_info.is_complete to -> {}",
                    caseId,
                    isComplete);
        }
    }

    private static String getShortestExecPath(WorkflowInfo pid) {
        String sep = null;
        int snum = 0;
        int size = pid.getExecPaths().size();
        for (int i = 0; i < size; i++) {
            String ep = pid.getExecPaths().get(i).getName();
            if (sep == null) {
                sep = ep;
                snum = StringUtils.getCount(ep, '.');
            } else {
                int num = StringUtils.getCount(ep, '.');
                if (num < snum) {
                    snum = num;
                    sep = ep;
                }
            }
        }
        return sep;
    }

    private static boolean checkAndSetTicketInExecPath(String caseId, WorkflowInfo pid) {
        boolean isTicketRaised = false;

        String ticket = pid.getTicket();
        if ((ticket != null) && (ticket.isEmpty() == false)) {
            // a ticket was set and the step pended
            String epName = pid.getPendExecPath();
            if (epName.isEmpty() == true) {
                // arbitrarily select the shortest exec path just so that we can resume
                epName = getShortestExecPath(pid);
                pid.setPendExecPath(epName);
                pid.setExecPath(null);
                pid.getExecPath(epName).setPendWorkBasket("workflow_temp_hold");
                pid.getExecPath(epName).setStepResponseType(StepResponseType.OK_PEND);
                log.info("Case id -> {}, setting info for execution path -> {}", caseId, epName);
            }

            // set ticket as blank in all exec paths
            int size = pid.getExecPaths().size();
            for (int i = 0; i < size; i++) {
                pid.getExecPaths().get(i).getTicket();
            }

            // set the ticket field in pended exec path
            String s = pid.getExecPath(epName).getName();
            if (s != null) {
                pid.getExecPath(epName).setTicket(ticket);
            }
            isTicketRaised = true;
        }
        return isTicketRaised;
    }

    private static void checkExecPathCompletion(
            WorkflowInfo pid, String caseId, WorkflowDefinition pd) {
        int size = pid.getExecPaths().size();

        for (int i = 0; i < size; i++) {
            // get status
            String epName = pid.getExecPaths().get(i).getName();
            ExecPathStatus epStatus = pid.getExecPaths().get(i).getStatus();

            if (epStatus == ExecPathStatus.STARTED) {
                // we have an exec path that could not go to completion. Set status and wb
                pid.getExecPaths().get(i).setStatus(epStatus);
                log.info(
                        "Case id -> {}, setting status to complete for execution path -> {}",
                        caseId,
                        epName);

                String prevWb = pid.getExecPaths().get(i).getPrevPendWorkBasket();
                String wb = null;
                if ((prevWb == null) || (prevWb.isEmpty() == true)) {
                    wb = "workflow_temp_hold";
                } else {
                    wb = prevWb;
                }
                pid.getExecPaths().get(i).setPendWorkBasket(wb);
                log.info(
                        "Case id -> {}, setting pend work basket to -> {} for execution path -> {}",
                        caseId,
                        wb,
                        epName);

                StepResponseType urt = pid.getExecPaths().get(i).getStepResponseType();
                if (urt == null) {
                    // urt is null and so we could not start on this unit. Set urt to ok_pend_eor so
                    // that we can execute again
                    pid.getExecPaths().get(i).setStepResponseType(StepResponseType.OK_PEND_EOR);
                    log.info(
                            "Case id -> {}, exec path -> {}, found urt as null, replacing with"
                                    + " ok_pend_eor",
                            caseId,
                            epName);
                } else {
                    // urt has a value
                    Step unit = pd.getStep(pid.getExecPaths().get(i).getStep());
                    if ((unit.getType() == StepType.P_ROUTE)
                            || (unit.getType() == StepType.P_ROUTE_DYNAMIC)) {
                        // urt can be ok_proceed or error_pend. In case of error pend we can let it
                        // remain as it is and it will
                        // be picked up automatically
                        // in case of ok_proceed, we do nothing. This will not cover the scenario
                        // that the child processes all reached join and completed
                        // but before the parent thread could join on child threads the crash
                        // happened. Since practically
                        // it is not possible to take care of every situation, we will live with
                        // this risk hoping that one of the child
                        // process would not have completed in which case we should be OK
                        log.info(
                                "Case id -> {}, encountered p_route or p_route_dynamic. Doing"
                                        + " nothing. Execution path -> {}",
                                caseId,
                                epName);
                    } else if (unit.getType() == StepType.S_ROUTE) {
                        // urt can be ok_proceed or error_pend. If ok_proceed we need to replace
                        // with ok_pend_eor
                        // as we need the rule to evaluate once again to decide where to go. For
                        // error pend we can
                        // leave it as it is
                        if (urt.equals(StepResponseType.OK_PROCEED.toString().toLowerCase())
                                == true) {
                            pid.getExecPaths()
                                    .get(i)
                                    .setStepResponseType(StepResponseType.OK_PEND_EOR);
                            log.info(
                                    "Case id -> {}, encountered s_route and urt as ok_proceed."
                                            + " Setting urt to ok_pend_eor. Execution path -> {}",
                                    caseId,
                                    epName);
                        } else {
                            // nothing to do
                            log.info(
                                    "Case id -> {}, encountered s_route and urt as not ok_proceed."
                                            + " Doing nothing. Execution path -> {}",
                                    caseId,
                                    epName);
                        }
                    } else {
                        // we are at a step
                        // urt can be ok_proceed, ok_pend_eor, ok_pend or error_pend
                        // replace ok_proceed with ok_pend. Rest can ramin as they are
                        if (urt.equals(StepResponseType.OK_PROCEED.toString().toLowerCase())
                                == true) {
                            pid.getExecPaths().get(i).setStepResponseType(StepResponseType.OK_PEND);
                            log.info(
                                    "Case id -> {}, exec path -> {}, found step with urt as"
                                            + " ok_proceed, replacing with ok_pend",
                                    caseId,
                                    epName);
                        } else {
                            // nothing to do
                            log.info(
                                    "Case id -> {}, encountered step and urt as not ok_proceed."
                                            + " Doing nothing. Execution path -> {}",
                                    caseId,
                                    epName);
                        }
                    }
                }
            }
        }
    }

    private static void setPendExecPath(WorkflowInfo pid, String caseId) {
        boolean isComplete = pid.getIsComplete();
        if (isComplete == false) {
            String pendExecPath = pid.getPendExecPath();
            if (pendExecPath == null) {
                pendExecPath = "";
            }

            if (pendExecPath.isEmpty() == true) {
                int size = pid.getExecPaths().size();
                int oldLevel = 0;
                for (int i = 0; i < size; i++) {
                    // pend to the deepest exec path
                    String epName = pid.getExecPaths().get(i).getName();
                    String wb = pid.getExecPaths().get(i).getPendWorkBasket();

                    if ((wb != null) && (wb.isEmpty() == false)) {
                        int newLevel = StringUtils.getCount(epName, '.');
                        if (newLevel > oldLevel) {
                            pendExecPath = epName;
                            oldLevel = newLevel;
                        }
                    }
                }

                if (pendExecPath.isEmpty() == false) {
                    pid.setPendExecPath(pendExecPath);
                    log.info("Case id -> {}, setting pend exec path -> {}", caseId, pendExecPath);
                } else {
                    log.info("Case id -> {}, could not find a exec path to pend", caseId);
                    throw new WorkflowRuntimeException(
                            "Case id "
                                    + caseId
                                    + " -> could not find an exec path to pend to. This application"
                                    + " cannot be repaired");
                }
            }
        }
    }

    private static void sanitize(WorkflowInfo pid, String caseId, WorkflowDefinition pd) {
        // check for existence of field is_complete
        // if not there then we are dealing with a file created by the previous version of workflow
        // in this case examine all exec paths and if all say completed without a pend
        // then we assume case is complete

        // also check for existence of field ticket in each exec path. If not found then we are
        // dealing with an application created by a previous version of workflow
        // we need to do this only if the ticket is set else the new version will self correct
        // in this case select the pend exec path and set the ticket field there

        // this function will fix the data in the process info file to handle for possible orphaned
        // applications
        // orphaned applications can result when the jvm crashes. In case of a jvm crash we will
        // attempt to
        // recover from the last known state of the process. At worst, one step / route (per
        // execution path) which could not log itself
        // into process info file will get executed once again and that is unavoidable from an
        // orchestration perspective

        // To handle a step / route getting executed once again in case of a crash, the application
        // services
        // which are invoked may need to take some extra care of being idempotent so as to avoid
        // unwanted side affects

        // the logic is like this
        // First identify if we are dealing with an orphaned case. The logic for doing this is to
        // traverse through all exec paths.

        // If the status is started then it means that the execution path did not get a chance to
        // complete

        // For all such execution paths, set the status to complete
        // Further for those which last executed a step, set the urt to ok pend and the workbasket.
        // For others set it to ok proceed
        // Lastly set the deepest level as pend exec path (only if ticket is not outstanding)

        // this logic should work for both single and multithreaded use cases

        setIsComplete(caseId, pid);

        boolean isTicket = checkAndSetTicketInExecPath(caseId, pid);
        if (isTicket == false) {
            checkExecPathCompletion(pid, caseId, pd);
        }

        setPendExecPath(pid, caseId);
    }
}
