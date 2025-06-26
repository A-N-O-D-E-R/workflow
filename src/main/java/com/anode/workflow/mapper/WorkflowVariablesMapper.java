package com.anode.workflow.mapper;

import com.anode.tool.document.Document;
import com.anode.workflow.entities.workflows.WorkflowVariable;
import com.anode.workflow.entities.workflows.WorkflowVariable.WorkflowVariableType;
import com.anode.workflow.entities.workflows.WorkflowVariables;
import java.util.ArrayList;
import java.util.List;

public class WorkflowVariablesMapper extends AbstractMapper {

    public static List<WorkflowVariable> toEntities(Document d) {

        int size = d.getArraySize("$.process_info.process_variables[]");
        List<WorkflowVariable> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = d.getString("$.process_info.process_variables[%].name", i + "");
            String value = d.getString("$.process_info.process_variables[%].value", i + "");
            String type = d.getString("$.process_info.process_variables[%].type", i + "");
            WorkflowVariableType pvt = WorkflowVariableType.valueOf(type.toUpperCase());
            Object vo = WorkflowDefinitionMapper.getValueAsObject(pvt, value);
            WorkflowVariable pv =
                    new WorkflowVariable(
                            name, WorkflowVariableType.valueOf(type.toUpperCase()), vo);
            list.add(pv);
        }
        return list;
    }

    public static Document toJDocument(WorkflowVariables workflowVariables) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'toJDocument'");
    }
}
