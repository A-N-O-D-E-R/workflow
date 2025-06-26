package com.anode.workflow.sample;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.io.IOException;

public class SchemaGenerator {
    public static void main(String[] args) throws ClassNotFoundException, IOException {
        EntityManagerFactory entityManagerFactory =
                Persistence.createEntityManagerFactory("WorkflowUnit");
        entityManagerFactory.close();
    }
}
