package com.anode.workflow.service.runtime;

import com.anode.workflow.WorkflowService;
import com.anode.workflow.entities.events.EventType;
import com.anode.workflow.entities.steps.Branch;
import com.anode.workflow.entities.steps.InvokableRoute;
import com.anode.workflow.entities.steps.InvokableTask;
import com.anode.workflow.entities.steps.Join;
import com.anode.workflow.entities.steps.Pause;
import com.anode.workflow.entities.steps.Persist;
import com.anode.workflow.entities.steps.Route;
import com.anode.workflow.entities.steps.Step;
import com.anode.workflow.entities.steps.Step.StepType;
import com.anode.workflow.entities.steps.Task;
import com.anode.workflow.entities.steps.responses.RouteResponse;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.steps.responses.TaskResponse;
import com.anode.workflow.entities.tickets.Ticket;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.entities.workflows.WorkflowInfo;
import com.anode.workflow.entities.workflows.paths.ExecPath;
import com.anode.workflow.entities.workflows.paths.ExecPath.ExecPathStatus;
import com.anode.workflow.exceptions.WorkflowRuntimeException;
import com.anode.workflow.service.AuditLogService;
import com.anode.workflow.service.WorkflowComponantFactory;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExecThreadTask implements Runnable {

    // these variables are shared across threads
    private RuntimeService rts = null;
    private WorkflowDefinition workflowDefinition = null;
    private WorkflowInfo workflowInfo = null;

    // these variables are thread specific
    private ExecPath execPath = null;
    private boolean isRootThread = false;

    // this variable is used to suppress the writing of the audit log for parallel route thread when
    // it joins on its child threads
    // basically for a parallel route we would have written the audit log before creating the
    // threads and we do not
    // want to write it again after the join or pend condition is reached and the parent thread
    // completes
    private boolean writeAuditLog = true;

    protected ExecThreadTask(RuntimeService rts) {
        this.workflowDefinition = rts.workflowDefinition.get();
        this.workflowInfo = rts.workflowInfo.get();
        this.rts = rts;
    }

    @Override
    public void run() {
        execute();
    }

    // return not null if the process was run and null otherwise
    protected WorkflowContext execute() {
        if (onStart() == false) {
            return null;
        }

        // start the recursive play of threads from here
        String next = null;
        Step step = workflowDefinition.getStep(execPath.getStep());
        WorkflowContext workflowContext = null;

        outer:
        while (true) {
            if (step == null) {
                next = "end";
            } else {
                switch (step.getType()) {
                    case TASK:
                        {
                            next = processStep((Task) step);

                            if (next == null) {
                                break outer;
                            }
                            break;
                        }

                    case P_ROUTE:
                    case P_ROUTE_DYNAMIC:
                        {
                            next = processParallelRoute((Route) step);

                            try {
                                workflowInfo.getLock().lock();

                                if (next == null) {
                                    if (isRootThread == true) {
                                        String ticketName = workflowInfo.getTicket();
                                        if (ticketName.isEmpty() == false) {
                                            Ticket ticket =
                                                    workflowDefinition.getTicket(ticketName);
                                            if (ticket == null) {
                                                throw new WorkflowRuntimeException(
                                                        "Ticket not found: "
                                                                + ticketName
                                                                + " for case: "
                                                                + workflowInfo.getCaseId());
                                            }
                                            if (workflowInfo.getTicketUrt()
                                                    == StepResponseType.OK_PROCEED) {
                                                if (execPath.getName().equals(".")) {
                                                    // raise event
                                                    workflowContext =
                                                            WorkflowContext.forEvent(
                                                                    EventType.ON_TICKET_RAISED,
                                                                    rts,
                                                                    execPath.getName());
                                                    rts.invokeEventHandler(
                                                            EventType.ON_TICKET_RAISED,
                                                            workflowContext);

                                                    // we set next, clear out ticket and proceed
                                                    next = ticket.getStep();
                                                    workflowInfo.getSetter().setTicket("");
                                                } else {
                                                    // mark current execution path as completed
                                                    // become the "." execution path and continue
                                                    log.info(
                                                            "Case id -> "
                                                                    + workflowInfo.getCaseId()
                                                                    + ", child thread going to"
                                                                    + " assume parent role,"
                                                                    + " execution path -> "
                                                                    + execPath.getName());

                                                    // raise event
                                                    workflowContext =
                                                            WorkflowContext.forEvent(
                                                                    EventType.ON_TICKET_RAISED,
                                                                    rts,
                                                                    execPath.getName());
                                                    rts.invokeEventHandler(
                                                            EventType.ON_TICKET_RAISED,
                                                            workflowContext);

                                                    execPath.set(
                                                            ExecPathStatus.COMPLETED,
                                                            step.getName(),
                                                            StepResponseType.OK_PROCEED);
                                                    ExecPath ep = new ExecPath(".");
                                                    ep.set(
                                                            ticket.getStep(),
                                                            StepResponseType.OK_PROCEED);
                                                    execPath = ep;
                                                    workflowInfo.setExecPath(ep);
                                                    next = ticket.getStep();
                                                    workflowInfo.getSetter().setTicket("");
                                                }
                                            } else {
                                                // the ticket is asking us to pend
                                                ExecPath tep = getTicketRaisingExecPath();
                                                ExecPath ep = new ExecPath(".");
                                                ep.setPendWorkBasket(tep.getPendWorkBasket());
                                                ep.setPrevPendWorkBasket(
                                                        tep.getPrevPendWorkBasket());
                                                ep.setTbcSlaWorkBasket(tep.getTbcSlaWorkBasket());
                                                ep.set(
                                                        ExecPathStatus.COMPLETED,
                                                        tep.getStep(),
                                                        workflowInfo.getTicketUrt());

                                                workflowInfo.clearPendWorkBaskets();
                                                workflowInfo.setExecPath(ep);
                                                workflowInfo.getSetter().setPendExecPath(".");
                                                execPath = ep;
                                            }

                                            if (next == null) {
                                                break outer;
                                            } else {
                                                break;
                                            }
                                        }
                                    }
                                    break outer;
                                }
                            } finally {
                                workflowInfo.getLock().unlock();
                            }

                            break;
                        }

                    case S_ROUTE:
                        {
                            next = processSingularRoute((Route) step);
                            if (next == null) {
                                break outer;
                            }
                            break;
                        }

                    case PAUSE:
                        {
                            processPause((Pause) step);
                            next = null;
                            break outer;
                        }

                    case PERSIST:
                        {
                            next = processPersist((Persist) step);
                            if (next == null) {
                                break outer;
                            }
                            break;
                        }

                    case P_JOIN:
                        {
                            next = processJoin((Join) step);
                            if (next == null) {
                                break outer;
                            }
                            break;
                        }
                }
            }

            if (next.equalsIgnoreCase("end") == false) {
                writeProcessInfoAndAuditLog(
                        workflowInfo,
                        step,
                        WorkflowService.instance().isWriteProcessInfoAfterEachStep());
            } else {
                execPath.set(
                        ExecPathStatus.COMPLETED, execPath.getStep(), StepResponseType.OK_PROCEED);

                try {
                    workflowInfo.getLock().lock();
                    workflowInfo.getSetter().setPendExecPath("");
                    workflowInfo.setCaseCompleted();
                } finally {
                    workflowInfo.getLock().unlock();
                }

                break outer;
            }

            step = workflowDefinition.getStep(next);
        }

        if (isRootThread == true) {
            if (next == null) {
                if (workflowInfo.getTicket().isEmpty() == false) {
                    workflowContext =
                            WorkflowContext.forEvent(
                                    EventType.ON_TICKET_RAISED, rts, execPath.getName());
                    rts.invokeEventHandler(EventType.ON_TICKET_RAISED, workflowContext);
                }

                workflowContext =
                        WorkflowContext.forEvent(
                                EventType.ON_PROCESS_PEND, rts, execPath.getName());
                rts.invokeEventHandler(EventType.ON_PROCESS_PEND, workflowContext);
            } else {
                if (next.equalsIgnoreCase("end")) {
                    workflowContext =
                            WorkflowContext.forEvent(
                                    EventType.ON_PROCESS_COMPLETE, rts, execPath.getName());
                    rts.invokeEventHandler(EventType.ON_PROCESS_COMPLETE, workflowContext);
                } else {
                    // this will not happen
                }
            }
            writeProcessInfoAndAuditLog(workflowInfo, step, true);
        } else {
            writeProcessInfoAndAuditLog(workflowInfo, step, true);
        }

        return workflowContext;
    }

    private ExecPath getTicketRaisingExecPath() {
        List<ExecPath> paths = workflowInfo.getExecPaths();
        ExecPath ep = null;
        for (ExecPath path : paths) {
            String ticket = path.getTicket();
            if (ticket.isEmpty() == false) {
                ep = path;
                break;
            }
        }
        return ep;
    }

    // return true if we need to proceed with running the process else false
    private boolean onStart() {
        boolean start = true;

        while (true) {
            if (execPath != null) {
                // a thread has invoked a child. Nothing to do in the case but just move ahead
                break;
            }

            // this means that we are starting up from a call to resume case
            isRootThread = true;
            execPath = getStartContext(workflowDefinition, workflowInfo);
            if (execPath == null) {
                start = false;
                break;
            }

            // clear out the pend and ticket information as we are now about to run
            workflowInfo.getSetter().setPendExecPath("").setTicket("");
            break;
        }

        return start;
    }

    // this function is only called when we are starting by reading the contents of the process info
    // file
    private ExecPath getStartContext(
            WorkflowDefinition workflowDefinition, WorkflowInfo workflowInfo) {
        ExecPath ep = null;

        while (true) {
            // check if we have never ever started
            if (workflowInfo.isCaseStarted() == false) {
                ep = new ExecPath(".");
                ep.set(ExecPathStatus.STARTED, "start", null);
                workflowInfo.setExecPath(ep);
                break;
            }

            {
                // we are in a pended state. We first check if a ticket is set
                if (workflowInfo.getTicket().isEmpty() == false) {
                    Ticket ticket = workflowDefinition.getTicket(workflowInfo.getTicket());

                    // clear out process information
                    workflowInfo.getSetter().setPendExecPath("").setTicket("");
                    workflowInfo.clearExecPaths();

                    // assign just one exec path to start with
                    ep = new ExecPath(".");
                    ep.set(ExecPathStatus.STARTED, ticket.getStep(), null);
                    workflowInfo.setExecPath(ep);
                    break;
                }
            }

            {
                // Check if we are pended in a pause state
                // if we are, set the next task of exec path to the one pointed to by pause
                ep = workflowInfo.getExecPath(workflowInfo.getPendExecPath());
                if (ep == null) {
                    throw new WorkflowRuntimeException(
                            "Execution path not found: "
                                    + workflowInfo.getPendExecPath()
                                    + " for case: "
                                    + workflowInfo.getCaseId());
                }
                Step c = workflowDefinition.getStep(ep.getStep());
                if (c == null) {
                    throw new WorkflowRuntimeException(
                            "Step not found: "
                                    + ep.getStep()
                                    + " for case: "
                                    + workflowInfo.getCaseId());
                }
                if (c.getType() == StepType.PAUSE) {
                    Pause p = (Pause) c;
                    ep.set(ExecPathStatus.STARTED, p.getNext(), null);
                    break;
                }
            }

            // we are pended at a task or a route
            {
                ep = workflowInfo.getExecPath(workflowInfo.getPendExecPath());
                StepResponseType urt = ep.getStepResponseType();
                switch (urt) {
                    case OK_PEND:
                        {
                            // we are at a task and we need to start from the next task
                            Task pendStep = (Task) workflowDefinition.getStep(ep.getStep());
                            ep.set(ExecPathStatus.STARTED, pendStep.getNext(), null);
                            ep.setPendWorkBasket("");
                            break;
                        }

                    case OK_PEND_EOR:
                        {
                            // we may be at a task or a route
                            ep.set(ExecPathStatus.STARTED, ep.getStep(), null);
                            ep.setPendWorkBasket("");
                            break;
                        }

                    case OK_PROCEED:
                        {
                            // we will never face this condition as we would have moved ahead in the
                            // process if
                            // a component was to return this value
                            throw new WorkflowRuntimeException(
                                    "Unexpected condition encountered. Case id -> "
                                            + workflowInfo.getCaseId());
                        }

                    case ERROR_PEND:
                        {
                            // we are pended on a task or route after an error and so we need to
                            // start from there
                            ep.set(ExecPathStatus.STARTED, ep.getStep(), null);
                            ep.setPendWorkBasket("");
                            break;
                        }
                }
                break;
            }
        }

        return ep;
    }

    private String processStep(Task task) {
        String next = null;

        log.info(
                "Case id -> "
                        + workflowInfo.getCaseId()
                        + ", executing task -> "
                        + task.getName()
                        + ", component -> "
                        + task.getComponentName()
                        + ", execution path -> "
                        + execPath.getName());

        TaskResponse resp = executeStep(task);

        try {
            workflowInfo.getLock().lock();

            StepResponseType urt = resp.getUnitResponseType();

            if ((urt == StepResponseType.OK_PROCEED) || (urt == StepResponseType.OK_PEND)) {
                workflowInfo.isPendAtSameStep = false;
            }

            switch (urt) {
                case OK_PEND:
                case OK_PEND_EOR:
                    {
                        log.info(
                                "Case id -> "
                                        + workflowInfo.getCaseId()
                                        + ", pending at task -> "
                                        + task.getName()
                                        + ", component -> "
                                        + task.getComponentName()
                                        + ", execution path -> "
                                        + execPath.getName());

                        execPath.set(ExecPathStatus.COMPLETED, task.getName(), urt);
                        execPath.setPendWorkBasket(resp.getWorkBasket());

                        // we first check if there is already a ticket set
                        if (workflowInfo.getTicket().isEmpty() == false) {
                            // we only need to update the process variables and terminate
                            log.info(
                                    "Case id -> "
                                            + workflowInfo.getCaseId()
                                            + ", abandoning as ticket is already set -> "
                                            + workflowInfo.getTicket()
                                            + ", component -> "
                                            + task.getComponentName()
                                            + ", execution path -> "
                                            + execPath.getName());
                            break;
                        }

                        // now we check if we have raised a ticket
                        String ticketName = resp.getTicket();
                        if (ticketName.isEmpty() == false) {
                            // in this case set the ticket and pend exec path
                            log.info(
                                    "Case id -> "
                                            + workflowInfo.getCaseId()
                                            + ", encountered ticket -> "
                                            + ticketName
                                            + ", component -> "
                                            + task.getComponentName()
                                            + ", execution path -> "
                                            + execPath.getName());
                            execPath.setTicket(ticketName);
                            workflowInfo
                                    .getSetter()
                                    .setTicketUrt(urt)
                                    .setPendExecPath(execPath.getName())
                                    .setTicket(ticketName);
                            break;
                        }

                        // there is no ticket existing or raised. We do normal processing
                        workflowInfo.getSetter().setPendExecPath(execPath.getName()).setTicket("");

                        break;
                    }

                case OK_PROCEED:
                    {
                        execPath.set(task.getName(), urt);

                        // again we first check if there is already a ticket set
                        if (workflowInfo.getTicket().isEmpty() == false) {
                            // we only need to update the process variables and terminate
                            log.info(
                                    "Case id -> "
                                            + workflowInfo.getCaseId()
                                            + ", abandoning as ticket is already set -> "
                                            + workflowInfo.getTicket()
                                            + ", component -> "
                                            + task.getComponentName()
                                            + ", execution path -> "
                                            + execPath.getName());
                            execPath.set(ExecPathStatus.COMPLETED, task.getName(), urt);
                            break;
                        }

                        // check if this call has raised a ticket
                        String ticketName = resp.getTicket();
                        if (ticketName.isEmpty() == false) {
                            log.info(
                                    "Case id -> "
                                            + workflowInfo.getCaseId()
                                            + ", encountered ticket -> "
                                            + ticketName
                                            + ", component -> "
                                            + task.getComponentName()
                                            + ", execution path -> "
                                            + execPath.getName());

                            workflowInfo
                                    .getSetter()
                                    .setTicketUrt(StepResponseType.OK_PROCEED)
                                    .setTicket(resp.getTicket());

                            if (isRootThread == true) {
                                // raise event
                                rts.invokeEventHandler(
                                        EventType.ON_TICKET_RAISED,
                                        WorkflowContext.forEvent(
                                                EventType.ON_TICKET_RAISED,
                                                rts,
                                                execPath.getName()));

                                if (execPath.getName().equals(".")) {
                                    // we only need to set next, clear out ticket and proceed
                                    Ticket ticket = workflowDefinition.getTicket(ticketName);
                                    if (ticket == null) {
                                        throw new WorkflowRuntimeException(
                                                "Ticket not found: "
                                                        + ticketName
                                                        + " for case: "
                                                        + workflowInfo.getCaseId());
                                    }
                                    next = ticket.getStep();
                                    workflowInfo.getSetter().setTicket("");
                                } else {
                                    // mark current execution path as completed
                                    // become the "." execution path and continue
                                    log.info(
                                            "Case id -> "
                                                    + workflowInfo.getCaseId()
                                                    + ", child thread going to assume parent role"
                                                    + " (\".\"), execution path -> "
                                                    + execPath.getName());

                                    execPath.set(ExecPathStatus.COMPLETED, task.getName(), urt);
                                    ExecPath ep = new ExecPath(".");
                                    ep.set(ExecPathStatus.STARTED, task.getName(), urt);
                                    execPath = ep;
                                    workflowInfo.setExecPath(ep);
                                    Ticket ticket = workflowDefinition.getTicket(ticketName);
                                    next = ticket.getStep();
                                    workflowInfo.getSetter().setTicket("");
                                }
                            } else {
                                // we are a child thread and our parent is running and so we need to
                                // set ticket and terminate
                                // and let the parent handle the ticket
                                log.info(
                                        "Case id -> "
                                                + workflowInfo.getCaseId()
                                                + ", child thread exiting, execution path -> "
                                                + execPath.getName());
                                execPath.set(ExecPathStatus.COMPLETED, task.getName(), urt);
                                execPath.setTicket(ticketName);
                                workflowInfo.getSetter().setTicket(ticketName);
                            }
                        } else {
                            // no ticket is raised hence do normal processing
                            next = task.getNext();
                        }

                        break;
                    }

                case ERROR_PEND:
                    {
                        log.info(
                                "Case id -> "
                                        + workflowInfo.getCaseId()
                                        + ", pending at task -> "
                                        + task.getName()
                                        + ", component -> "
                                        + task.getComponentName()
                                        + ", execution path -> "
                                        + execPath.getName());
                        execPath.set(ExecPathStatus.COMPLETED, task.getName(), urt);
                        execPath.setPendWorkBasket(resp.getWorkBasket());
                        execPath.setPendError(resp.getError());
                        workflowInfo.getSetter().setPendExecPath(execPath.getName());
                        break;
                    }
            }
        } finally {
            workflowInfo.getLock().unlock();
        }

        return next;
    }

    private String processPersist(Persist task) {
        String next = null;
        try {
            log.info(
                    "Case id -> "
                            + workflowInfo.getCaseId()
                            + ", executing persist task -> "
                            + task.getName()
                            + ", execution path -> "
                            + execPath.getName());
            workflowInfo.isPendAtSameStep = false;
            rts.invokeEventHandler(
                    EventType.ON_PERSIST,
                    WorkflowContext.forEvent(EventType.ON_PERSIST, rts, execPath.getName()));
            execPath.set(task.getName(), StepResponseType.OK_PROCEED);
            next = task.getNext();
            return next;
        } catch (Exception e) {
            log.info(
                    "Case id -> "
                            + workflowInfo.getCaseId()
                            + ", pending at persist task -> "
                            + task.getName()
                            + ", execution path -> "
                            + execPath.getName());
            execPath.set(task.getName(), StepResponseType.ERROR_PEND);
            execPath.setPendWorkBasket(WorkflowService.instance().getErrorWorkbasket());
            return null;
        }
    }

    private void setChildExecPaths(Route route, List<String> branches) {
        for (int i = 0; i < branches.size(); i++) {
            String branchName = branches.get(i);
            String execPathName = execPath.getName() + route.getName() + "." + branchName + ".";
            ExecPath ep = new ExecPath(execPathName);
            if (route.getNext() != null) {
                ep.set(route.getNext(), null);
            } else {
                ep.set(route.getBranch(branchName).getNext(), null);
            }
            workflowInfo.setExecPath(ep);
        }
    }

    private String processParallelRoute(Route route) {
        String next = null;

        log.info(
                "Case id -> "
                        + workflowInfo.getCaseId()
                        + ", executing parallel routing rule -> "
                        + route.getName()
                        + ", execution path -> "
                        + execPath.getName());

        RouteResponse resp = executeRule(route);

        StepResponseType urt = resp.getUnitResponseType();

        if (urt == StepResponseType.OK_PROCEED) {
            workflowInfo.isPendAtSameStep = false;
            execPath.set(route.getName(), urt);
        } else {
            execPath.set(route.getName(), urt);
            execPath.setPendWorkBasket(resp.getWorkBasket());
            execPath.setPendError(resp.getError());
        }

        setChildExecPaths(route, resp.getBranches());

        try {
            workflowInfo.getLock().lock();

            if (WorkflowService.instance().isWriteProcessInfoAfterEachStep() == true) {
                writeWorkflowInfo(workflowInfo, route);
            }

            writeAuditLog(workflowInfo, route, resp.getBranches());
            writeAuditLog = false;
        } finally {
            workflowInfo.getLock().unlock();
        }

        switch (urt) {
            case OK_PEND:
            case OK_PEND_EOR:
                {
                    // this situation will not arise as a route cannot specify a pend upon
                    // successful execution
                    throw new WorkflowRuntimeException(
                            "Route cannot pend upon successful execution");
                }

            case OK_PROCEED:
                {
                    String joinPoint = executeThreads(execPath, route, resp.getBranches());

                    if (joinPoint != null) {
                        // we have reached the join point and all threads that were supposed to
                        // reach the join point have completed
                        // in this case we are the parent thread and we move ahead in the process
                        if (workflowInfo.getTicket().isEmpty() == false) {
                            if (isRootThread == true) {
                                if (execPath.equals(".")) {
                                    next =
                                            workflowDefinition
                                                    .getTicket(workflowInfo.getTicket())
                                                    .getStep();
                                } else {
                                    try {
                                        workflowInfo.getLock().lock();

                                        // mark current execution path as completed
                                        // become the parent execution path and continue
                                        execPath.set(
                                                ExecPathStatus.COMPLETED, route.getName(), urt);
                                        ExecPath ep =
                                                new ExecPath(execPath.getParentExecPathName());
                                        ep.set(route.getName(), urt);
                                        execPath = ep;
                                        workflowInfo.setExecPath(ep);
                                        Ticket ticket =
                                                workflowDefinition.getTicket(
                                                        workflowInfo.getTicket());
                                        next = ticket.getStep();
                                        workflowInfo.getSetter().setTicket("");
                                    } finally {
                                        workflowInfo.getLock().unlock();
                                    }
                                }
                            } else {
                                // mark myself completed and let the parent handle ticket
                                execPath.set(ExecPathStatus.COMPLETED, route.getName(), urt);
                            }
                        } else {
                            Join join = (Join) workflowDefinition.getStep(joinPoint);
                            next = join.getNext();
                        }

                        break;
                    }

                    // we reach here because we are the main thread and some child thread has pended
                    // in this case we are going to terminate and so set ourselves as completed
                    execPath.set(ExecPathStatus.COMPLETED, route.getName(), urt);
                    break;
                }

            case ERROR_PEND:
                {
                    log.info(
                            "Case id -> "
                                    + workflowInfo.getCaseId()
                                    + ", pending at parallel route -> "
                                    + route.getName()
                                    + ", component -> "
                                    + route.getComponentName()
                                    + ", execution path -> "
                                    + execPath.getName());

                    try {
                        workflowInfo.getLock().lock();
                        workflowInfo.getSetter().setPendExecPath(execPath.getName());
                    } finally {
                        workflowInfo.getLock().unlock();
                    }
                    break;
                }
        }

        return next;
    }

    private String processSingularRoute(Route route) {
        String next = null;

        log.info(
                "Case id -> "
                        + workflowInfo.getCaseId()
                        + ", executing singular routing rule -> "
                        + route.getName()
                        + ", execution path -> "
                        + execPath.getName());

        RouteResponse resp = executeRule(route);

        try {
            workflowInfo.getLock().lock();

            StepResponseType urt = resp.getUnitResponseType();

            if ((urt == StepResponseType.OK_PROCEED) || (urt == StepResponseType.OK_PEND)) {
                workflowInfo.isPendAtSameStep = false;
            }

            writeAuditLog(workflowInfo, route, resp.getBranches());
            writeAuditLog = false;

            switch (urt) {
                case OK_PEND:
                case OK_PEND_EOR:
                    {
                        // this situation will not arise as a route cannot specify a pend upon
                        // successful execution
                        throw new WorkflowRuntimeException("anexdeus_err_8");
                    }

                case OK_PROCEED:
                    {
                        execPath.set(route.getName(), urt);
                        String branchName = resp.getBranches().get(0);
                        next = route.getBranch(branchName).getNext();
                        break;
                    }

                case ERROR_PEND:
                    {
                        log.info(
                                "Case id -> "
                                        + workflowInfo.getCaseId()
                                        + ", pending at route -> "
                                        + route.getName()
                                        + ", component -> "
                                        + route.getComponentName()
                                        + ", execution path -> "
                                        + execPath.getName());
                        execPath.set(route.getName(), urt);
                        execPath.setPendWorkBasket(resp.getWorkBasket());
                        execPath.setPendError(resp.getError());
                        workflowInfo.getSetter().setPendExecPath(execPath.getName());
                        break;
                    }
            }
        } finally {
            workflowInfo.getLock().unlock();
        }

        return next;
    }

    private void processPause(Pause pause) {
        log.info(
                "Case id -> "
                        + workflowInfo.getCaseId()
                        + ", executing pause task -> "
                        + pause.getName()
                        + ", execution path -> "
                        + execPath.getName());
        try {
            workflowInfo.getLock().lock();
            execPath.set(ExecPathStatus.COMPLETED, pause.getName(), StepResponseType.OK_PEND);
            execPath.setPendWorkBasket("workflow_pause");
            workflowInfo.getSetter().setPendExecPath(execPath.getName());
        } finally {
            workflowInfo.getLock().unlock();
        }
    }

    private String processJoin(Join join) {
        String next = null;

        log.info(
                "Case id -> "
                        + workflowInfo.getCaseId()
                        + ", handling join for execution path -> "
                        + execPath.getName());

        try {
            workflowInfo.getLock().lock();

            // mark myself as complete first. This will reflect in workflowInfo
            execPath.set(ExecPathStatus.COMPLETED, join.getName(), StepResponseType.OK_PROCEED);

            // check if all siblings have completed
            // this is used for correctly unravelling the pended process. The other approach
            // could have been to explicitly select the next pended execution path, become that
            // execution path and start from there. May be done in the future
            boolean isComplete = true;
            ExecPath pendedEp = null;
            List<ExecPath> paths = workflowInfo.getExecPaths();
            for (ExecPath path : paths) {
                // ignore self
                if (path.getName().equals(path)) {
                    continue;
                }

                if (execPath.isSibling(path)) {
                    if (path.getPendWorkBasket().isEmpty() == false) {
                        isComplete = false;
                        pendedEp = path;
                        break;
                    }
                }
            }

            if (isComplete) {
                // we need to become parent and continue processing
                ExecPath parentEp = workflowInfo.getExecPath(execPath.getParentExecPathName());
                if (parentEp.getStatus() == ExecPathStatus.COMPLETED) {
                    parentEp.set(
                            ExecPathStatus.STARTED, join.getName(), StepResponseType.OK_PROCEED);
                    execPath = parentEp;
                    next = join.getNext();
                } else {
                    // parent thread is running and will will let that thread take over and so
                    // nothing to do
                }
            } else {
                if (pendedEp != null) {
                    workflowInfo.getSetter().setPendExecPath(pendedEp.getName());
                }
            }
        } finally {
            workflowInfo.getLock().unlock();
        }

        return next;
    }

    private String executeThreads(ExecPath parentExecPath, Route route, List<String> branches) {
        int count = branches.size();
        ExecThreadTask[] tasks = new ExecThreadTask[count];

        for (int i = 0; i < count; i++) {
            String branchName = branches.get(i);
            String next = route.getNext();

            if (next != null) {
                next = route.getNext();
            } else {
                Branch branch = route.getBranch(branchName);
                next = branch.getNext();
            }

            ExecPath ep =
                    new ExecPath(
                            parentExecPath.getName() + route.getName() + "." + branchName + ".");
            ep.setStep(workflowDefinition.getStep(next).getName());
            ExecThreadTask in = new ExecThreadTask(rts);
            in.execPath = ep;
            tasks[i] = in;
            workflowInfo.setExecPath(ep);
        }

        // run threads and wait for them to finish
        runThreads(tasks);

        // we now check if there has been a pend in any of the levels that this thread has called. A
        // pend
        // can not only occur in one of the threads created by this thread but can exist many levels
        // deep
        if (workflowInfo.getPendExecPath().isEmpty() == true) {
            // no pend occurred -> return the join point
            ExecThreadTask in = tasks[0];
            ExecPath ep = in.execPath;
            return ep.getStep();
        } else {
            // a pend occurred somewhere in the hierarchy
            return null;
        }
    }

    private void runThreadsWithExecutorService(
            ExecutorService executorService, ExecThreadTask[] tasks) {
        int count = tasks.length;
        Future<?>[] futures = new Future[count];

        // start threads
        for (int i = 0; i < count; i++) {
            futures[i] = executorService.submit(tasks[i]);
        }

        // wait for them to finish
        for (int i = 0; i < tasks.length; i++) {
            try {
                futures[i].get();
            } catch (InterruptedException | ExecutionException e) {
                // should never happen
                throw new WorkflowRuntimeException(
                        "Unexpected condition encountered. Case id -> " + workflowInfo.getCaseId(),
                        e);
            }
        }
    }

    private void runThreadsAsChildren(ExecThreadTask[] tasks) {
        Thread[] threads = new Thread[tasks.length];

        // start threads
        for (int i = 0; i < tasks.length; i++) {
            threads[i] = new Thread(tasks[i]);
            threads[i].start();
        }

        // wait for them to finish
        for (int i = 0; i < tasks.length; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                // should never happen
                throw new WorkflowRuntimeException(
                        "Unexpected condition encountered. Case id -> " + workflowInfo.getCaseId(),
                        e);
            }
        }
    }

    private void runThreads(ExecThreadTask[] tasks) {
        ExecutorService executorService = WorkflowService.instance().getExecutorService();
        if (executorService == null) {
            runThreadsAsChildren(tasks);
        } else {
            runThreadsWithExecutorService(executorService, tasks);
        }
    }

    private RouteResponse executeRule(Route route) {
        RouteResponse rr = null;
        WorkflowContext workflowContext = null;

        try {
            WorkflowComponantFactory factory = rts.factory;
            workflowContext =
                    new WorkflowContext(
                            workflowDefinition.getName(),
                            workflowInfo.getCaseId(),
                            route.getName(),
                            route.getComponentName(),
                            route.getUserData(),
                            route.getType(),
                            workflowInfo.getWorkflowVariables(),
                            execPath.getName());
            InvokableRoute rule = (InvokableRoute) factory.getObject(workflowContext);
            rr = rule.executeRoute();
        } catch (Exception e) {
            log.error(
                    "Exception encountered while executing rule. Case id -> {}, step_name -> {},"
                            + " comp_name -> {}",
                    workflowInfo.getCaseId(),
                    workflowContext.getStepName(),
                    workflowContext.getCompName());
            log.error("Exception details -> {}", e.getMessage());
            rr =
                    new RouteResponse(
                            StepResponseType.ERROR_PEND,
                            null,
                            WorkflowService.instance().getErrorWorkbasket());
        }

        return rr;
    }

    private TaskResponse executeStep(Task task) {
        TaskResponse sr = null;
        WorkflowContext workflowContext = null;

        try {
            WorkflowComponantFactory factory = rts.factory;
            workflowContext =
                    new WorkflowContext(
                            workflowDefinition.getName(),
                            workflowInfo.getCaseId(),
                            task.getName(),
                            task.getComponentName(),
                            task.getUserData(),
                            StepType.TASK,
                            workflowInfo.getWorkflowVariables(),
                            execPath.getName(),
                            rts.lastPendWorkBasket,
                            rts.lastPendStep,
                            workflowInfo.isPendAtSameStep);
            InvokableTask iStep = (InvokableTask) factory.getObject(workflowContext);
            sr = iStep.executeStep();
        } catch (Exception e) {
            log.error(
                    "Exception encountered while executing task. Case id -> {}, step_name -> {},"
                            + " comp_name -> {}",
                    workflowInfo.getCaseId(),
                    workflowContext.getStepName(),
                    workflowContext.getCompName());
            log.error("Exception details -> {}", e.getMessage());
            sr =
                    new TaskResponse(
                            StepResponseType.ERROR_PEND,
                            null,
                            WorkflowService.instance().getErrorWorkbasket());
        }

        return sr;
    }

    private void writeProcessInfoAndAuditLog(
            WorkflowInfo workflowInfo, Step lastUnit, boolean writeProcessInfo) {
        try {
            workflowInfo.getLock().lock();

            if (writeProcessInfo == true) {
                writeWorkflowInfo(workflowInfo, lastUnit);
            }

            writeAuditLog(workflowInfo, lastUnit, null);
        } finally {
            workflowInfo.getLock().unlock();
        }
    }

    private void writeWorkflowInfo(WorkflowInfo workflowInfo, Step lastUnit) {
        workflowInfo.getSetter().setLastStepExecuted(lastUnit);
        rts.dao.saveOrUpdate(
                RuntimeService.WORKFLOW_INFO + RuntimeService.SEP + workflowInfo.getCaseId(),
                workflowInfo);
    }

    private void writeAuditLog(WorkflowInfo workflowInfo, Step lastUnit, List<String> branches) {
        if (WorkflowService.instance().isWriteAuditLog() == false) {
            return;
        }

        if (writeAuditLog == false) {
            writeAuditLog = true;
            return;
        }

        if (lastUnit == null) {
            AuditLogService.writeAuditLog(rts.dao, workflowInfo, null, branches, "end");
        } else {
            AuditLogService.writeAuditLog(
                    rts.dao, workflowInfo, lastUnit, branches, lastUnit.getName());
        }
    }
}
