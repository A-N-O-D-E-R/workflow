

package com.anode.workflow.test_parallel_in_dyn_parallel;


import com.anode.workflow.*;
import com.anode.workflow.entities.steps.InvokableTask;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.steps.responses.TaskResponse;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.service.EventHandler;
import com.anode.workflow.service.SlaQueueManager;
import com.anode.workflow.service.WorkflowComponantFactory;
import com.anode.workflow.service.runtime.RuntimeService;


public class TestStepPidp implements InvokableTask {

  private String name = null;
  private WorkflowContext pc = null;

  public TestStepPidp(WorkflowContext pc) {
    this.name = pc.getCompName();
    this.pc = pc;
  }

  public String getName() {
    return name;
  }

  public TaskResponse executeStep() {
    String stepName = pc.getStepName();
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
