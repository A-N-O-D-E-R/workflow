package com.anode.workflow.test_concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.anode.tool.StringUtils;
import com.anode.tool.document.JDocument;
import com.anode.workflow.TestHandler;
import com.anode.workflow.WorkflowService;
import com.anode.workflow.entities.steps.InvokableTask;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.steps.responses.TaskResponse;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.mapper.AbstractMapper;
import com.anode.workflow.mapper.WorkflowDefinitionMapper;
import com.anode.workflow.service.WorkflowComponantFactory;
import com.anode.workflow.test_durable.JsonDao;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Concurrent cases that pend must each report their own pend, then each resume to completion. */
class ConcurrentPendTest {

    @BeforeAll
    static void init() {
        AbstractMapper.loadModel();
        WorkflowService.init(10, 30000, "-");
    }

    /** step3 pends the first time it runs for a case; every other step proceeds. */
    static final class PendOnceFactory implements WorkflowComponantFactory {
        private final Set<String> pended = ConcurrentHashMap.newKeySet();

        @Override
        public Object getObject(WorkflowContext ctx) {
            String caseId = ctx.getCaseId();
            String step = ctx.getStepName();
            return (InvokableTask) () -> step.equals("step3") && pended.add(caseId)
                    ? new TaskResponse(StepResponseType.OK_PEND, "", "wb-" + caseId)
                    : new TaskResponse(StepResponseType.OK_PROCEED, "", "");
        }
    }

    @Test
    void eachCasePendsAndResumesOnItsOwn() throws Exception {
        Map<Serializable, String> rows = new ConcurrentHashMap<>();
        var factory = new PendOnceFactory();
        WorkflowDefinition journey = WorkflowDefinitionMapper.toEntity(new JDocument(
                StringUtils.getResourceAsString(getClass(), "/workflow_service/journey_00_linear.json")));
        List<String> ids = IntStream.range(0, 30).mapToObj(i -> "K" + i).toList();

        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<WorkflowContext>> starts = ids.stream().map(id -> pool.submit(() ->
                    WorkflowService.instance().getRunTimeService(new JsonDao(rows), factory, new TestHandler(), null)
                            .startCase(id, journey, null, null))).toList();
            for (int i = 0; i < ids.size(); i++) {
                assertThat(starts.get(i).get().getPendWorkBasket()).as(ids.get(i)).isEqualTo("wb-" + ids.get(i));
            }
            List<Future<WorkflowContext>> resumes = ids.stream().map(id -> pool.submit(() ->
                    WorkflowService.instance().getRunTimeService(new JsonDao(rows), factory, new TestHandler(), null)
                            .resumeCase(id))).toList();
            for (var resume : resumes) {
                assertThat(resume.get().getPendWorkBasket()).isNullOrEmpty();
            }
        }
    }
}
