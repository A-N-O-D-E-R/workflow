package com.anode.workflow.entities.workflows.paths;

import com.anode.tool.StringUtils;
import com.anode.workflow.entities.steps.responses.StepResponseType;
import com.anode.workflow.service.ErrorHandler;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class ExecPath {

    private String name = ".";

    // the status is started if the thread corresponding to this exec path is running
    // if the thread terminates then the status will be marked as complete
    @Enumerated(EnumType.STRING)
    private ExecPathStatus status = ExecPathStatus.STARTED;

    // this contains the start step when we start / resume the process
    // during the process this contains the last executed step
    private String step = "";

    // this contains the name of the ticket raised by this execpath
    private String ticket = "";

    // this contains the name of the workbasket in case a pend has occurred
    private String pendWorkBasket = "";

    // this contains the name of the previous workbasket in case of a pend
    private String prevPendWorkBasket = "";

    // To Be Cleared sla work basket. This will contain the name of the work basket for which SLA
    // milestones
    // are to be cleared in case we receive an ok_pend_eor response and there is an error_pend after
    // that. Then
    // when the application is resumed, we would like the original work basket where the application
    // had
    // pended to fire the dequeue SLA when the error is cleared
    private String tbcSlaWorkBasket = "";

    // this contains the response type return from the last step or route executed by this execution
    // path
    @Enumerated(EnumType.STRING)
    private StepResponseType stepResponseType = null;

    private ErrorHandler pendError = new ErrorHandler();

    public ExecPath(String name) {
        this.name = name;
    }

    public void set(ExecPathStatus status, String step, StepResponseType stepResponseType) {
        this.status = status;
        this.step = step;
        this.stepResponseType = stepResponseType;
    }

    public void set(String step, StepResponseType stepResponseType) {
        this.step = step;
        this.stepResponseType = stepResponseType;
    }

    public boolean isSibling(ExecPath ep) {
        int i = StringUtils.getCount(name, '.');
        int j = StringUtils.getCount(ep.getName(), '.');
        if (i == j) {
            return true;
        } else {
            return false;
        }
    }

    public String getParentExecPathName() {
        String ppn = ".";

        // get number of dots in the execpath
        int num = StringUtils.getCount(name, '.');

        int num1 = num - 2;
        if (num1 > 0) {
            int index = StringUtils.getIndexOfChar(name, '.', num1, true);
            ppn = name.substring(0, index + 1);
        }

        return ppn;
    }

    public enum ExecPathStatus {
        STARTED,
        COMPLETED
    }
}
