

package com.anode.workflow;

import com.anode.workflow.entities.events.EventType;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.service.EventHandler;


public class TestHandler implements EventHandler {

  @Override
  public void invoke(EventType event, WorkflowContext pc) {
    System.out.println("Received event -> " + event.toString() + ", isPendAtSameStep -> " + pc.isPendAtSameStep());

    if (event == EventType.ON_PROCESS_PEND) {
      System.out.println("Pend workbasket -> " + pc.getPendWorkBasket());
    }

    if (event == EventType.ON_TICKET_RAISED) {
      System.out.println("Ticket raised -> " + pc.getTicketName());
    }
  }

}
