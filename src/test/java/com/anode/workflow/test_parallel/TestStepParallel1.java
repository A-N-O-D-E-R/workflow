package com.anode.workflow.test_parallel;

import com.anode.workflow.StepResponseFactory;
import com.anode.workflow.TestStepResponse;
import com.anode.workflow.entities.steps.InvokableTask;
import com.anode.workflow.entities.steps.responses.TaskResponse;
import com.anode.workflow.entities.workflows.WorkflowContext;

public class TestStepParallel1 implements InvokableTask {

    private String name = null;
    private WorkflowContext pc = null;

    public TestStepParallel1(WorkflowContext pc) {
        this.name = pc.getCompName();
        this.pc = pc;
    }

    public String getName() {
        return name;
    }

    public TaskResponse executeStep() {
        String stepName = pc.getStepName();
        if (stepName.equals("step_wait_1")
                || stepName.equals("step_wait_2")
                || stepName.equals("step_wait_3")) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        TestStepResponse tsr = StepResponseFactory.getResponse(stepName);
        TaskResponse sr = tsr.getTaskResponse();
        long delay = tsr.getDelay();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return sr;
    }
}
