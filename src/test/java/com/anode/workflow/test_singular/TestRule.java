

package com.anode.workflow.test_singular;


import com.anode.workflow.RouteResponseFactory;
import com.anode.workflow.entities.steps.InvokableRoute;
import com.anode.workflow.entities.steps.responses.RouteResponse;
import com.anode.workflow.entities.workflows.WorkflowContext;


public class TestRule implements InvokableRoute {

  private String name = null;
  private WorkflowContext pc = null;

  public TestRule(WorkflowContext pc) {
    this.name = pc.getCompName();
    this.pc = pc;
  }

  public String getName() {
    return name;
  }

  public RouteResponse executeRoute() {
    String stepName = pc.getStepName();
    return RouteResponseFactory.getResponse(stepName);
  }

}
