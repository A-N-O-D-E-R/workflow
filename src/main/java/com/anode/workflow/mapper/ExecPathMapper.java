package com.anode.workflow.mapper;

import com.anode.tool.document.Document;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.workflows.paths.ExecPath;
import com.anode.workflow.entities.workflows.paths.ExecPath.ExecPathStatus;
import com.anode.workflow.service.ErrorHandler;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ExecPathMapper extends AbstractMapper {

    private static List<ExecPath> toEntity(String json) throws JsonMappingException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(json);
        List<ExecPath> execPaths = new ArrayList<>();
        for (JsonNode execPathNode : rootNode.path("process_info").path("exec_paths")) {
            String name = execPathNode.path("name").asText();
            String status = execPathNode.path("status").asText();
            String step = execPathNode.path("step").asText();
            String pendWorkBasket = execPathNode.path("pend_workbasket").asText();
            String prevPendWorkBasket = execPathNode.path("prev_pend_workbasket").asText();
            String tbcSlaWorkBasket = execPathNode.path("tbc_sla_workbasket").asText();
    
            ErrorHandler et = new ErrorHandler();
            JsonNode errorNode = execPathNode.path("error");
            if (!errorNode.isMissingNode()) {
                String errorCode = errorNode.path("code").asText();
                String errorMessage = errorNode.path("message").asText();
                String errorDetails = errorNode.path("details").asText();
                boolean isRetryable = errorNode.path("is_retryable").asBoolean();
                et.setErrorCode(Integer.valueOf(errorCode));
                et.setErrorMessage(errorMessage);
                et.setErrorDetails(errorDetails);
                et.setRetryable(isRetryable);
            }
    
            StepResponseType urt = null;
            String surt = execPathNode.path("unit_response_type").asText();
            if (!surt.isEmpty()) {
                urt = StepResponseType.valueOf(surt.toUpperCase());
            }
    
            ExecPath ep = new ExecPath(name);
            ep.set(ExecPathStatus.valueOf(status.toUpperCase()), step, urt);
            ep.setPendWorkBasket(pendWorkBasket);
            ep.setPendError(et);
            ep.setPrevPendWorkBasket(prevPendWorkBasket);
            ep.setTbcSlaWorkBasket(tbcSlaWorkBasket);
            execPaths.add(ep);
        }
        return execPaths;
    }

    public static List<ExecPath> toEntity(Document d) {

        List<ExecPath> execPaths = new ArrayList<>();
        int size = d.getArraySize("$.process_info.exec_paths[]");
        for (int i = 0; i < size; i++) {
            String name = d.getString("$.process_info.exec_paths[%].name", i + "");
            String status = d.getString("$.process_info.exec_paths[%].status", i + "");
            String step = d.getString("$.process_info.exec_paths[%].step", i + "");
            String pendWorkBasket =
                    d.getString("$.process_info.exec_paths[%].pend_workbasket", i + "");
            String prevPendWorkBasket =
                    d.getString("$.process_info.exec_paths[%].prev_pend_workbasket", i + "");
            String tbcSlaWorkBasket =
                    d.getString("$.process_info.exec_paths[%].tbc_sla_workbasket", i + "");

            ErrorHandler et = new ErrorHandler();
            String errorCode = d.getString("$.process_info.exec_paths[%].error.code", i + "");
            if (errorCode != null) {
                String errorMessage =
                        d.getString("$.process_info.exec_paths[%].error.message", i + "");
                String errorDetails =
                        d.getString("$.process_info.exec_paths[%].error.details", i + "");
                boolean isRetryable =
                        d.getBoolean("$.process_info.exec_paths[%].error.is_retryable", i + "");
                et.setErrorCode(Integer.valueOf(errorCode));
                et.setErrorMessage(errorMessage);
                et.setErrorDetails(errorDetails);
                et.setRetryable(isRetryable);
            }

            String surt = d.getString("$.process_info.exec_paths[%].unit_response_type", i + "");
            StepResponseType urt = null;
            if (surt != null) {
                urt = StepResponseType.valueOf(surt.toUpperCase());
            }

            ExecPath ep = new ExecPath(name);
            ep.set(ExecPathStatus.valueOf(status.toUpperCase()), step, urt);
            ep.setPendWorkBasket(pendWorkBasket);
            ep.setPendError(et);
            ep.setPrevPendWorkBasket(prevPendWorkBasket);
            ep.setTbcSlaWorkBasket(tbcSlaWorkBasket);
            execPaths.add(ep);
        }
        return execPaths;
    }

    public static Document toJDocument(ExecPath execPath) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'toJDocument'");
    }
}
