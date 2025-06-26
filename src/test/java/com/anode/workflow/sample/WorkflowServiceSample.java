package com.anode.workflow.sample;

import com.anode.tool.StringUtils;
import com.anode.tool.document.JDocument;
import com.anode.workflow.WorkflowService;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.mapper.AbstractMapper;
import com.anode.workflow.mapper.WorkflowDefinitionMapper;
import com.anode.workflow.service.runtime.RuntimeService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowServiceSample {

    // this is windows path - change as required - also change on mac as required
    public static final String DIR_PATH = "C:/temp/workflow_service/";

    public static void main(String[] args) {
        // clear out base directory
        deleteFiles(DIR_PATH);
        AbstractMapper.loadModel();
        WorkflowService.init(10, 30000, "-");
        WorkflowService.instance().setWriteAuditLog(true);
        WorkflowService.instance().setWriteProcessInfoAfterEachStep(true);

        // wire up the workflow_service runtime service
        SampleCommonDao dao = new SampleCommonDao(DIR_PATH);
        SampleComponentFactory factory = new SampleComponentFactory();
        SampleEventHandler handler = new SampleEventHandler();
        RuntimeService rts =
                WorkflowService.instance().getRunTimeService(dao, factory, handler, null);

        // get the process to run
        String json =
                StringUtils.getResourceAsString(
                        WorkflowServiceSample.class, "/workflow_service/sample/order_part.json");

        // start the process - 1 is the case id
        WorkflowDefinition journey = WorkflowDefinitionMapper.toEntity(new JDocument(json));
        rts.startCase("1", journey, null, null);
        // resume if we had pended somewhere till an exception is thrown
        try {
            while (true) {
                log.info("\n");
                rts.resumeCase("1");
            }
        } catch (RuntimeException e) {
            log.error("Exception -> " + e.getMessage());
        }
    }

    public static void deleteFiles(String dirPath) {
        try {
            Files.walk(Paths.get(dirPath))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            log.error("Exception -> " + e.getMessage());
        }
    }
}
