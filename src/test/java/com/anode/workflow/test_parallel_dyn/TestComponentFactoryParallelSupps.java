

package com.anode.workflow.test_parallel_dyn;

import com.anode.workflow.entities.steps.Step.StepType;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.service.WorkflowComponantFactory;

public class TestComponentFactoryParallelSupps implements WorkflowComponantFactory {

  @Override
  public Object getObject(WorkflowContext pc) {
    Object o = null;

    if ((pc.getCompType() == StepType.S_ROUTE) || (pc.getCompType() == StepType.P_ROUTE) || (pc.getCompType() == StepType.P_ROUTE_DYNAMIC)) {
      o = new TestRuleParallelSupps(pc);
    }
    else if (pc.getCompType() == StepType.TASK) {
      o = new TestStepParallelSupps(pc);
    }

    return o;
  }

}
