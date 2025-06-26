

package com.anode.workflow;

import com.anode.workflow.entities.workflows.WorkflowContext;


public class TestWorkManager implements WorkManager {

  @Override
  public void changeWorkBasket(WorkflowContext pc, String oldWb, String newWb) {
    System.out.println("Received change work basket command. Old wb = " + oldWb + ", new wb = " + newWb);
  }

}
