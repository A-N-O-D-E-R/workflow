package com.anode.workflow.service;

import com.anode.workflow.entities.sla.Milestone;
import com.anode.workflow.entities.sla.Milestone.Setup;
import com.anode.workflow.entities.sla.Milestone.SetupOption;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.mapper.MilestoneMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SlaQueueService {
    public static void enqueueCaseStartMilestones(
            WorkflowContext pc, List<Milestone> sla, SlaQueueManager slaQm) {
        List<Milestone> newSla = new ArrayList<>();
        for (Milestone milestone : sla) {
            if (milestone.getSetupOn().equals(Setup.case_start)) {
                Milestone newMilestone = Milestone.deepCopy(milestone);
                newSla.add(newMilestone);
            }
        }
        if (newSla.size() > 0) {
            log.info(
                    "Case id -> {}, raising sla milestones enqueue event on case start for"
                            + " milestones -> {}",
                    pc.getCaseId(),
                    MilestoneMapper.toJDocument(newSla).getPrettyPrintJson());
            slaQm.enqueue(pc, newSla);
        }
    }

    public static void enqueueCaseRestartMilestones(
            WorkflowContext pc, List<Milestone> sla, SlaQueueManager slaQm) {

        List<Milestone> newSla = new ArrayList<>();
        for (Milestone milestone : sla) {
            if (milestone.getSetupOn().equals(Setup.case_restart)) {
                Milestone newMilestone = Milestone.deepCopy(milestone);
                newSla.add(newMilestone);
            }
        }

        if (newSla.size() > 0) {
            log.info(
                    "Case id -> {}, raising sla milestones enqueue event on case restart for"
                            + " milestones -> {}",
                    pc.getCaseId(),
                    MilestoneMapper.toJDocument(newSla).getPrettyPrintJson());
            slaQm.enqueue(pc, newSla);
        }
    }

    private static boolean hasMilestones(List<Milestone> sla, String wb) {
        boolean b = false;
        for (Milestone milestone : sla) {
            if ((SetupOption.work_basket.equals(milestone.getType()))
                    && (milestone.getWorkBasketName() != null)
                    && (milestone.getWorkBasketName().equals(wb))) {
                b = true;
                break;
            }
        }
        return b;
    }

    public static void dequeueWorkBasketMilestones(
            WorkflowContext pc, String wb, List<Milestone> sla, SlaQueueManager slaQm) {
        if (wb.isEmpty()) {
            return;
        }

        if (hasMilestones(sla, wb)) {
            log.info(
                    "Case id -> {}, raising sla milestones dequeue event on exit of work basket ->"
                            + " {}",
                    pc.getCaseId(),
                    wb);
            slaQm.dequeue(pc, wb);
        }
    }

    public static void enqueueWorkBasketMilestones(
            WorkflowContext pc,
            Setup setupOn,
            String wb,
            List<Milestone> sla,
            SlaQueueManager slaQm) {

        List<Milestone> newSla = new ArrayList<>();
        for (Milestone milestone : sla) {
            if (milestone.getSetupOn().equals(setupOn)) {
                if (milestone.getWorkBasketName().equals(wb)) {
                    Milestone newMilestone = Milestone.deepCopy(milestone);
                    newSla.add(newMilestone);
                }
            }
        }

        if (newSla.size() > 0) {
            log.info(
                    "Case id -> {}, raising sla milestones enqueue event on -> {} of work basket ->"
                            + " {} for milestones -> {}",
                    pc.getCaseId(),
                    setupOn.toString(),
                    wb,
                    MilestoneMapper.toJDocument(newSla).getPrettyPrintJson());
            slaQm.enqueue(pc, newSla);
        }
    }
}
