package com.anode.workflow.test_singular;

import com.anode.tool.StringUtils;
import com.anode.tool.document.JDocument;
import com.anode.tool.service.CommonService;
import com.anode.workflow.FileDao;
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
import java.io.File;
import java.util.List;

public class TestSampleProgram {

    private static String dirPath = "./target/test-data-results/";
    private static RuntimeService rts = null;

    public static WorkflowContext startCase(
            String caseId, String journeyJson, WorkflowVariables pvs, List<Milestone> journeySla) {
        WorkflowDefinition journey = WorkflowDefinitionMapper.toEntity(new JDocument(journeyJson));
        return rts.startCase(caseId, journey, pvs, journeySla);
    }

    public static void main(String[] args) {
        File directory = new File(dirPath);
        if (!directory.exists()) {
            directory.mkdir();
        }
        TestManager.deleteFiles(dirPath);

        WorkflowService.init(10, 30000, "-");
        StepResponseFactory.clear();
        RouteResponseFactory.clear();

        // foo1("test_journey_wms");
        // foo2("test_journey_wms");
        foo3("test_journey_1");

        WorkflowService.instance().close();
    }

    private static void foo1(String journey) {
        setScenario1();
        init(
                new FileDao(dirPath),
                new TestComponentFactory(),
                new TestHandler(),
                new TestSlaQueueManager());
        runJourneyWithWms1(journey);
    }

    private static void foo2(String journey) {
        setScenario2();
        init(
                new FileDao(dirPath),
                new TestComponentFactory(),
                new TestHandler(),
                new TestSlaQueueManager());
        runJourneyWithWms2(journey);
    }

    private static void foo3(String journey) {
        setScenario3();
        init(
                new FileDao(dirPath),
                new TestComponentFactory(),
                new TestHandler(),
                new TestSlaQueueManager());
        runJourneyWithoutWms(journey);
    }

    private static void runJourneyWithWms1(String journey) {
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

        if (new File(dirPath + "workflow_process_info-1.json").exists() == false) {
            startCase("1", json, null, MilestoneMapper.toEntities(new JDocument(slaJson)));
        }

        WorkflowManagerServices wms =
                WorkflowService.instance()
                        .getWorkManagementService(
                                new FileDao(dirPath),
                                new TestWorkManager(),
                                new TestSlaQueueManager());
        wms.changeWorkBasket("1", "wb_2");

        rts.resumeCase("1");
    }

    private static void runJourneyWithWms2(String journey) {
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

        if (new File(dirPath + "workflow_process_info-1.json").exists() == false) {
            startCase("1", json, null, MilestoneMapper.toEntities(new JDocument(slaJson)));
        }

        WorkflowManagerServices wms =
                WorkflowService.instance()
                        .getWorkManagementService(
                                new FileDao(dirPath),
                                new TestWorkManager(),
                                new TestSlaQueueManager());
        wms.changeWorkBasket("1", "wb_2");
        wms.changeWorkBasket("1", "wb_3");
        wms.changeWorkBasket("1", "wb_4");
        wms.changeWorkBasket("1", "wb_5");

        rts.resumeCase("1");
    }

    private static void runJourneyWithoutWms(String journey) {
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

        if (new File(dirPath + "workflow_process_info-1.json").exists() == false) {
            startCase("1", json, null, MilestoneMapper.toEntities(new JDocument(slaJson)));
        }
    }

    private static void init(
            CommonService dao,
            WorkflowComponantFactory factory,
            EventHandler handler,
            SlaQueueManager sqm) {
        rts = WorkflowService.instance().getRunTimeService(dao, factory, handler, sqm);
    }

    private static void setScenario1() {
        StepResponseFactory.addResponse("step3", StepResponseType.OK_PEND_EOR, "wb_1", "");
        StepResponseFactory.addResponse("step3", StepResponseType.OK_PEND_EOR, "wb_1", "");
    }

    private static void setScenario2() {
        StepResponseFactory.addResponse("step3", StepResponseType.OK_PEND_EOR, "wb_1", "");
    }

    public static void setScenario3() {
        StepResponseFactory.addResponse("step3", StepResponseType.OK_PEND_EOR, "wb_1", "");
    }
}
