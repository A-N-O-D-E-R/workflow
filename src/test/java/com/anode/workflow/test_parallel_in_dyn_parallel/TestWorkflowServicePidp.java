package com.anode.workflow.test_parallel_in_dyn_parallel;

import com.anode.tool.document.JDocument;
import com.anode.workflow.CommonDao;
import com.anode.workflow.MemoryDao;
import com.anode.workflow.RouteResponseFactory;
import com.anode.workflow.StepResponseFactory;
import com.anode.workflow.TestHandler;
import com.anode.workflow.TestManager;
import com.anode.workflow.WorkflowService;
import com.anode.workflow.entities.sla.Milestone;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.entities.workflows.WorkflowVariables;
import com.anode.workflow.mapper.WorkflowDefinitionMapper;
import com.anode.workflow.service.EventHandler;
import com.anode.workflow.service.SlaQueueManager;
import com.anode.workflow.service.WorkflowComponantFactory;
import com.anode.workflow.service.runtime.RuntimeService;
import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestWorkflowServicePidp {

    private static String baseDirPath = "./target/test-data-results/";
    private static RuntimeService rts = null;
    private static String simpleClassName = MethodHandles.lookup().lookupClass().getSimpleName();

    // set to true if you want to log to disk to trouble shoot any specific test case
    private static boolean writeFiles = false;

    // set to true if you want to log to console
    private static boolean writeToConsole = false;

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

    // happy path
    public static void setScenario0() {
        // nothing to do
    }

    // pend in a step in dynamic parallel route scope
    public static void setScenario1() {
        StepResponseFactory.addResponse("r2_1_s1", StepResponseType.ERROR_PEND, "tech", "");
    }

    private static void runJourney(String journey, MemoryDao dao) {
        String json =
                com.anode.tool.StringUtils.getResourceAsString(
                        TestWorkflowServicePidp.class, "/workflow_service/" + journey + ".json");

        if (dao.get(Object.class, "workflow_journey-3.json ") == null) {
            startCase("3", json, null, null);
        }

        try {
            while (true) {
                System.out.println();
                rts.resumeCase("3");
            }
        } catch (RuntimeException e) {
            System.out.println("Exception -> " + e.getMessage());
        }
    }

    private static void init(
            CommonDao dao,
            WorkflowComponantFactory factory,
            EventHandler handler,
            SlaQueueManager sqm) {
        rts = WorkflowService.instance().getRunTimeService(dao, factory, handler, sqm);
    }

    @Test
    void testScenario0() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario0();
        init(dao, new TestComponentFactoryPidp(), new TestHandler(), null);
        runJourney("pidp_test", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEqualsTodo(writeToConsole, simpleClassName + "." + methodName, null);
    }

    @Test
    void testScenario1() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario1();
        init(dao, new TestComponentFactoryPidp(), new TestHandler(), null);
        runJourney("pidp_test", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEqualsTodo(writeToConsole, simpleClassName + "." + methodName, null);
    }

    public static WorkflowContext startCase(
            String caseId, String journeyJson, WorkflowVariables pvs, List<Milestone> journeySla) {
        WorkflowDefinition journey = WorkflowDefinitionMapper.toEntity(new JDocument(journeyJson));
        return rts.startCase(caseId, journey, pvs, journeySla);
    }
}
