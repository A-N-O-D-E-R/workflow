package com.anode.workflow.exceptions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ErrorMap {
    protected static Map<Integer, String> errors = new ConcurrentHashMap<>();

    public static final String getErrorMessage(Integer code) {
        String s = errors.get(code);
        s = (s == null) ? "" : s;
        return s;
    }
}
