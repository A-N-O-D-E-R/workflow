

package com.anode.workflow.sample;


import com.anode.workflow.entities.steps.InvokableRoute;
import com.anode.workflow.entities.steps.responses.RouteResponse;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.workflows.WorkflowContext;

import java.util.ArrayList;
import java.util.List;

public class SampleRoute implements InvokableRoute {

  private String name = null;
  private WorkflowContext pc = null;

  public SampleRoute(WorkflowContext pc) {
    this.name = pc.getCompName();
    this.pc = pc;
  }

  public String getName() {
    return name;
  }

  public RouteResponse executeRoute() {
    String compName = pc.getCompName();
    if (compName.equals("is_part_available")) {
      List<String> branches = new ArrayList<>();
      branches.add("Yes");
      return new RouteResponse(StepResponseType.OK_PROCEED, branches, "");
    }
    return null;
  }

}
