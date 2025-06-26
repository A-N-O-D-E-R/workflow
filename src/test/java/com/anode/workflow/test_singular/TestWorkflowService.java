package com.anode.workflow.test_singular;

import com.anode.tool.StringUtils;
import com.anode.tool.document.JDocument;
import com.anode.workflow.CommonDao;
import com.anode.workflow.MemoryDao;
import com.anode.workflow.RouteResponseFactory;
import com.anode.workflow.StepResponseFactory;
import com.anode.workflow.TestHandler;
import com.anode.workflow.TestManager;
import com.anode.workflow.TestSlaQueueManager;
import com.anode.workflow.TestWorkManager;
import com.anode.workflow.WorkflowManagerServices;
import com.anode.workflow.WorkflowService;
import com.anode.workflow.entities.sla.Milestone;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.entities.workflows.WorkflowVariables;
import com.anode.workflow.mapper.MilestoneMapper;
import com.anode.workflow.mapper.WorkflowDefinitionMapper;
import com.anode.workflow.service.EventHandler;
import com.anode.workflow.service.SlaQueueManager;
import com.anode.workflow.service.WorkflowComponantFactory;
import com.anode.workflow.service.runtime.RuntimeService;
import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestWorkflowService {

    // NOTE -> while setting the contents of the expected files, do not use the IntelliJ editor
    // When we run the assertEquals method, for some reason, the empty space at the end of the line
    // in the
    // contents of the expected file is being trimmed. This does not happen if we use Notepad++ to
    // save the contents of the expected files

    public static WorkflowContext startCase(
            String caseId, String journeyJson, WorkflowVariables pvs, List<Milestone> journeySla) {
        WorkflowDefinition journey = WorkflowDefinitionMapper.toEntity(new JDocument(journeyJson));
        return rts.startCase(caseId, journey, pvs, journeySla);
    }

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
        String slaJson = null;

        try {
            slaJson =
                    StringUtils.getResourceAsString(
                            TestWorkflowService.class,
                            "/workflow_service/" + journey + "_sla.json");
        } catch (Exception e) {
            // nothing to do
        }

        if (dao.get(Object.class, "workflow_process_info-1.json") == null) {
            startCase(
                    "1",
                    json,
                    null,
                    Objects.nonNull(slaJson)
                            ? MilestoneMapper.toEntities(new JDocument(slaJson))
                            : null);
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

    private static void runJourneyWms(String journey, MemoryDao dao) {
        String json =
                StringUtils.getResourceAsString(
                        TestWorkflowService.class, "/workflow_service/" + journey + ".json");
        String slaJson = null;

        try {
            slaJson =
                    StringUtils.getResourceAsString(
                            TestWorkflowService.class,
                            "/workflow_service/" + journey + "_sla.json");
        } catch (Exception e) {
            // nothing to do
        }

        if (dao.get(Object.class, "workflow_process_info-1.json") == null) {
            startCase(
                    "1",
                    json,
                    null,
                    Objects.nonNull(slaJson)
                            ? MilestoneMapper.toEntities(new JDocument(slaJson))
                            : null);
        }

        WorkflowManagerServices wms =
                WorkflowService.instance()
                        .getWorkManagementService(
                                dao, new TestWorkManager(), new TestSlaQueueManager());
        wms.changeWorkBasket("1", "wb_2");

        rts.resumeCase("1");
    }

    private static void runJourneyWms1(String journey, MemoryDao dao) {
        String json =
                StringUtils.getResourceAsString(
                        TestWorkflowService.class, "/workflow_service/" + journey + ".json");
        String slaJson = null;

        try {
            slaJson =
                    StringUtils.getResourceAsString(
                            TestWorkflowService.class,
                            "/workflow_service/" + journey + "_sla.json");
        } catch (Exception e) {
            // nothing to do
        }

        if (dao.get(Object.class, "workflow_process_info-1.json") == null) {
            startCase(
                    "1",
                    json,
                    null,
                    Objects.nonNull(slaJson)
                            ? MilestoneMapper.toEntities(new JDocument(slaJson))
                            : null);
        }

        WorkflowManagerServices wms =
                WorkflowService.instance()
                        .getWorkManagementService(
                                dao, new TestWorkManager(), new TestSlaQueueManager());
        wms.changeWorkBasket("1", "wb_2");
        wms.changeWorkBasket("1", "wb_3");
        wms.changeWorkBasket("1", "wb_4");
        wms.changeWorkBasket("1", "wb_5");

        rts.resumeCase("1");
    }

    // pend scenario without ticket
    public static void setScenario1() {
        StepResponseFactory.addResponse("step3", StepResponseType.OK_PEND, "comp3_wb", "");
        StepResponseFactory.addResponse("step11", StepResponseType.OK_PEND_EOR, "comp11_wb", "");
        StepResponseFactory.addResponse("step11", StepResponseType.ERROR_PEND, "comp11_err1", "");
        StepResponseFactory.addResponse("step11", StepResponseType.ERROR_PEND, "comp11_err2", "");
        StepResponseFactory.addResponse("step13", StepResponseType.ERROR_PEND, "comp13_err3", "");
        StepResponseFactory.addResponse("step13", StepResponseType.ERROR_PEND, "comp13_err3", "");
        StepResponseFactory.addResponse("step13", StepResponseType.ERROR_PEND, "comp13_err3", "");
        StepResponseFactory.addResponse("step14", StepResponseType.ERROR_PEND, "comp14_wb", "");

        List<String> branches = new ArrayList<>();
        branches.add("yes");
        RouteResponseFactory.addResponse("route2", StepResponseType.OK_PROCEED, branches, null);
        RouteResponseFactory.addResponse("route4", StepResponseType.OK_PROCEED, branches, null);
        RouteResponseFactory.addResponse("route5", StepResponseType.OK_PROCEED, branches, null);
    }

    // scenario with ticket no pend
    public static void setScenario2() {
        StepResponseFactory.addResponse("step16", StepResponseType.OK_PROCEED, "", "final_step");

        List<String> branches = new ArrayList<>();
        branches.add("yes");
        RouteResponseFactory.addResponse("route3", StepResponseType.OK_PROCEED, branches, null);
        RouteResponseFactory.addResponse("route4", StepResponseType.OK_PROCEED, branches, null);
    }

    // scenario with ticket and pend
    public static void setScenario3() {
        StepResponseFactory.addResponse(
                "step16", StepResponseType.OK_PEND, "some_wb", "final_step");

        List<String> branches = new ArrayList<>();
        branches.add("yes");
        RouteResponseFactory.addResponse("route3", StepResponseType.OK_PROCEED, branches, null);
        RouteResponseFactory.addResponse("route4", StepResponseType.OK_PROCEED, branches, null);
    }

    // scenario with ticket and pend eor
    public static void setScenario4() {
        StepResponseFactory.addResponse(
                "step16", StepResponseType.OK_PEND_EOR, "some_wb", "final_step");

        List<String> branches = new ArrayList<>();
        branches.add("yes");
        RouteResponseFactory.addResponse("route3", StepResponseType.OK_PROCEED, branches, null);
        RouteResponseFactory.addResponse("route4", StepResponseType.OK_PROCEED, branches, null);
    }

    // pend scenario to check last pend work basket feature
    public static void setScenario5() {
        StepResponseFactory.addResponse("step3", StepResponseType.OK_PEND, "comp3_wb", "");
        StepResponseFactory.addResponse("step11", StepResponseType.OK_PEND, "comp11_wb", "");
        StepResponseFactory.addResponse("step13", StepResponseType.ERROR_PEND, "tech", "");
        StepResponseFactory.addResponse("step13", StepResponseType.ERROR_PEND, "tech", "");
        StepResponseFactory.addResponse("step13", StepResponseType.ERROR_PEND, "tech", "");
        StepResponseFactory.addResponse("step13", StepResponseType.ERROR_PEND, "tech", "");
        StepResponseFactory.addResponse("step13", StepResponseType.ERROR_PEND, "tech", "");
        StepResponseFactory.addResponse("step14", StepResponseType.ERROR_PEND, "comp14_wb", "");

        List<String> branches = new ArrayList<>();
        branches.add("yes");
        RouteResponseFactory.addResponse("route2", StepResponseType.OK_PROCEED, branches, null);
        RouteResponseFactory.addResponse("route4", StepResponseType.OK_PROCEED, branches, null);
        RouteResponseFactory.addResponse("route5", StepResponseType.OK_PROCEED, branches, null);
    }

    public static void setScenario6() {
        StepResponseFactory.addResponse("step3", StepResponseType.OK_PEND_EOR, "wb_1", "");
        StepResponseFactory.addResponse("step3", StepResponseType.OK_PEND_EOR, "wb_1", "");
    }

    public static void setScenario7() {
        StepResponseFactory.addResponse("step3", StepResponseType.OK_PEND_EOR, "wb_1", "");
    }

    @Test
    void testClean() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        List<String> branches = new ArrayList<>();
        branches.add("yes");
        RouteResponseFactory.addResponse("route2", StepResponseType.OK_PROCEED, branches, null);
        RouteResponseFactory.addResponse("route4", StepResponseType.OK_PROCEED, branches, null);
        RouteResponseFactory.addResponse("route5", StepResponseType.OK_PROCEED, branches, null);
        init(dao, new TestComponentFactory(), new TestHandler(), null);
        runJourney("test_journey", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_clean_expected.txt");
    }

    @Test
    void testScenario1() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario1();
        init(dao, new TestComponentFactory(), new TestHandler(), null);
        runJourney("test_journey", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_scenario_1_expected.txt");
    }

    @Test
    void testScenario1WithSla() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario1();
        init(dao, new TestComponentFactory(), new TestHandler(), new TestSlaQueueManager());
        runJourney("test_journey", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_scenario_1_sla_expected.txt");
    }

    @Test
    void testScenario2() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario2();
        init(dao, new TestComponentFactory(), new TestHandler(), null);
        runJourney("test_journey", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_scenario_2_expected.txt");
    }

    @Test
    void testScenario2WithSla() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario2();
        init(dao, new TestComponentFactory(), new TestHandler(), new TestSlaQueueManager());
        runJourney("test_journey", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_scenario_2_sla_expected.txt");
    }

    @Test
    void testScenario3() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario3();
        init(dao, new TestComponentFactory(), new TestHandler(), null);
        runJourney("test_journey", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_scenario_3_expected.txt");
    }

    @Test
    void testScenario3WithSla() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario3();
        init(dao, new TestComponentFactory(), new TestHandler(), new TestSlaQueueManager());
        runJourney("test_journey", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_scenario_3_sla_expected.txt");
    }

    @Test
    void testScenario4() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario4();
        init(dao, new TestComponentFactory(), new TestHandler(), null);
        runJourney("test_journey", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_scenario_4_expected.txt");
    }

    @Test
    void testScenario4WithSla() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario4();
        init(dao, new TestComponentFactory(), new TestHandler(), new TestSlaQueueManager());
        runJourney("test_journey", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_scenario_4_sla_expected.txt");
    }

    @Test
    void testScenario5() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario5();
        init(dao, new TestComponentFactory(), new TestHandler(), null);
        runJourney("test_journey", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_scenario_5_expected.txt");
    }

    @Test
    void testScenario6() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario6();
        init(dao, new TestComponentFactory(), new TestHandler(), new TestSlaQueueManager());
        runJourneyWms("test_journey_wms", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_scenario_6_expected.txt");
    }

    @Test
    void testScenario7() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        setScenario7();
        init(dao, new TestComponentFactory(), new TestHandler(), new TestSlaQueueManager());
        runJourneyWms1("test_journey_wms", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_scenario_7_expected.txt");
    }

    @Test
    void testPersist() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        init(dao, new TestComponentFactory(), new TestHandler(), new TestSlaQueueManager());
        runJourney("test_persist", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_persist_expected.txt");
    }

    @Test
    void testResume() {
        MemoryDao dao = new MemoryDao();
        String methodName = new Object() {}.getClass().getEnclosingMethod().getName();
        String path = baseDirPath + simpleClassName + "/" + methodName + "/";
        List<String> branches = new ArrayList<>();
        branches.add("yes");
        RouteResponseFactory.addResponse("route2", StepResponseType.OK_PROCEED, branches, null);
        RouteResponseFactory.addResponse("route4", StepResponseType.OK_PROCEED, branches, null);
        RouteResponseFactory.addResponse("route5", StepResponseType.OK_PROCEED, branches, null);
        init(dao, new TestComponentFactory(), new TestHandler(), null);
        runJourney("test_journey", dao);
        TestManager.writeFiles(writeFiles, path, dao.getDocumentMap());
        TestManager.myAssertEquals1(
                writeToConsole,
                simpleClassName + "." + methodName,
                "/workflow_service/test_singular/test_resume_expected.txt");
    }
}
