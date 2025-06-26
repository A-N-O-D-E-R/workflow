package com.anode.workflow.test_parallel;

import com.anode.workflow.entities.steps.InvokableRoute;
import com.anode.workflow.entities.steps.responses.RouteResponse;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.workflows.WorkflowContext;
import java.util.ArrayList;
import java.util.List;

public class TestRuleParallel implements InvokableRoute {

    private String name = null;
    private WorkflowContext pc = null;

    public TestRuleParallel(WorkflowContext pc) {
        this.name = pc.getCompName();
        this.pc = pc;
    }

    public String getName() {
        return name;
    }

    public RouteResponse executeRoute() {
        List<String> branches = new ArrayList<>();
        RouteResponse resp = null;
        String stepName = pc.getStepName();

        if (stepName.equalsIgnoreCase("route_1")) {
            branches.add("1");
            branches.add("2");
            branches.add("3");
            resp = new RouteResponse(StepResponseType.OK_PROCEED, branches, null);
        }

        return resp;
    }
}
