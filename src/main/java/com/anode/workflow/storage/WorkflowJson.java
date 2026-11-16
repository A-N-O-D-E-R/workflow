package com.anode.workflow.storage;

import com.anode.workflow.entities.sla.Milestone;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.entities.workflows.WorkflowInfo;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.persistence.Transient;
import java.util.List;

/**
 * JSON form of everything the engine persists through its DAO, for stores that keep text (a
 * database column, a file, a cache) rather than live Java objects.
 *
 * <p>The engine writes four kinds of objects: the {@link WorkflowDefinition} of a case, its {@link
 * WorkflowInfo} (execution state), its SLA as a list of {@link Milestone}s, and audit-log entries
 * (JSON strings). {@link #write} wraps each in an envelope naming its kind; {@link #read} only
 * rebuilds those four kinds, so a stored document can never make the reader instantiate an
 * arbitrary class.
 */
public final class WorkflowJson {

    /** Kinds of persisted objects. */
    public enum Kind {
        DEFINITION,
        INFO,
        SLA,
        TEXT
    }

    /**
     * Field-based: the entities expose helper getters (setters objects, locks, derived values) that
     * are not state. Fields marked {@code transient} or JPA {@code @Transient} are runtime-only.
     */
    private static final ObjectMapper MAPPER =
            JsonMapper.builder()
                    .visibility(PropertyAccessor.ALL, Visibility.NONE)
                    .visibility(PropertyAccessor.FIELD, Visibility.ANY)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .annotationIntrospector(
                            new JacksonAnnotationIntrospector() {
                                @Override
                                public boolean hasIgnoreMarker(AnnotatedMember m) {
                                    return m.hasAnnotation(Transient.class) || super.hasIgnoreMarker(m);
                                }
                            })
                    .build();

    private static final JavaType SLA_TYPE =
            MAPPER.getTypeFactory().constructCollectionType(List.class, Milestone.class);

    private WorkflowJson() {}

    /** The kind the engine uses for {@code value}. */
    public static Kind kindOf(Object value) {
        return switch (value) {
            case WorkflowDefinition d -> Kind.DEFINITION;
            case WorkflowInfo i -> Kind.INFO;
            case List<?> l -> Kind.SLA;
            case String s -> Kind.TEXT;
            default ->
                    throw new IllegalArgumentException(
                            "Not a workflow object: " + value.getClass().getName());
        };
    }

    /** Serializes {@code value} into a self-describing JSON document. */
    public static String write(Object value) {
        var envelope = MAPPER.createObjectNode();
        envelope.put("kind", kindOf(value).name());
        envelope.set("value", MAPPER.valueToTree(value));
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize " + value.getClass().getName(), e);
        }
    }

    /**
     * Rebuilds a value written by {@link #write}. {@code requested} is the type the engine asks
     * for; {@code Object.class} returns whatever was stored.
     */
    public static <T> T read(String json, Class<T> requested) {
        try {
            JsonNode envelope = MAPPER.readTree(json);
            Kind kind = Kind.valueOf(envelope.required("kind").asText());
            JsonNode value = envelope.required("value");
            Object result =
                    switch (kind) {
                        case DEFINITION -> MAPPER.treeToValue(value, WorkflowDefinition.class);
                        case INFO -> MAPPER.treeToValue(value, WorkflowInfo.class);
                        case SLA -> MAPPER.treeToValue(value, SLA_TYPE);
                        case TEXT -> value.asText();
                    };
            return requested.cast(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read a workflow document", e);
        }
    }
}
