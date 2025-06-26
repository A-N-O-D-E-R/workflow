package com.anode.workflow.entities.steps;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "route_branch")
public class Branch implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long hibid;

    @Column private String name = null;
    @Column private String next = null;

    protected String getName() {
        return name;
    }

    public String getNext() {
        return next;
    }

    public Branch(String name, String next) {
        this.name = name;
        this.next = next;
    }
}
