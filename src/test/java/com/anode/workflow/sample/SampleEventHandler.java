

package com.anode.workflow.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anode.workflow.entities.events.EventType;
import com.anode.workflow.entities.workflows.WorkflowContext;

public class SampleEventHandler implements com.anode.workflow.service.EventHandler {

  private static Logger logger = LoggerFactory.getLogger(SampleEventHandler.class);

  @Override
  public void invoke(EventType event, WorkflowContext pc) {
    logger.info("Received event -> {}", event.toString());
  }

}
