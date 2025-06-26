package com.anode.workflow.entities.workflows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorkflowVariables {
    private Map<String, WorkflowVariable> workflowVariableMap = new ConcurrentHashMap<>();

    public WorkflowVariables() {}

    protected WorkflowVariables(Map<String, WorkflowVariable> workflowVariableMap) {
        this.workflowVariableMap = workflowVariableMap;
    }

    public <T> T getObject(String name) {
        WorkflowVariable pv = workflowVariableMap.get(name);
        if (pv == null) {
            return null;
        } else {
            return (T) pv.getValue();
        }
    }

    public <T> List<T> getListObject(String name) {
        WorkflowVariable pv = workflowVariableMap.get(name);
        if (pv == null) {
            return null;
        } else {
            return (List<T>) pv.getValue();
        }
    }

    public Integer getInteger(String name) {
        return getObject(name);
    }

    public Long getLong(String name) {
        return getObject(name);
    }

    public String getString(String name) {
        return getObject(name);
    }

    public Boolean getBoolean(String name) {
        return getObject(name);
    }

    public String getValueAsString(String name) {
        String s = null;
        WorkflowVariable pv = workflowVariableMap.get(name);

        if (pv == null) {
            return null;
        } else {
            Object value = pv.getValue();
            switch (pv.getType()) {
                case BOOLEAN:
                    {
                        Boolean b = (Boolean) value;
                        s = b.toString();
                        break;
                    }

                case LONG:
                    {
                        Long l = (Long) value;
                        s = String.valueOf(l);
                        break;
                    }

                case INTEGER:
                    {
                        Integer i = (Integer) value;
                        s = String.valueOf(i);
                        break;
                    }
                case OBJECT:
                    {
                        s = String.valueOf(value);
                    }

                case LIST_OF_OBJECT:
                    {
                        s = String.valueOf(value);
                    }

                case STRING:
                    {
                        s = (String) value;
                        break;
                    }
            }

            return s;
        }
    }

    public WorkflowVariable.WorkflowVariableType getType(String name) {
        WorkflowVariable pv = workflowVariableMap.get(name);
        if (pv == null) {
            return null;
        } else {
            return pv.getType();
        }
    }

    public void setValue(String name, WorkflowVariable.WorkflowVariableType type, Object value) {
        WorkflowVariable pv = workflowVariableMap.get(name);
        if (pv == null) {
            pv = new WorkflowVariable(name, type, value);
        } else {
            pv.setValue(value);
        }
        workflowVariableMap.put(name, pv);
    }

    public List<WorkflowVariable> getListOfWorkflowVariable() {
        return new ArrayList<>(workflowVariableMap.values());
    }
}
