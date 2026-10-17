package com.anode.workflow.entities.workflows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anode.workflow.entities.workflows.WorkflowVariable.WorkflowVariableType;
import com.anode.workflow.exceptions.WorkflowVariableTypeParseException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class WorkflowVariableCodecTest {

    static List<Arguments> values() {
        return List.of(
                Arguments.of(WorkflowVariableType.STRING, "a,b \"c\""),
                Arguments.of(WorkflowVariableType.BOOLEAN, true),
                Arguments.of(WorkflowVariableType.INTEGER, 42),
                Arguments.of(WorkflowVariableType.LONG, 42L),
                Arguments.of(WorkflowVariableType.LIST_OF_STRING, new ArrayList<>(List.of("x", "y,z"))),
                Arguments.of(WorkflowVariableType.LIST_OF_INTEGER, new ArrayList<>(List.of(1, 2))),
                Arguments.of(WorkflowVariableType.LIST_OF_LONG, new ArrayList<>(List.of(1L, 2L))),
                Arguments.of(WorkflowVariableType.LIST_OF_BOOLEAN, new ArrayList<>(List.of(true, false))),
                Arguments.of(WorkflowVariableType.OBJECT, java.time.Duration.ofMinutes(3)));
    }

    @ParameterizedTest
    @MethodSource("values")
    void roundTripsKeepingTheDeclaredJavaType(WorkflowVariableType type, Object value) {
        Object decoded = WorkflowVariableCodec.decode(type, WorkflowVariableCodec.encode(type, value));

        assertThat(decoded).isEqualTo(value).hasSameClassAs(value);
    }

    @Test
    void scalarsAreStoredAsPlainText() {
        assertThat(WorkflowVariableCodec.encode(WorkflowVariableType.LONG, 7L)).isEqualTo("7");
    }

    @Test
    void nullStaysNull() {
        assertThat(WorkflowVariableCodec.encode(WorkflowVariableType.STRING, null)).isNull();
        assertThat(WorkflowVariableCodec.decode(WorkflowVariableType.OBJECT, null)).isNull();
    }

    record Gadget(String payload) implements Serializable {}

    @Test
    void refusesToDeserializeClassesOutsideJavaBase() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(new Gadget("x"));
        }
        String stored = Base64.getEncoder().encodeToString(bytes.toByteArray());

        assertThatThrownBy(() -> WorkflowVariableCodec.decode(WorkflowVariableType.OBJECT, stored))
                .isInstanceOf(WorkflowVariableTypeParseException.class);
    }
}
