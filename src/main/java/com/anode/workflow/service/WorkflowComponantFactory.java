package com.anode.workflow.service;

import com.anode.workflow.entities.workflows.WorkflowContext;

public interface WorkflowComponantFactory {
    public Object getObject(WorkflowContext workflowContext);
}
