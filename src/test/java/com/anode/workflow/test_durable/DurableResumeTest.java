package com.anode.workflow.test_durable;

import static org.assertj.core.api.Assertions.assertThat;

import com.anode.tool.StringUtils;
import com.anode.tool.document.JDocument;
import com.anode.workflow.RouteResponseFactory;
import com.anode.workflow.StepResponseFactory;
import com.anode.workflow.TestHandler;
import com.anode.workflow.WorkflowService;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.entities.workflows.WorkflowInfo;
import com.anode.workflow.mapper.AbstractMapper;
import com.anode.workflow.mapper.WorkflowDefinitionMapper;
import com.anode.workflow.service.runtime.RuntimeService;
import com.anode.workflow.test_singular.TestComponentFactory;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A case pended by one RuntimeService is resumed by another one that only sees the JSON written by
 * the first: what a process restart against a database does.
 */
class DurableResumeTest {

    private final Map<Serializable, String> database = new ConcurrentHashMap<>();

    @BeforeAll
    static void initEngine() {
        AbstractMapper.loadModel();
        WorkflowService.init(10, 30000, "-");
    }

    @BeforeEach
    void reset() {
        StepResponseFactory.clear();
        RouteResponseFactory.clear();
    }

    private RuntimeService freshRuntime() {
        return WorkflowService.instance()
                .getRunTimeService(new JsonDao(database), new TestComponentFactory(), new TestHandler(), null);
    }

    private static WorkflowDefinition linearJourney() {
        String json = StringUtils.getResourceAsString(
                DurableResumeTest.class, "/workflow_service/journey_00_linear.json");
        return WorkflowDefinitionMapper.toEntity(new JDocument(json));
    }

    @Test
    void aPendedCaseIsResumedFromItsSerializedStateAfterARestart() {
        StepResponseFactory.addResponse("step3", StepResponseType.OK_PEND, "review", "");

        var pended = freshRuntime().startCase("D-1", linearJourney(), null, null);
        assertThat(pended.getPendWorkBasket()).isEqualTo("review");
        assertThat(freshRuntime().isCaseStarted("D-1")).isTrue();

        var resumed = freshRuntime().resumeCase("D-1");

        assertThat(resumed.getPendWorkBasket()).isNullOrEmpty();
        var stored = new JsonDao(database).get(WorkflowInfo.class,
                RuntimeService.WORKFLOW_INFO + RuntimeService.SEP + "D-1");
        assertThat(stored.isCaseCompleted()).isTrue();
    }
}
