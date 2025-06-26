

package com.anode.workflow.sample;

import com.anode.workflow.entities.steps.Step.StepType;
import com.anode.workflow.service.WorkflowComponantFactory;

public class SampleComponentFactory implements WorkflowComponantFactory {

  @Override
  public Object getObject(com.anode.workflow.entities.workflows.WorkflowContext pc) {
    Object o = null;

    if (pc.getCompType() == StepType.S_ROUTE) {
      o = new SampleRoute(pc);
    }
    else if (pc.getCompType() == StepType.TASK) {
      o = new SampleStep(pc);
    }

    return o;
  }

}
