package com.anode.workflow.entities.workflows;

import com.anode.tool.StringUtils;
import com.anode.workflow.entities.steps.Step;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.workflows.paths.ExecPath;
import com.anode.workflow.service.ErrorHandler;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
@lombok.Setter
@Entity
public class WorkflowInfo implements Serializable {

    @Id private Serializable hibid;

    @Column private final String caseId;

    @ManyToOne private final WorkflowDefinition workflowDefinition;

    @Transient private ReentrantLock lock = new ReentrantLock(true);

    @Column private Boolean isComplete = false;

    // Shared variables that will be updated by threads
    @ElementCollection
    @CollectionTable(name = "info_variables", joinColumns = @JoinColumn(name = "info_hibid"))
    @MapKeyColumn(name = "variable_key")
    @Column(name = "variable_value")
    private Map<String, WorkflowVariable> variablesMap = new ConcurrentHashMap<>();

    // Ticket raised
    @Column private volatile String ticket = "";

    @Column
    // Whether the component that raised the ticket returned an OK_PEND or not
    private volatile StepResponseType ticketUrt = StepResponseType.OK_PROCEED;

    @Column
    // Pend exec path
    private volatile String pendExecPath = "";

    // Variables which are thread specific
    @ElementCollection
    @CollectionTable(name = "info_exec_path", joinColumns = @JoinColumn(name = "info_hibid"))
    @MapKeyColumn(name = "exec_path_key")
    @Column(name = "exec_path_value")
    private Map<String, ExecPath> execPaths = Collections.synchronizedSortedMap(new TreeMap<>());

    // This variable is populated while writing and is for troubleshooting and information only
    // This variable is not used while reading
    @Column private volatile Step lastUnitExecuted = null;

    @Column public volatile boolean isPendAtSameStep = false;

    @Transient private Setter setter = null;

    public WorkflowInfo(String caseId, WorkflowDefinition workflowDefinition) {
        this.caseId = caseId;
        this.workflowDefinition = workflowDefinition;
        setter = new Setter();
    }

    private void setLastUnitExecuted(Step lastUnitExecuted) {
        this.lastUnitExecuted = lastUnitExecuted;
    }

    public Step getLastUnitExecuted() {
        return lastUnitExecuted;
    }

    public StepResponseType getTicketUrt() {
        return ticketUrt;
    }

    private void setTicketUrt(StepResponseType ticketUrt) {
        this.ticketUrt = ticketUrt;
    }

    public Lock getLock() {
        return lock;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setPendExecPath(String pendExecPath) {
        // go ahead if we are trying to clear
        if (pendExecPath.isEmpty() == true) {
            this.pendExecPath = pendExecPath;
        } else {
            // if the pend is not on "." execution path then
            // we need to set the pend exec path to the one that is deepest in the hierarchy
            // we need to do this so that the unravelling can take place correctly
            // we determine this by counting the number of "."

            // however if the pend happened in the "." execution path, then it means that we have
            // moved ahead of parallel processing in the branches (due to a ticket) and in this case
            // the pends in the previous branches does not matter

            if (pendExecPath.equals(".")) {
                this.pendExecPath = pendExecPath;
            } else {
                int oldDepth = StringUtils.getCount(this.pendExecPath, '.');
                int newDepth = StringUtils.getCount(pendExecPath, '.');

                if (newDepth > oldDepth) {
                    this.pendExecPath = pendExecPath;
                } else {
                    // do nothing
                }
            }
        }
    }

    public String getPendExecPath() {
        return pendExecPath;
    }

    private void setTicket(String ticket) {
        if (ticket.isEmpty() == true) {
            this.ticket = ticket;
        } else {
            // set only if it is already empty
            if (this.ticket.isEmpty() == true) {
                this.ticket = ticket;
            }
        }
    }

    public String getTicket() {
        return ticket;
    }

    protected void removeExecPath(String name) {
        execPaths.remove(name);
    }

    public void setWorkflowVariable(WorkflowVariable variable) {
        WorkflowVariable newVariable =
                new WorkflowVariable(variable.getName(), variable.getType(), variable.getValue());
        variablesMap.put(newVariable.getName(), newVariable);
    }

    public WorkflowVariables getWorkflowVariables() {
        return new WorkflowVariables(variablesMap);
    }

    public ExecPath getExecPath(String name) {
        return execPaths.get(name);
    }

    public List<ExecPath> getExecPaths() {
        synchronized (execPaths) {
            return execPaths.values().stream().collect(Collectors.toList());
        }
    }

    public void setExecPath(ExecPath ep) {
        execPaths.put(ep.getName(), ep);
    }

    public void clearExecPaths() {
        execPaths.clear();
    }

    public String getPendWorkBasket() {
        return execPaths.get(pendExecPath).getPendWorkBasket();
    }

    protected ErrorHandler getPendErrorHandler() {
        return execPaths.get(pendExecPath).getPendError();
    }

    public boolean isCaseStarted() {
        if (execPaths.size() == 0) {
            return false;
        } else {
            return true;
        }
    }

    public boolean isCaseCompleted() {
        return isComplete;
    }

    public void setCaseCompleted() {
        this.isComplete = true;
    }

    public void setCaseCompleted(boolean isComplete) {
        this.isComplete = isComplete;
    }

    public void clearPendWorkBaskets() {
        synchronized (execPaths) {
            List<ExecPath> paths = new ArrayList<>(execPaths.values());
            for (ExecPath path : paths) {
                path.setPendWorkBasket("");
            }
        }
    }

    public Setter getSetter() {
        return this.setter;
    }

    public class Setter {

        public Setter setTicket(String ticket) {
            WorkflowInfo.this.setTicket(ticket);
            if (ticket.isEmpty() == true) {
                WorkflowInfo.this.setTicketUrt(StepResponseType.OK_PROCEED);
            }
            return this;
        }

        public Setter setTicketUrt(StepResponseType ticketUrt) {
            WorkflowInfo.this.setTicketUrt(ticketUrt);
            return this;
        }

        public Setter setPendExecPath(String pendExecPath) {
            WorkflowInfo.this.setPendExecPath(pendExecPath);
            return this;
        }

        public Setter setLastStepExecuted(Step unit) {
            WorkflowInfo.this.setLastUnitExecuted(unit);
            return this;
        }
    }
}
