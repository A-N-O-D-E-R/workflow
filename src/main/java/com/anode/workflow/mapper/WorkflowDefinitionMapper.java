package com.anode.workflow.mapper;

import com.anode.tool.document.Document;
import com.anode.tool.document.JDocument;
import com.anode.workflow.entities.steps.Branch;
import com.anode.workflow.entities.steps.Join;
import com.anode.workflow.entities.steps.Pause;
import com.anode.workflow.entities.steps.Persist;
import com.anode.workflow.entities.steps.Route;
import com.anode.workflow.entities.steps.Step;
import com.anode.workflow.entities.steps.Step.StepType;
import com.anode.workflow.entities.steps.Task;
import com.anode.workflow.entities.tickets.Ticket;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.entities.workflows.WorkflowVariable;
import com.anode.workflow.entities.workflows.WorkflowVariable.WorkflowVariableType;
import com.anode.workflow.exceptions.WorkflowRuntimeException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorkflowDefinitionMapper extends AbstractMapper {

    public static WorkflowDefinition toEntity(Document d) {

        WorkflowDefinition pd = new WorkflowDefinition();

        pd.setName(d.getString("$.journey.name"));

        // process variables
        if (d.pathExists("$.journey.process_variables[]")) {
            int size = d.getArraySize("$.journey.process_variables[]");
            List<WorkflowVariable> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String name = d.getString("$.journey.process_variables[%].name", i + "");
                String value = d.getString("$.journey.process_variables[%].value", i + "");
                String type = d.getString("$.journey.process_variables[%].type", i + "");
                WorkflowVariableType pvt = WorkflowVariableType.valueOf(type.toUpperCase());
                Object vo = getValueAsObject(pvt, value);
                WorkflowVariable pv =
                        new WorkflowVariable(
                                name, WorkflowVariableType.valueOf(type.toUpperCase()), vo);
                list.add(pv);
            }
            pd.setWorkflowVariables(list);
        }

        // tickets
        if (d.pathExists("$.journey.tickets[]")) {
            int size = d.getArraySize("$.journey.tickets[]");
            for (int i = 0; i < size; i++) {
                String name = d.getString("$.journey.tickets[%].name", i + "");
                String stepName = d.getString("$.journey.tickets[%].step", i + "");
                Ticket t = new Ticket(name, stepName);
                pd.setTicket(t);
            }
        }

        // flows
        {
            int size = d.getArraySize("$.journey.flow[]");
            for (int i = 0; i < size; i++) {
                String si = i + "";

                StepType type = null;
                String s = d.getString("$.journey.flow[%].type", si);
                if (s == null) {
                    type = StepType.TASK;
                } else {
                    type = StepType.valueOf(s.toUpperCase());
                }

                Step unit = null;
                switch (type) {
                    case TASK:
                        unit = getTask(d, si);
                        break;

                    case S_ROUTE:
                        unit = getRoute(d, si, StepType.S_ROUTE);
                        break;

                    case P_ROUTE:
                        unit = getRoute(d, si, StepType.P_ROUTE);
                        break;

                    case P_ROUTE_DYNAMIC:
                        unit = getRoute(d, si, StepType.P_ROUTE_DYNAMIC);
                        break;

                    case PAUSE:
                        unit = getPause(d, si);
                        break;

                    case PERSIST:
                        unit = getPersist(d, si);
                        break;

                    case P_JOIN:
                        unit = getJoin(d, si);
                        break;
                }

                pd.addStep(unit);
            }
        }

        return pd;
    }

    protected static Object getValueAsObject(WorkflowVariableType type, String value) {
        Object vo = null;

        switch (type) {
            case BOOLEAN:
                {
                    vo = new Boolean(value);
                    break;
                }

            case LONG:
                {
                    vo = new Long(value);
                    break;
                }

            case INTEGER:
                {
                    vo = new Integer(value);
                    break;
                }

            case STRING:
                {
                    vo = value;
                    break;
                }
        }

        return vo;
    }

    private static Step getTask(Document d, String si) {
        String name = d.getString("$.journey.flow[%].name", si);
        String component = d.getString("$.journey.flow[%].component", si);
        String next = d.getString("$.journey.flow[%].next", si);
        String userData = d.getString("$.journey.flow[%].user_data", si);
        return new Task(name, component, next, userData);
    }

    private static Step getPause(Document d, String si) {
        String name = d.getString("$.journey.flow[%].name", si);
        String next = d.getString("$.journey.flow[%].next", si);
        return new Pause(name, next);
    }

    private static Step getPersist(Document d, String si) {
        String name = d.getString("$.journey.flow[%].name", si);
        String next = d.getString("$.journey.flow[%].next", si);
        return new Persist(name, next);
    }

    private static Step getJoin(Document d, String si) {
        String name = d.getString("$.journey.flow[%].name", si);
        String next = d.getString("$.journey.flow[%].next", si);
        return new Join(name, next);
    }

    private static Step getRoute(Document d, String si, StepType type) {
        String name = d.getString("$.journey.flow[%].name", si);
        String component = d.getString("$.journey.flow[%].component", si);
        String userData = d.getString("$.journey.flow[%].user_data", si);
        String next = d.getString("$.journey.flow[%].next", si);
        boolean hasBranches = d.pathExists("$.journey.flow[%].branches[]", si);

        if ((type == StepType.P_ROUTE) && (next != null)) {
            throw new WorkflowRuntimeException("A parallel route cannot have next specified");
        }

        if ((type == StepType.P_ROUTE_DYNAMIC) && (hasBranches == true)) {
            throw new WorkflowRuntimeException(
                    "A dynamic parallel route cannot have branches specified");
        }

        Route route = null;
        if (next != null) {
            route = new Route(name, component, userData, next, type);
        } else {
            Map<String, Branch> branches = new HashMap<>();
            int size = d.getArraySize("$.journey.flow[%].branches[]", si);
            for (int i = 0; i < size; i++) {
                String bname = d.getString("$.journey.flow[%].branches[%].name", si, i + "");
                String next1 = d.getString("$.journey.flow[%].branches[%].next", si, i + "");
                Branch branch = new Branch(bname, next1);
                branches.put(bname, branch);
            }
            route = new Route(name, component, userData, branches, type);
        }

        return route;
    }

    public static void validateJourneyDefinition(String journeyDefn) {

        Document jd = new JDocument(journeyDefn);
        validateJourneyDefinition(jd);
    }

    public static void validateJourneyDefinition(Document jd) {

        // we will check the following:
        // check against the model
        // check that each step name is unique
        // check that next of each step points to a valid step
        // check that branch names are unique
        // check that each branch points to a valid step

        String journeyName = jd.getString("$.journey.name");

        // validate against model
        if (jd.isTyped() == true) {
            if (jd.getType().equals("workflow_journey") == false) {
                jd.validate("workflow_journey");
            } else {
                // nothing to do as it must already be validated
            }
        } else {
            jd.validate("workflow_journey");
        }

        // build a set of steps and validate if any is repeated
        Set<String> steps = new HashSet<>();
        for (int i = 0; i < jd.getArraySize("$.journey.flow[]"); i++) {
            String stepName = jd.getString("$.journey.flow[%].name", i + "");
            if (steps.contains(stepName) == true) {
                throw new WorkflowRuntimeException(
                        "More than one occurrence(s) of a step name found. Journey name -> "
                                + journeyName
                                + ", step name -> "
                                + stepName);
            }
            steps.add(stepName);
        }
        if (steps.contains("end") == true) {
            throw new WorkflowRuntimeException(
                    "Step name cannot be end. Journey name ->" + journeyName);
        }
        steps.add("end"); // this is required for later

        // check that the next of each step points to a valid step
        for (int i = 0; i < jd.getArraySize("$.journey.flow[]"); i++) {
            String stepName = jd.getString("$.journey.flow[%].name", i + "");
            String nextName = jd.getString("$.journey.flow[%].next", i + "");
            if (nextName == null) {
                continue;
            }
            if (steps.contains(nextName) == false) {
                throw new WorkflowRuntimeException(
                        "Next step name specified in a step is not defined. Journey name -> "
                                + journeyName
                                + ", step name -> "
                                + stepName
                                + ", next name -> "
                                + nextName);
            }
        }

        // check that branch names are unique and point to valid steps
        for (int i = 0; i < jd.getArraySize("$.journey.flow[]"); i++) {
            if (jd.pathExists("$.journey.flow[%].branches[]", i + "") == false) {
                continue;
            }
            String stepName = jd.getString("$.journey.flow[%].name", i + "");
            Set<String> branchNames = new HashSet<>();
            for (int j = 0; j < jd.getArraySize("$.journey.flow[%].branches[]", i + ""); j++) {
                // check for branch name uniqueness
                String branchName =
                        jd.getString("$.journey.flow[%].branches[%].name", i + "", j + "");
                if (steps.contains(branchName) == true) {
                    throw new WorkflowRuntimeException(
                            "More than one occurrence(s) of a name in a branch found. Journey name"
                                    + " -> "
                                    + journeyName
                                    + ", step name -> "
                                    + stepName
                                    + ", branch name -> "
                                    + branchName);
                }
                branchNames.add(branchName);

                // check next pointing
                String nextName =
                        jd.getString("$.journey.flow[%].branches[%].next", i + "", j + "");
                if (steps.contains(nextName) == false) {
                    throw new WorkflowRuntimeException(
                            "Next step name specified in branch does not exist. Journey name -> "
                                    + journeyName
                                    + ", step name -> "
                                    + stepName
                                    + ", branch name -> "
                                    + branchName
                                    + ", next name -> "
                                    + nextName);
                }
            }
        }
    }

    public static Document toJDocument(WorkflowDefinition workflowDefinition) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'toJDocument'");
    }
}
