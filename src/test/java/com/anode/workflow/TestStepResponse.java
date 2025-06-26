package com.anode.workflow;

import com.anode.workflow.entities.steps.responses.TaskResponse;

public class TestStepResponse {

  private TaskResponse stepResponse = null;
  private long delay = 0;

  public TestStepResponse(TaskResponse stepResponse, long delay) {
    this.stepResponse = stepResponse;
    this.delay = delay;
  }

  public TaskResponse getTaskResponse() {
    return stepResponse;
  }

  public long getDelay() {
    return delay;
  }

}
