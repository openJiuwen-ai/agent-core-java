package com.openjiuwen.auto_harness.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verify stage output artifact.
 *
 * <p>Mirrors Python's {@code VerifyReportArtifact} in
 * {@code openjiuwen.auto_harness.schema}.</p>
 */
public class VerifyReportArtifact {
    private Map<String, Object> ciResult = new LinkedHashMap<>();
    private String fixErrors = "";
    private boolean reverted;
    private String error = "";

    public VerifyReportArtifact() {
    }

    public VerifyReportArtifact(Map<String, Object> ciResult) {
        setCiResult(ciResult);
    }

    public Map<String, Object> getCiResult() {
        return ciResult;
    }

    public void setCiResult(Map<String, Object> ciResult) {
        this.ciResult = ciResult != null ? new LinkedHashMap<>(ciResult) : new LinkedHashMap<>();
    }

    public String getFixErrors() {
        return fixErrors;
    }

    public void setFixErrors(String fixErrors) {
        this.fixErrors = fixErrors != null ? fixErrors : "";
    }

    public boolean isReverted() {
        return reverted;
    }

    public void setReverted(boolean reverted) {
        this.reverted = reverted;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error != null ? error : "";
    }
}
