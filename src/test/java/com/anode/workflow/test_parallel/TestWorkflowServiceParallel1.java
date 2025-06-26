package com.anode.workflow.test_parallel;

import com.anode.tool.StringUtils;
import com.anode.tool.document.JDocument;
import com.anode.workflow.CommonDao;
import com.anode.workflow.MemoryDao;
import com.anode.workflow.RouteResponseFactory;
import com.anode.workflow.StepResponseFactory;
import com.anode.workflow.TestHandler;
import com.anode.workflow.TestManager;
import com.anode.workflow.WorkflowService;
import com.anode.workflow.entities.sla.Milestone;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.entities.workflows.WorkflowVariables;
import com.anode.workflow.mapper.WorkflowDefinitionMapper;
import com.anode.workflow.service.EventHandler;
import com.anode.workflow.service.SlaQueueManager;
import com.anode.workflow.service.WorkflowComponantFactory;
import com.anode.workflow.service.runtime.RuntimeService;
import com.anode.workflow.test_singular.TestWorkflowService;
import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestWorkflowServiceParallel1 {

    private static String baseDirPath = "./target/test-data-results/";
    private static RuntimeService rts = null;
    private static String simpleClassName = MethodHandles.lookup().lookupClass().getSimpleName();

    // set to true if you want to log to disk to trouble shoot any specific test case
    private static boolean writeFiles = false;

    // set to true if you want to log to console
    private static boolean writeToConsole = false;

    public static WorkflowContext startCase(
            String caseId, String journeyJson, WorkflowVariables pvs, List<Milestone> journeySla) {
        WorkflowDefinition journey = WorkflowDefinitionMapper.toEntity(new JDocument(journeyJson));
        return rts.startCase(caseId, journey, pvs, journeySla);
    }

    @BeforeAll
    protected static void beforeAll() {
        TestManager.init(System.out, new ByteArrayOutputStream(), 10, 30000);
    }

    @BeforeEach
    protected void beforeEach() {
        TestManager.reset();
        StepResponseFactory.clear();
        RouteResponseFactory.clear();
    }

    private static void init(
            CommonDao dao,
            WorkflowComponantFactory factory,
            EventHandler handler,
            SlaQueueManager sqm) {
        rts = WorkflowService.instance().getRunTimeService(dao, factory, handler, sqm);
    }

    private static void runJourney(String journey, MemoryDao dao) {
        String json =
                StringUtils.getResourceAsString(
                        TestWorkflowService.class, "/workflow_service/" + journey + ".json");
        if (dao.get(Object.class, "workflow_process_info-1.json") == null) {
            startCase("1", json, null, null);
        }

        try {
            while (true) {
                System.out.println();
                rts.resumeCase("1");
            }
        } catch (RuntimeException e) {
            System.out.println("Exception -> " + e.getMessage());
        }
    }

    // 3 branches, happy path i.e. all branches proceed
    public static void setScenario1() {
        // nothing to do
    }

    @Test
    void testScenario1() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario1();
        init(dao, new TestComponentFactoryParallel1(), new TestHandler(), null);
        runJourney("parallel_test_1", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEqualsTodo(writeToConsole, simpleClassName + "." + methodName, null);
    }
}
