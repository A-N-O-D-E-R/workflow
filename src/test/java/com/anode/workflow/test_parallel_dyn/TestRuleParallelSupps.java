

package com.anode.workflow.test_parallel_dyn;



import java.util.ArrayList;
import java.util.List;

import com.anode.tool.StringUtils;
import com.anode.workflow.entities.steps.InvokableRoute;
import com.anode.workflow.entities.steps.responses.RouteResponse;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.entities.workflows.WorkflowContext;
import com.anode.workflow.entities.workflows.WorkflowVariables;
import com.anode.workflow.entities.workflows.WorkflowVariable.WorkflowVariableType;


public class TestRuleParallelSupps implements InvokableRoute {

  private String name = null;
  private WorkflowContext pc = null;

  public TestRuleParallelSupps(WorkflowContext pc) {
    this.name = pc.getCompName();
    this.pc = pc;
  }

  public String getName() {
    return name;
  }

  public RouteResponse executeRoute() {
    List<String> branches = new ArrayList<>();
    RouteResponse resp = null;
    String name = pc.getCompName();
    WorkflowVariables pvs = pc.getProcessVariables();
    String execPathPvName = "supp_exec_path_name";
    String processSuppsPvName = "process_supps";

    while (true) {
      if (StringUtils.compareWithMany(name, "route_0")) {
        Boolean processSupps = pvs.getBoolean(processSuppsPvName);
        if (processSupps == null) {
          pvs.setValue(processSuppsPvName, WorkflowVariableType.BOOLEAN, true);
          branches.add("yes");
        }
        else {
          pvs.setValue(processSuppsPvName, WorkflowVariableType.BOOLEAN, false);
          branches.add("no");
        }

        break;
      }

      if (StringUtils.compareWithMany(name, "route_1_c")) {
        pvs.setValue(execPathPvName, WorkflowVariableType.STRING, pc.getExecPathName());

        // simulate 5 supps
        branches.add("ai_index_1");
        branches.add("ai_index_2");
        branches.add("ai_index_3");
        branches.add("ai_index_4");
        branches.add("ai_index_5");

        break;
      }

      break;
    }

    resp = new RouteResponse(StepResponseType.OK_PROCEED, branches, null);

    return resp;
  }

}
