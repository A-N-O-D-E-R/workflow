package com.anode.workflow.entities.steps.responses;

import java.util.List;

import com.anode.workflow.service.ErrorHandler;
import lombok.Getter;

@Getter
public class RouteResponse {
  private StepResponseType unitResponseType = null;
  private List<String> branches = null;
  private String workBasket = "";
  private ErrorHandler error = new ErrorHandler();

  public RouteResponse(StepResponseType unitResponseType, List<String> branches, String workBasket) {
    init(unitResponseType, branches, workBasket, new ErrorHandler());
  }

  public RouteResponse(StepResponseType unitResponseType, List<String> branches, String workBasket, ErrorHandler error) {
    init(unitResponseType, branches, workBasket, error);
  }

  private void init(StepResponseType unitResponseType, List<String> branches, String workBasket, ErrorHandler error) {
    this.unitResponseType = unitResponseType;
    if (branches != null) {
      this.branches = branches;
    }
    if (workBasket != null) {
      this.workBasket = workBasket;
    }
    this.error = error;
  }
}
