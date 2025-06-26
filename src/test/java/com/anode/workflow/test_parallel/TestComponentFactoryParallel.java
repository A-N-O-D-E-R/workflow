package com.anode.workflow.test_parallel;

import com.anode.workflow.entities.steps.Step.StepType;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.service.WorkflowComponantFactory;

public class TestComponentFactoryParallel implements WorkflowComponantFactory {

    @Override
    public Object getObject(WorkflowContext pc) {
        Object o = null;

        if ((pc.getCompType() == StepType.S_ROUTE)
                || (pc.getCompType() == StepType.P_ROUTE)
                || (pc.getCompType() == StepType.P_ROUTE_DYNAMIC)) {
            o = new TestRuleParallel(pc);
        } else if (pc.getCompType() == StepType.TASK) {
            o = new TestStepParallel(pc);
        }

        return o;
    }
}
