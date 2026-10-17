package com.anode.workflow.entities.workflows;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anode.workflow.entities.steps.Branch;
import com.anode.workflow.entities.steps.Route;
import com.anode.workflow.entities.steps.Step.StepType;
import com.anode.workflow.entities.steps.Task;
import com.anode.workflow.entities.tickets.Ticket;
import com.anode.workflow.exceptions.WorkflowRuntimeException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionValidationTest {

    private static WorkflowDefinition definition() {
        WorkflowDefinition d = new WorkflowDefinition();
        d.setName("t");
        return d;
    }

    @Test
    void aWellFormedDefinitionIsValid() {
        WorkflowDefinition d = definition();
        d.addStep(new Task("start", "c1", "decide", null));
        d.addStep(new Route("decide", "r", null,
                Map.of("yes", new Branch("yes", "done"), "no", new Branch("no", "end")), StepType.S_ROUTE));
        d.addStep(new Task("done", "c2", "end", null));
        d.setTicket(new Ticket("reset", "start"));

        assertThatCode(d::validate).doesNotThrowAnyException();
    }

    @Test
    void danglingReferencesAreAllReported() {
        WorkflowDefinition d = definition();
        d.addStep(new Task("first", "c1", "missing", null));
        d.addStep(new Route("decide", "r", null, Map.of("yes", new Branch("yes", "nowhere")), StepType.S_ROUTE));
        d.setTicket(new Ticket("reset", "gone"));

        assertThatThrownBy(d::validate)
                .isInstanceOf(WorkflowRuntimeException.class)
                .hasMessageContaining("no step named 'start'")
                .hasMessageContaining("step first -> 'missing'")
                .hasMessageContaining("branch decide.yes -> 'nowhere'")
                .hasMessageContaining("ticket reset -> 'gone'");
    }
}
