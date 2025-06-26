package com.anode.workflow.test_parallel;

import com.anode.tool.StringUtils;
import com.anode.tool.document.Document;
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
import com.anode.workflow.exceptions.WorkflowRuntimeException;
import com.anode.workflow.mapper.MilestoneMapper;
import com.anode.workflow.mapper.WorkflowDefinitionMapper;
import com.anode.workflow.service.EventHandler;
import com.anode.workflow.service.SlaQueueManager;
import com.anode.workflow.service.WorkflowComponantFactory;
import com.anode.workflow.service.runtime.RuntimeService;
import com.anode.workflow.test_singular.TestWorkflowService;
import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestWorkflowServiceParallel {

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
        TestManager.init(System.out, new ByteArrayOutputStream(), 20, 30000);
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

    // 3 branches, happy path i.e. all branches proceed
    public static void setScenario1() {
        // nothing to do
    }

    // 3 branches, 1st branch ok proceeds, 2nd branch pends, 3rd branch pends
    public static void setScenario2() {
        StepResponseFactory.addResponse("step_2_1", StepResponseType.OK_PROCEED, "", "");

        StepResponseFactory.addResponse("step_2_2", StepResponseType.OK_PEND, "test_wb", "");
        StepResponseFactory.addResponse("step_2_2", StepResponseType.OK_PROCEED, "", "");

        StepResponseFactory.addResponse("step_2_3", StepResponseType.OK_PEND, "test_wb", "");
        StepResponseFactory.addResponse("step_2_3", StepResponseType.OK_PROCEED, "", "");
    }

    // 3 branches, all 3 error pend
    public static void setScenario2_1() {
        StepResponseFactory.addResponse("step_2_1", StepResponseType.ERROR_PEND, "error_wb", "");
        StepResponseFactory.addResponse("step_2_1", StepResponseType.OK_PROCEED, "", "");

        StepResponseFactory.addResponse("step_2_2", StepResponseType.ERROR_PEND, "error_wb", "");
        StepResponseFactory.addResponse("step_2_2", StepResponseType.OK_PROCEED, "", "");

        StepResponseFactory.addResponse("step_2_3", StepResponseType.ERROR_PEND, "error_wb", "");
        StepResponseFactory.addResponse("step_2_3", StepResponseType.OK_PROCEED, "", "");
    }

    // 3 branches, 1st branch ok proceeds, 2nd branch pends and 3rd branch raises ticket
    // then a further pend after ticket is raised
    public static void setScenario3() {
        StepResponseFactory.addResponse("step_2_1", StepResponseType.OK_PROCEED, "", "");

        StepResponseFactory.addResponse("step_2_2", StepResponseType.OK_PEND, "test_wb", "");
        StepResponseFactory.addResponse("step_2_2", StepResponseType.OK_PROCEED, "", "");

        // we add delay to ensure that the ticket is set after above two branches are executed
        StepResponseFactory.addResponse("step_2_3", StepResponseType.OK_PROCEED, "", "reject", 500);

        StepResponseFactory.addResponse("step_4", StepResponseType.OK_PEND, "test_wb", "");
        StepResponseFactory.addResponse("step_4", StepResponseType.OK_PROCEED, "", "");
    }

    // 3 branches, 1st branch ok proceeds, 2nd branch pends and 3rd branch raises ticket
    // with a pend, then a further pend after ticket is raised
    public static void setScenario4() {
        StepResponseFactory.addResponse("step_2_1", StepResponseType.OK_PROCEED, "", "");

        StepResponseFactory.addResponse("step_2_2", StepResponseType.OK_PEND, "test_wb", "");
        StepResponseFactory.addResponse("step_2_2", StepResponseType.OK_PROCEED, "", "");

        StepResponseFactory.addResponse(
                "step_2_3", StepResponseType.OK_PEND, "test_wb", "reject", 1000);

        StepResponseFactory.addResponse("step_4", StepResponseType.OK_PEND, "test_wb", "");
        StepResponseFactory.addResponse("step_4", StepResponseType.OK_PROCEED, "", "");
    }

    private static void runJourney(String journey, MemoryDao dao) {
        String json =
                StringUtils.getResourceAsString(
                        TestWorkflowService.class, "/workflow_service/" + journey + ".json");
        String slaJson =
                StringUtils.getResourceAsString(
                        TestWorkflowService.class, "/workflow_service/" + journey + "_sla.json");
        ;

        Document d = Objects.nonNull(slaJson) ? new JDocument(slaJson) : null;

        if (dao.get(Object.class, "workflow_process_info-1.json") == null) {
            startCase("1", json, null, MilestoneMapper.toEntities(d));
        }

        try {
            while (true) {
                System.out.println();
                rts.resumeCase("1");
            }
        } catch (WorkflowRuntimeException e) {
            System.out.println("Exception -> " + e.getMessage());
        }
    }

    @Test
    void testScenario1() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario1();
        init(dao, new TestComponentFactoryParallel(), new TestHandler(), null);
        runJourney("parallel_test", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals2(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_parallel/test_scenario_1_expected.txt");
    }

    @Test
    void testScenario2() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario2();
        init(dao, new TestComponentFactoryParallel(), new TestHandler(), null);
        runJourney("parallel_test", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals2(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_parallel/test_scenario_2_expected.txt");
    }

    @Test
    void testScenario2_1() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario2_1();
        init(dao, new TestComponentFactoryParallel(), new TestHandler(), null);
        runJourney("parallel_test", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals2(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_parallel/test_scenario_2_1_expected.txt");
    }

    @Test
    void testScenario3() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario3();
        init(dao, new TestComponentFactoryParallel(), new TestHandler(), null);
        runJourney("parallel_test", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals2(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_parallel/test_scenario_3_expected.txt");
    }

    @Test
    void testScenario4() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario4();
        init(dao, new TestComponentFactoryParallel(), new TestHandler(), null);
        runJourney("parallel_test", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals2(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_parallel/test_scenario_4_expected.txt");
    }
}
