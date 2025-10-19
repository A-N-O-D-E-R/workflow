package com.anode.workflow.storage.db.sql.common;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DatabaseCreator {
    private static final String[] WORKFLOW_DEF_TABLES = new String[]{ "workflow", "variable","step", "workflow_steps", "workflow_variables"};
    private static final String[] WORKFLOW_EXECUTION_TABLES = new String[]{ "workflow", "variable","step", "workflow_steps", "workflow_variables"};
    

}
