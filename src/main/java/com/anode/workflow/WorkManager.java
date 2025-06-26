package com.anode.workflow;

import com.anode.workflow.entities.workflows.WorkflowContext;

public interface WorkManager {

    void changeWorkBasket(WorkflowContext workflowContext, String oldWorkBastket, String newWorkBastket);
  
  }
  