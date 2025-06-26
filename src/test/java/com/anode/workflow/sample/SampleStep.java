package com.anode.workflow.sample;

import com.anode.workflow.entities.steps.InvokableTask;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.steps.responses.TaskResponse;
import com.anode.workflow.entities.workflows.WorkflowContext;

public class SampleStep implements InvokableTask {

    private String name = null;
    private WorkflowContext pc = null;

    public SampleStep(WorkflowContext pc) {
        this.name = pc.getCompName();
        this.pc = pc;
    }

    public String getName() {
        return name;
    }

    public TaskResponse executeStep() {
        String compName = pc.getCompName();

        if (compName.equals("start")) {
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }

        if (compName.equals("get_part_info")) {
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }

        if (compName.equals("get_part_inventory")) {
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }

        if (compName.equals("ship_part")) {
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }

        if (compName.equals("cancel_order")) {
            return new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }

        return null;
    }
}
