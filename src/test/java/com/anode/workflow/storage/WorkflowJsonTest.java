package com.anode.workflow.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anode.tool.StringUtils;
import com.anode.tool.document.JDocument;
import com.anode.workflow.entities.sla.Milestone;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.mapper.AbstractMapper;
import com.anode.workflow.mapper.MilestoneMapper;
import com.anode.workflow.mapper.WorkflowDefinitionMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkflowJsonTest {

    @BeforeAll
    static void loadModels() {
        AbstractMapper.loadModel();
    }

    private static String resource(String name) {
        return StringUtils.getResourceAsString(WorkflowJsonTest.class, "/workflow_service/" + name);
    }

    @Test
    void definitionRoundTrips() {
        WorkflowDefinition definition =
                WorkflowDefinitionMapper.toEntity(new JDocument(resource("parallel_test.json")));

        WorkflowDefinition read = WorkflowJson.read(WorkflowJson.write(definition), WorkflowDefinition.class);

        assertThat(read).usingRecursiveComparison().isEqualTo(definition);
    }

    @Test
    void slaRoundTrips() {
        List<Milestone> sla = MilestoneMapper.toEntities(new JDocument(resource("test_journey_sla.json")));
        assertThat(sla).isNotEmpty();

        List<?> read = WorkflowJson.read(WorkflowJson.write(sla), List.class);

        assertThat(read).usingRecursiveComparison().isEqualTo(sla);
    }

    @Test
    void textRoundTrips() {
        assertThat(WorkflowJson.read(WorkflowJson.write("{\"a\":1}"), Object.class)).isEqualTo("{\"a\":1}");
    }

    @Test
    void onlyTheEngineKindsAreAccepted() {
        assertThatThrownBy(() -> WorkflowJson.write(42)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowJson.read("{\"kind\":\"java.lang.Runtime\",\"value\":{}}", Object.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
