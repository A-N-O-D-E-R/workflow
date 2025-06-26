package com.anode.workflow.entities.steps;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PERSIST")
public class Persist extends Step {

    @Column(name = "next_step")
    private String next = null;

    public Persist(String name, String next) {
        super(name, Step.StepType.PERSIST);
        this.next = next;
    }

    public String getNext() {
        return next;
    }

    @Override
    public String getComponentName() {
        return "persist";
    }

    @Override
    public String getUserData() {
        return null;
    }
}
