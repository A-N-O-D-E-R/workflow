package com.anode.workflow.mapper;

import com.anode.tool.document.Document;
import com.anode.tool.document.JDocument;
import com.anode.workflow.entities.workflows.WorkflowInfo;
import com.anode.workflow.entities.workflows.WorkflowVariable;
import com.anode.workflow.entities.workflows.paths.ExecPath;
import com.anode.workflow.service.ErrorHandler;
import java.time.Instant;

public class WorkflowInfoMapper extends AbstractMapper {

    public static Document toJDocument(WorkflowInfo info) {

        if (info == null) {
            return null;
        }
        Document d = new JDocument();

        // write last executed unit details
        if (info.getLastUnitExecuted() == null) {
            d.setString("$.process_info.last_executed_step", "");
            d.setString("$.process_info.last_executed_comp_name", "");
        } else {
            d.setString("$.process_info.last_executed_step", info.getLastUnitExecuted().getName());
            d.setString(
                    "$.process_info.last_executed_comp_name",
                    info.getLastUnitExecuted().getComponentName());
        }

        // write pend info
        d.setString("$.process_info.pend_exec_path", info.getPendExecPath());

        // write ts
        d.setLong("$.process_info.ts", Instant.now().toEpochMilli());

        // write isComplete status
        d.setBoolean("$.process_info.is_complete", info.getIsComplete());

        // write process variables
        int i = 0;
        for (WorkflowVariable var : info.getVariablesMap().values()) {
            d.setString("$.process_info.process_variables[%].name", var.getName(), i + "");
            d.setString(
                    "$.process_info.process_variables[%].value", var.getValue().toString(), i + "");
            d.setString(
                    "$.process_info.process_variables[%].type",
                    var.getType().toString().toLowerCase(), i + "");
            i++;
        }

        // write execution paths
        i = 0;
        synchronized (info.getExecPaths()) {
            for (ExecPath path : info.getExecPaths()) {
                d.setString("$.process_info.exec_paths[%].name", path.getName(), i + "");
                d.setString(
                        "$.process_info.exec_paths[%].status",
                        path.getStatus().toString().toLowerCase(), i + "");

                String s = path.getStep();
                d.setString("$.process_info.exec_paths[%].step", s, i + "");

                if (s.equals("end")) {
                    d.setString("$.process_info.exec_paths[%].comp_name", s, i + "");
                } else {
                    s = info.getWorkflowDefinition().getStep(s).getComponentName();
                    d.setString("$.process_info.exec_paths[%].comp_name", s, i + "");
                }

                s = path.getPendWorkBasket();
                d.setString("$.process_info.exec_paths[%].pend_workbasket", s, i + "");

                s = path.getTicket();
                d.setString("$.process_info.exec_paths[%].ticket", s, i + "");

                ErrorHandler et = path.getPendError();
                d.setString(
                        "$.process_info.exec_paths[%].pend_error.code",
                        et.getErrorCodeAsString(), i + "");
                d.setString(
                        "$.process_info.exec_paths[%].pend_error.message",
                        et.getErrorMessage(), i + "");
                d.setString(
                        "$.process_info.exec_paths[%].pend_error.details",
                        et.getErrorMessage(), i + "");
                d.setBoolean(
                        "$.process_info.exec_paths[%].pend_error.is_retyable",
                        et.isRetryable(), i + "");

                s = path.getPrevPendWorkBasket();
                d.setString("$.process_info.exec_paths[%].prev_pend_workbasket", s, i + "");

                s = path.getTbcSlaWorkBasket();
                d.setString("$.process_info.exec_paths[%].tbc_sla_workbasket", s, i + "");

                if (path.getStepResponseType() != null) {
                    d.setString(
                            "$.process_info.exec_paths[%].unit_response_type",
                            path.getStepResponseType().toString().toLowerCase(), i + "");
                }
                i++;
            }
        }

        // write ticket info
        if (info.getTicket() != null) {
            d.setString("$.process_info.ticket", info.getTicket());
        }

        return d;
    }

    public static WorkflowInfo toEntity(Document document) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'toEntity'");
    }
}
