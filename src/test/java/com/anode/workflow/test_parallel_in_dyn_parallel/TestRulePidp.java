

package com.anode.workflow.test_parallel_in_dyn_parallel;

import com.anode.tool.StringUtils;
import com.anode.workflow.entities.steps.InvokableRoute;
import com.anode.workflow.entities.steps.responses.RouteResponse;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.entities.workflows.WorkflowInfo;


import java.util.ArrayList;
import java.util.List;


public class TestRulePidp implements InvokableRoute {

  private String name = null;
  private WorkflowContext pc = null;

  public TestRulePidp(com.anode.workflow.entities.workflows.WorkflowContext pc) {
    this.name = pc.getCompName();
    this.pc = pc;
  }

  public String getName() {
    return name;
  }

  public RouteResponse executeRoute() {
    List<String> branches = new ArrayList<>();
    com.anode.workflow.entities.steps.responses.RouteResponse resp = null;
    String name = pc.getCompName();

    while (true) {
      if (com.anode.tool.StringUtils.compareWithMany(name, "r1_c")) {
        branches.add("1");
        branches.add("2");
        branches.add("3");
        break;
      }

      if (StringUtils.compareWithMany(name, "r2_c")) {
        branches.add("1");
        branches.add("2");
        branches.add("3");
        break;
      }

      break;
    }

    resp = new com.anode.workflow.entities.steps.responses.RouteResponse(com.anode.workflow.entities.steps.responses.StepResponseType.OK_PROCEED, branches, null);

    return resp;
  }

}
