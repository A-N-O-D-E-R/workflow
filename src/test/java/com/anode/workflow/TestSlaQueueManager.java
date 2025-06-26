package com.anode.workflow;

import com.anode.workflow.entities.sla.Milestone;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.mapper.MilestoneMapper;
import com.anode.workflow.service.SlaQueueManager;
import java.util.List;

public class TestSlaQueueManager implements SlaQueueManager {

    @Override
    public void enqueue(WorkflowContext pc, List<Milestone> milestones) {
        System.out.println("Received enqueue request. Json below");
        System.out.println(MilestoneMapper.toJDocument(milestones).getPrettyPrintJson());
    }

    @Override
    public void dequeue(WorkflowContext pc, String wb) {
        System.out.println("Received dequeue request for workbasket -> " + wb);
    }

    @Override
    public void dequeueAll(WorkflowContext pc) {
        System.out.println("Received dequeue all request");
    }
}
