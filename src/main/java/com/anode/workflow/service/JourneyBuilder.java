package com.anode.workflow.service;

import com.anode.workflow.entities.steps.Step;
import com.anode.workflow.entities.tickets.Ticket;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.entities.workflows.WorkflowVariable;
import com.anode.workflow.entities.workflows.WorkflowVariable.WorkflowVariableType;
import com.anode.workflow.exceptions.WorkflowVariableTypeParseException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class JourneyBuilder {
    private String name;
    private List<Ticket> tickets = new ArrayList<>();
    private List<WorkflowVariable> workflowVariables = new ArrayList<>();
    private List<Step> steps = new ArrayList<>();

    public JourneyBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public JourneyBuilder addTicket(String name, String step) {
        this.tickets.add(new Ticket(name, step));
        return this;
    }

    public JourneyBuilder addVariable(Object value, String name, String comment) {
        if (value instanceof Integer) {
            this.workflowVariables.add(
                    new WorkflowVariable(name, WorkflowVariableType.INTEGER, value));
        } else if (value instanceof Boolean) {
            this.workflowVariables.add(
                    new WorkflowVariable(name, WorkflowVariableType.BOOLEAN, value));
        } else if (value instanceof Long) {
            this.workflowVariables.add(
                    new WorkflowVariable(name, WorkflowVariableType.LONG, value));
        } else if (value instanceof String) {
            this.workflowVariables.add(
                    new WorkflowVariable(name, WorkflowVariableType.STRING, value));
        } else if (value instanceof Serializable) {
            this.workflowVariables.add(
                    new WorkflowVariable(name, WorkflowVariableType.OBJECT, value));
        } else if (value instanceof List
                && !((List) value).isEmpty()
                && ((List) value).get(0) instanceof String) {
            this.workflowVariables.add(
                    new WorkflowVariable(name, WorkflowVariableType.LIST_OF_STRING, value));
        } else if (value instanceof List
                && !((List) value).isEmpty()
                && ((List) value).get(0) instanceof Integer) {
            this.workflowVariables.add(
                    new WorkflowVariable(name, WorkflowVariableType.LIST_OF_INTEGER, value));
        } else if (value instanceof List
                && !((List) value).isEmpty()
                && ((List) value).get(0) instanceof Boolean) {
            this.workflowVariables.add(
                    new WorkflowVariable(name, WorkflowVariableType.LIST_OF_BOOLEAN, value));
        } else if (value instanceof List
                && !((List) value).isEmpty()
                && ((List) value).get(0) instanceof Long) {
            this.workflowVariables.add(
                    new WorkflowVariable(name, WorkflowVariableType.LIST_OF_LONG, value));
        } else if (value instanceof List
                && !((List) value).isEmpty()
                && ((List) value).get(0) instanceof Serializable) {
            this.workflowVariables.add(
                    new WorkflowVariable(name, WorkflowVariableType.LIST_OF_OBJECT, value));
        } else if (value instanceof List && ((List) value).isEmpty()) {
            throw new WorkflowVariableTypeParseException(
                    "Unable to set a variable of type "
                            + value.getClass()
                            + ", because collection is empty");
        } else {
            throw new WorkflowVariableTypeParseException(
                    "Unable to set a variable of type "
                            + value.getClass()
                            + ", suported variable : "
                            + WorkflowVariableType.values());
        }
        return this;
    }

    public JourneyBuilder addStep(Step step) {
        this.steps.add(step);
        return this;
    }

    public WorkflowDefinition build() {
        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setName(this.name);
        this.tickets.forEach(workflowDefinition::setTicket);
        workflowDefinition.setWorkflowVariables(this.workflowVariables);
        this.steps.forEach(workflowDefinition::addStep);
        return workflowDefinition;
    }
}
