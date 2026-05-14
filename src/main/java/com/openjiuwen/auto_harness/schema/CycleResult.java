package com.openjiuwen.auto_harness.schema;

/**
 * Mirrors Python's {@code CycleResult} in {@code openjiuwen.auto_harness.schema}.
 */
public class CycleResult {

    private boolean success;
    private String summary = "";
    private String prUrl = "";
    private String error = "";
    private boolean reverted;
    private String errorLog = "";

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getPrUrl() { return prUrl; }
    public void setPrUrl(String prUrl) { this.prUrl = prUrl; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public boolean isReverted() { return reverted; }
    public void setReverted(boolean reverted) { this.reverted = reverted; }
    public String getErrorLog() { return errorLog; }
    public void setErrorLog(String errorLog) { this.errorLog = errorLog; }
}
