package com.anode.workflow.entities.steps;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PAUSE")
public class Pause extends Step {

    @Column(name = "next_step")
    private String next = null;

    public Pause(String name, String next) {
        super(name, Step.StepType.PAUSE);
        this.next = next;
    }

    public String getNext() {
        return next;
    }

    @Override
    public String getComponentName() {
        return "pause";
    }

    @Override
    public String getUserData() {
        return null;
    }
}
