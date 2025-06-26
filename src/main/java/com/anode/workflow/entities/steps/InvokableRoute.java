package com.anode.workflow.entities.steps;

import com.anode.workflow.entities.steps.responses.RouteResponse;

public interface InvokableRoute {

  public RouteResponse executeRoute();

}
