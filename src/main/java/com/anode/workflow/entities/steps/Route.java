package com.anode.workflow.entities.steps;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import java.util.HashMap;
import java.util.Map;

@Entity
@DiscriminatorValue("ROUTE")
public class Route extends Step {

    @ElementCollection
    @CollectionTable(name = "route_branches", joinColumns = @JoinColumn(name = "route_id"))
    @MapKeyColumn(name = "branch_key")
    @Column(name = "branch_value")
    private Map<String, Branch> branches = new HashMap<>();

    @Column(name = "componant_name")
    private String componentName = null;

    @Column(name = "user_data")
    private String userData = null;

    @Column(name = "next_step")
    private String next = null;

    public Route(
            String name,
            String componentName,
            String userData,
            Map<String, Branch> branches,
            StepType type) {
        super(name, type);
        this.branches = branches;
        this.componentName = componentName;
        this.userData = userData;
    }

    public Route(String name, String componentName, String userData, String next, StepType type) {
        super(name, type);
        this.next = next;
        this.componentName = componentName;
        this.userData = userData;
    }

    public Branch getBranch(String name) {
        return branches.get(name);
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    public String getUserData() {
        return userData;
    }

    public String getNext() {
        return next;
    }
}
