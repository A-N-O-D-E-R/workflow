package com.anode.workflow.test_singular;


import com.anode.workflow.FileDao;
import com.anode.workflow.TestHandler;
import com.anode.workflow.TestSlaQueueManager;
import com.anode.workflow.WorkflowService;
import com.anode.workflow.service.runtime.RuntimeService;

public class TestSampleProgram1 {

  private static String dirPath = "./target/test-data-results/";
  private static RuntimeService rts = null;

  public static void main(String[] args) {
    WorkflowService.init(10, 30000, "-");
    foo3();
    WorkflowService.instance().close();
  }

  private static void foo3() {
    rts = WorkflowService.instance().getRunTimeService(new FileDao(dirPath), new TestComponentFactory(), new TestHandler(), new TestSlaQueueManager());
    rts.resumeCase("1");
  }

}
