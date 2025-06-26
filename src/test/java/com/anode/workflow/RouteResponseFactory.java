package com.anode.workflow;

import java.util.*;

import com.anode.workflow.entities.steps.responses.RouteResponse;
import com.anode.workflow.entities.steps.responses.StepResponseType;


public class RouteResponseFactory {

  private static Map<String, Queue<RouteResponse>> actions = new HashMap<>();

  public synchronized static void addResponse(String stepName, StepResponseType urt, List<String> branches, String wb) {
    RouteResponse r = new RouteResponse(urt, branches, wb);
    Queue<RouteResponse> q = actions.get(stepName);
    if (q == null) {
      q = new LinkedList<>();
      actions.put(stepName, q);
    }
    q.add(r);
  }

  public synchronized static RouteResponse getResponse(String stepName) {
    List<String> branches = new ArrayList<>();
    branches.add("no");
    RouteResponse r = new RouteResponse(StepResponseType.OK_PROCEED, branches, "");

    Queue<RouteResponse> q = actions.get(stepName);
    if (q != null) {
      if (q.size() > 0) {
        r = q.remove();
      }
    }

    return r;
  }

  public synchronized static void clear() {
    actions.clear();
  }

}
