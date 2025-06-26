package com.anode.workflow.entities.steps.responses;

import com.anode.workflow.service.ErrorHandler;
import lombok.Getter;

@Getter
public class TaskResponse {
    private StepResponseType unitResponseType = null;
  private String ticket = "";
  private String workBasket = "";
  private ErrorHandler error = new ErrorHandler();

  public TaskResponse(StepResponseType unitResponseType, String ticket, String workBasket) {
    init(unitResponseType, ticket, workBasket, new ErrorHandler());
  }

  public TaskResponse(StepResponseType unitResponseType, String ticket, String workBasket, ErrorHandler error) {
    init(unitResponseType, ticket, workBasket, error);
  }

  private void init(StepResponseType unitResponseType, String ticket, String workBasket, ErrorHandler error) {
    this.unitResponseType = unitResponseType;
    if (ticket != null) {
      this.ticket = ticket;
    }
    if (workBasket != null) {
      this.workBasket = workBasket;
    }
    this.error = error;
  }

}
