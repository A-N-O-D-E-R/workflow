package com.anode.workflow.entities.steps;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("TASK")
public class Task extends Step {

    @Column(name = "next_step")
    private String next = null;

    @Column(name = "component_name")
    private String componentName = null;

    @Column(name = "user_data")
    private String userData = null;

    public Task(String name, String componentName, String next, String userData) {
        super(name, Step.StepType.TASK);
        this.next = next;
        this.componentName = componentName;
        this.userData = userData;
    }

    public String getNext() {
        return next;
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    public String getUserData() {
        return userData;
    }
}
