package com.anode.workflow;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.steps.responses.TaskResponse;

public class StepResponseFactory {

  private static Map<String, Queue<TaskResponse>> actions = new HashMap<>();
  private static Map<String, Queue<Long>> delays = new HashMap<>();

  public synchronized static void addResponse(String stepName, StepResponseType urt, String wb, String ticket) {
    add(stepName, urt, wb, ticket, 0);
  }

  public synchronized static void addResponse(String stepName, StepResponseType urt, String wb, String ticket, long delayInMs) {
    add(stepName, urt, wb, ticket, delayInMs);
  }

  private static void add(String stepName, StepResponseType urt, String wb, String ticket, long delayInMs) {
    TaskResponse r = new TaskResponse(urt, ticket, wb);
    Queue<TaskResponse> q = actions.get(stepName);
    if (q == null) {
      q = new LinkedList<>();
      actions.put(stepName, q);
    }
    q.add(r);

    // put the delay
    Queue<Long> q1 = delays.get(stepName);
    if (q1 == null) {
      q1 = new LinkedList<>();
      delays.put(stepName, q1);
    }
    q1.add(delayInMs);
  }

  public synchronized static TestStepResponse getResponse(String stepName) {
    TaskResponse r = new TaskResponse(StepResponseType.OK_PROCEED, "", "");
    Queue<TaskResponse> q = actions.get(stepName);
    if (q != null) {
      if (q.size() > 0) {
        r = q.remove();
      }
    }

    // for setting a break point only
    if (stepName.equals("step16")) {
      int i = 0;
    }

    long delay = 0;
    Queue<Long> q1 = delays.get(stepName);
    if (q1 != null) {
      if (q1.size() > 0) {
        delay = q1.remove();
      }
    }

    return new TestStepResponse(r, delay);
  }

  public synchronized static void clear() {
    actions.clear();
    delays.clear();
  }

}
