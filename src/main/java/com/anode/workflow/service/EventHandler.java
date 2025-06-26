package com.anode.workflow.service;

import com.anode.workflow.entities.events.EventType;
import com.anode.workflow.entities.workflows.WorkflowContext;

public interface EventHandler {

    public void invoke(EventType event, WorkflowContext workflowContext);
  
  }
