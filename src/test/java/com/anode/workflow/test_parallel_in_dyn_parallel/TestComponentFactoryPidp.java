

package com.anode.workflow.test_parallel_in_dyn_parallel;


import com.anode.workflow.*;
import com.anode.workflow.entities.steps.InvokableTask;
import com.anode.workflow.entities.steps.Step.StepType;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.steps.responses.TaskResponse;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.service.EventHandler;
import com.anode.workflow.service.SlaQueueManager;
import com.anode.workflow.service.WorkflowComponantFactory;
import com.anode.workflow.service.runtime.RuntimeService;

public class TestComponentFactoryPidp implements WorkflowComponantFactory {

  @Override
  public Object getObject(WorkflowContext pc) {
    Object o = null;

    if ((pc.getCompType() == StepType.S_ROUTE) || (pc.getCompType() == StepType.P_ROUTE) || (pc.getCompType() == StepType.P_ROUTE_DYNAMIC)) {
      o = new TestRulePidp(pc);
    }
    else if (pc.getCompType() == StepType.TASK) {
      o = new TestStepPidp(pc);
    }

    return o;
  }

}
