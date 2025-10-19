package com.anode.workflow.storage;

import com.anode.tool.StringUtils;

public class StorageProviderUtils {
 private StorageProviderUtils() {
    }

    public static String elementPrefixer(String prefix, String element) {
        return StringUtils.isNullOrEmpty(prefix) ? element : prefix + element;
    }

    public enum DatabaseOptions {
        CREATE,
        SKIP_CREATE,
        NO_VALIDATE
    }

    private static final String FIELD_ID = "id";

    public static final class Migrations {
        private Migrations() {
        }
        public static final String NAME = "migrations";
        public static final String FIELD_ID = StorageProviderUtils.FIELD_ID;
        public static final String FIELD_NAME = "name";
        public static final String FIELD_DATE = "date";
    }

    public static final class Workflow {
        private Workflow() {
        }
        public static final String NAME = "workflows";
        public static final String FIELD_ID = StorageProviderUtils.FIELD_ID;
        public static final String FIELD_VERSION = "version";
        public static final String FIELD_STATE = "state";
        public static final String FIELD_JOB_AS_JSON = "workflowAsJson";
        public static final String FIELD_JOB_SIGNATURE = "workflowSignature";
        public static final String FIELD_CREATED_AT = "createdAt";
        public static final String FIELD_UPDATED_AT = "updatedAt";
    }

}
