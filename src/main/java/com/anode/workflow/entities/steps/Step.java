package com.anode.workflow.entities.steps;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.Getter;

import java.io.Serializable;

@Entity
@Getter
@Table(name = "step")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "step_class", discriminatorType = DiscriminatorType.STRING)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type") // Tells Jackson to add type info to the JSON
@JsonSubTypes({
    @JsonSubTypes.Type(value = Task.class, name = "TASK"),
    @JsonSubTypes.Type(value = Pause.class, name = "PAUSE"),
    @JsonSubTypes.Type(value = Persist.class, name = "PERSIST"),
    @JsonSubTypes.Type(value = Join.class, name = "P_JOIN"),
    @JsonSubTypes.Type(value = Route.class, name = "P_ROUTE"),
    @JsonSubTypes.Type(value = Route.class, name = "S_ROUTE"),
    @JsonSubTypes.Type(value = Route.class, name = "P_ROUTE_DYNAMIC")
})
public abstract class Step implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long hibid;

    @Column(name = "name", nullable = false)
    private String name = null;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private StepType type = null;

    protected Step(String name, StepType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public StepType getType() {
        return type;
    }

    public abstract String getComponentName();

    public abstract String getUserData();

    public enum StepType {
        TASK,
        PAUSE,
        S_ROUTE,
        P_ROUTE,
        P_ROUTE_DYNAMIC,
        P_JOIN,
        PERSIST
    }
}
