package com.anode.workflow.entities.workflows;

import com.anode.workflow.exceptions.WorkflowVariableTypeParseException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "workflow_variable")
public class WorkflowVariable implements Serializable {

    @Id private Long hibid;

    private String name = null;

    @Transient private volatile Object value = null;

    /** Persistent form of {@link #value}, encoded according to {@link #type}. */
    @Column(name = "value", nullable = true, columnDefinition = "text")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String storedValue = null;

    @Enumerated(EnumType.STRING)
    private WorkflowVariableType type = null;

    private String comment = "";

    /** For JPA only. */
    protected WorkflowVariable() {}

    public WorkflowVariable(String name, WorkflowVariableType type, Object value) {
        validate(type, value);
        this.name = name;
        this.value = value;
        this.type = type;
    }

    @PrePersist
    @PreUpdate
    void encodeValue() {
        storedValue = WorkflowVariableCodec.encode(type, value);
    }

    @PostLoad
    void decodeValue() {
        value = WorkflowVariableCodec.decode(type, storedValue);
    }

    private void validate(WorkflowVariableType type, Object value) {
        switch (type) {
            case BOOLEAN:
                {
                    if ((value instanceof Boolean) == false) {
                        throw new WorkflowVariableTypeParseException(
                                "Value object does not conform to process variable data type ->"
                                        + " BOOLEAN");
                    }
                    break;
                }

            case LONG:
                {
                    if ((value instanceof Long) == false) {
                        throw new WorkflowVariableTypeParseException(
                                "Value object does not conform to process variable data type ->"
                                        + " LONG");
                    }
                    break;
                }

            case INTEGER:
                {
                    if ((value instanceof Integer) == false) {
                        throw new WorkflowVariableTypeParseException(
                                "Value object does not conform to process variable data type ->"
                                        + " INTEGER");
                    }
                    break;
                }

            case STRING:
                {
                    if ((value instanceof String) == false) {
                        throw new WorkflowVariableTypeParseException(
                                "Value object does not conform to process variable data type ->"
                                        + " STRING");
                    }
                    break;
                }
            case OBJECT:
                {
                    if ((value instanceof Serializable) == false) {
                        throw new WorkflowVariableTypeParseException(
                                "Value object does not conform to process variable requirement ->"
                                        + " OBJECT should be Serializable");
                    }
                    break;
                }
            case LIST_OF_OBJECT:
                {
                    if ((value instanceof List) == false) {
                        throw new WorkflowVariableTypeParseException(
                                "Value object does not conform to process variable rdata type ->"
                                        + " LIST<OBJECT>");
                    } else {
                        for (Object element : (List<?>) value) {
                            if (!(element instanceof Serializable)) {
                                throw new WorkflowVariableTypeParseException(
                                        "Value object does not conform to process variable rdata"
                                                + " type -> LIST<OBJECT>");
                            }
                        }
                    }
                    break;
                }
            case LIST_OF_STRING:
                {
                    if ((value instanceof List) == false) {
                        throw new WorkflowVariableTypeParseException(
                                "Value object does not conform to process variable rdata type ->"
                                        + " LIST<STRING>");
                    } else {
                        for (Object element : (List<?>) value) {
                            if (!(element instanceof String)) {
                                throw new WorkflowVariableTypeParseException(
                                        "Value object does not conform to process variable rdata"
                                                + " type -> LIST<STRING>");
                            }
                        }
                    }
                    break;
                }
            case LIST_OF_BOOLEAN:
                {
                    if ((value instanceof List) == false) {
                        throw new WorkflowVariableTypeParseException(
                                "Value object does not conform to process variable rdata type ->"
                                        + " LIST<BOOLEAN>");
                    } else {
                        for (Object element : (List<?>) value) {
                            if (!(element instanceof Boolean)) {
                                throw new WorkflowVariableTypeParseException(
                                        "Value object does not conform to process variable rdata"
                                                + " type -> LIST<BOOLEAN>");
                            }
                        }
                    }
                    break;
                }
            case LIST_OF_INTEGER:
                {
                    if ((value instanceof List) == false) {
                        throw new WorkflowVariableTypeParseException(
                                "Value object does not conform to process variable rdata type ->"
                                        + " LIST<INTEGER>");
                    } else {
                        for (Object element : (List<?>) value) {
                            if (!(element instanceof Integer)) {
                                throw new WorkflowVariableTypeParseException(
                                        "Value object does not conform to process variable rdata"
                                                + " type -> LIST<INTEGER>");
                            }
                        }
                    }
                    break;
                }
            case LIST_OF_LONG:
                {
                    if ((value instanceof List) == false) {
                        throw new WorkflowVariableTypeParseException(
                                "Value object does not conform to process variable rdata type ->"
                                        + " LIST<LONG>");
                    } else {
                        for (Object element : (List<?>) value) {
                            if (!(element instanceof Long)) {
                                throw new WorkflowVariableTypeParseException(
                                        "Value object does not conform to process variable rdata"
                                                + " type -> LIST<LONG>");
                            }
                        }
                    }
                    break;
                }
        }
    }

    public enum WorkflowVariableType {
        STRING,
        BOOLEAN,
        LONG,
        INTEGER,
        OBJECT,
        LIST_OF_OBJECT,
        LIST_OF_STRING,
        LIST_OF_BOOLEAN,
        LIST_OF_INTEGER,
        LIST_OF_LONG
    }
}
