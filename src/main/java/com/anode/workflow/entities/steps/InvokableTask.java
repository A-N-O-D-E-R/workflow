package com.anode.workflow.entities.steps;

import com.anode.workflow.entities.steps.responses.TaskResponse;

public interface InvokableTask {

    public TaskResponse executeStep();
}
