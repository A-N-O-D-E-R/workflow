package com.anode.workflow.entities.workflows;

import com.anode.workflow.entities.steps.Step;
import com.anode.workflow.entities.tickets.Ticket;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "workflow_definition")
public class WorkflowDefinition implements Serializable {
    @Id private Long hibid;

    @Column(name = "name", nullable = false)
    private String name = null;

    @ElementCollection
    @CollectionTable(name = "workflow_tickets", joinColumns = @JoinColumn(name = "workflow_hibid"))
    @MapKeyColumn(name = "ticket_key")
    @Column(name = "ticket_value")
    private Map<String, Ticket> tickets = new HashMap<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "workflow_id")
    private List<WorkflowVariable> workflowVariables = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "workflow_steps", joinColumns = @JoinColumn(name = "workflow_hibid"))
    @MapKeyColumn(name = "step_key")
    @Column(name = "step_value")
    private Map<String, Step> steps = null;

    public WorkflowDefinition() {
        this.steps = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addStep(Step step) {
        steps.put(step.getName(), step);
    }

    public Step getStep(String name) {
        return steps.get(name);
    }

    public Ticket getTicket(String name) {
        return tickets.get(name);
    }

    public void setTicket(Ticket ticket) {
        tickets.put(ticket.getName(), ticket);
    }

    public void setWorkflowVariables(List<WorkflowVariable> workflowVariables) {
        this.workflowVariables = workflowVariables;
    }

    public List<WorkflowVariable> getWorkflowVariables() {
        return workflowVariables;
    }
}
