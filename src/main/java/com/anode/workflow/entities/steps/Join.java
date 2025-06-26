package com.anode.workflow.entities.steps;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("JOIN")
public class Join extends Step {

    @Column(name = "next_step")
    private String next = null;

    public Join(String name, String next) {
        super(name, StepType.P_JOIN);
        this.next = next;
    }

    public String getNext() {
        return next;
    }

    @Override
    public String getComponentName() {
        return "join";
    }

    @Override
    public String getUserData() {
        return null;
    }
}
