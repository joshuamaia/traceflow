package dev.traceflow.analysis.domain;

public enum AnalysisStatus {
    PENDING, PROCESSING, COMPLETED, FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
