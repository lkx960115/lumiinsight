package com.lumiinsight.modules.pipeline;

public enum JobStatus {
    PENDING,
    IMPORTING,
    CLEANING,
    ANALYZING,
    INDEXING,
    REPORTING,
    READY,
    FAILED;

    public static boolean isRunning(String status) {
        return PENDING.name().equals(status)
                || IMPORTING.name().equals(status)
                || CLEANING.name().equals(status)
                || ANALYZING.name().equals(status)
                || INDEXING.name().equals(status)
                || REPORTING.name().equals(status);
    }
}
