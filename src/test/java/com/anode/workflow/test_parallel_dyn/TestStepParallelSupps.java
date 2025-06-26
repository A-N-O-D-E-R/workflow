

package com.anode.workflow.test_parallel_dyn;

import com.anode.workflow.*;
import com.anode.workflow.entities.steps.InvokableTask;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.steps.responses.TaskResponse;
import com.anode.workflow.entities.workflows.WorkflowContext;

import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class TestStepParallelSupps implements InvokableTask {

  private String name = null;
  private WorkflowContext pc = null;
  private static Map<String, String> responses = new ConcurrentHashMap<>();
  private static volatile boolean setOnce = true;

  public TestStepParallelSupps(WorkflowContext pc) {
    this.name = pc.getCompName();
    this.pc = pc;
  }

  public String getName() {
    return name;
  }

  public TaskResponse executeStep() {
    TaskResponse response = null;

    String s = MessageFormat.format("Step -> {0}, execution path -> {1}", pc.getStepName(), pc.getExecPathName());
    System.out.println(s);

    if (pc.getStepName().equals("step_1")) {
      if (setOnce == true) {
        setOnce = false;
        response = get(100, StepResponseType.ERROR_PEND, null);
      }
      else {
        response = get(100, StepResponseType.OK_PROCEED, null);
      }
    }
    else {
      response = get(100, StepResponseType.OK_PROCEED, null);
    }

    try {
      Thread.sleep(1000);
    }
    catch (InterruptedException e) {
    }

    System.out.println("Exiting " + s);

    return response;
  }

  private TaskResponse get(int percent, StepResponseType first, StepResponseType second) {
    int num = RandomGen.get(1, 100);
    if (num <= percent) {
      if (first == StepResponseType.OK_PROCEED) {
        return new TaskResponse(first, null, null);
      }
      else {
        return new TaskResponse(first, null, "some_wb");
      }
    }
    else {
      if (second == StepResponseType.OK_PROCEED) {
        return new TaskResponse(second, null, null);
      }
      else {
        return new TaskResponse(second, null, "some_wb");
      }
    }
  }

}
