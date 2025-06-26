package com.anode.workflow.mapper;

import com.anode.tool.StringUtils;
import com.anode.tool.document.JDocument;

public abstract class AbstractMapper {
    public static void loadModel() {
        String json =
                StringUtils.getResourceAsString(
                        AbstractMapper.class, "/workflow/models/workflow_journey.json");
        JDocument.loadDocumentModel("workflow_journey", json);
        json =
                StringUtils.getResourceAsString(
                        AbstractMapper.class, "/workflow/models/workflow_journey_sla.json");
        JDocument.loadDocumentModel("workflow_journey_sla", json);
    }
}
