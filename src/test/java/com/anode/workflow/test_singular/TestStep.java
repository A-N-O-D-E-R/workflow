package com.anode.workflow.test_singular;

import com.anode.tool.StringUtils;
import com.anode.workflow.StepResponseFactory;
import com.anode.workflow.TestStepResponse;
import com.anode.workflow.entities.steps.InvokableTask;
import com.anode.workflow.entities.steps.responses.TaskResponse;
import com.anode.workflow.entities.workflows.WorkflowContext;


public class TestStep implements InvokableTask {

  private String name = null;
  private WorkflowContext pc = null;

  public TestStep(WorkflowContext pc) {
    this.name = pc.getCompName();
    this.pc = pc;
  }

  public String getName() {
    return name;
  }

  public TaskResponse executeStep() {
    String stepName = pc.getStepName();
    //     if (StringUtils.compareWithMany(stepName, "step3", "step11", "step13", "step14")) {
    if (StringUtils.compareWithMany(stepName, "step13")) {
      // only there to set a break point
      int i = 0;
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
