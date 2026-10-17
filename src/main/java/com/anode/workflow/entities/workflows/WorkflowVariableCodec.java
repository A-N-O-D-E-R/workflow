package com.anode.workflow.entities.workflows;

import com.anode.workflow.entities.workflows.WorkflowVariable.WorkflowVariableType;
import com.anode.workflow.exceptions.WorkflowVariableTypeParseException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

/**
 * Encodes a {@link WorkflowVariable} value to a single text column and back, driven by the
 * variable's declared {@link WorkflowVariableType} (the column alone cannot tell an INTEGER from a
 * LONG, nor hold a list).
 *
 * <p>Scalars are stored as their plain text so they stay readable and queryable. Lists and objects
 * are stored as Base64 Java serialization, read back through an {@link ObjectInputFilter} that only
 * admits {@code java.base} classes by default; widen it with the standard {@code jdk.serialFilter}
 * mechanism if you store your own {@code Serializable} types.
 */
final class WorkflowVariableCodec {

    private static final ObjectInputFilter JAVA_BASE_ONLY =
            ObjectInputFilter.Config.createFilter("java.base/*;!*");

    private WorkflowVariableCodec() {}

    static String encode(WorkflowVariableType type, Object value) {
        if (value == null) {
            return null;
        }
        return switch (type) {
            case STRING, BOOLEAN, LONG, INTEGER -> value.toString();
            case OBJECT,
                    LIST_OF_OBJECT,
                    LIST_OF_STRING,
                    LIST_OF_BOOLEAN,
                    LIST_OF_INTEGER,
                    LIST_OF_LONG -> serialize(value);
        };
    }

    static Object decode(WorkflowVariableType type, String stored) {
        if (stored == null) {
            return null;
        }
        return switch (type) {
            case STRING -> stored;
            case BOOLEAN -> Boolean.valueOf(stored);
            case LONG -> Long.valueOf(stored);
            case INTEGER -> Integer.valueOf(stored);
            case OBJECT,
                    LIST_OF_OBJECT,
                    LIST_OF_STRING,
                    LIST_OF_BOOLEAN,
                    LIST_OF_INTEGER,
                    LIST_OF_LONG -> deserialize(stored);
        };
    }

    private static String serialize(Object value) {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        } catch (IOException e) {
            throw new WorkflowVariableTypeParseException("Cannot serialize workflow variable", e);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static Object deserialize(String stored) {
        try (var in = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(stored)))) {
            if (ObjectInputFilter.Config.getSerialFilter() == null) {
                in.setObjectInputFilter(JAVA_BASE_ONLY);
            }
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new WorkflowVariableTypeParseException("Cannot deserialize workflow variable", e);
        }
    }
}
