package com.anode.workflow.service;

import com.anode.tool.document.Document;
import com.anode.tool.service.CommonService;
import com.anode.workflow.entities.steps.Step;
import com.anode.workflow.entities.workflows.WorkflowInfo;
import com.anode.workflow.mapper.WorkflowInfoMapper;
import com.anode.workflow.service.runtime.RuntimeService;
import java.util.List;

public class AuditLogService {
    public static void writeAuditLog(
            CommonService dao,
            WorkflowInfo pi,
            Step lastStep,
            List<String> branches,
            String compName) {
        // write the process info as audit log
        long seq = dao.incrCounter("workflow_audit_log_counter-" + pi.getCaseId());
        String s = String.format("%05d", seq);
        String key =
                RuntimeService.AUDIT_LOG
                        + RuntimeService.SEP
                        + pi.getCaseId()
                        + "_"
                        + s
                        + "_"
                        + compName;

        if (lastStep != null) {
            pi.getSetter().setLastStepExecuted(lastStep);
        }
        Document d = WorkflowInfoMapper.toJDocument(pi);

        // special handling if the last unit executed was a route
        if (branches != null) {
            for (int i = 0; i < branches.size(); i++) {
                String branch = branches.get(i);
                d.setArrayValueString("$.process_info.branches[%]", branch, i + "");
            }
        }

        // save document
        dao.saveOrUpdate(key, d.getPrettyPrintJson());
    }
}
