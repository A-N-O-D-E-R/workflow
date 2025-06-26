package com.anode.workflow.service;

import com.anode.workflow.entities.sla.Milestone;
import com.anode.workflow.entities.workflows.WorkflowContext;
import java.util.List;

public interface SlaQueueManager {

    void enqueue(WorkflowContext pc, List<Milestone> milestones);

    void dequeue(WorkflowContext pc, String wb);

    void dequeueAll(WorkflowContext pc);
}
