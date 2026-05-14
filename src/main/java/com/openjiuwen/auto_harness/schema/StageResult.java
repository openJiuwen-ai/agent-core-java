package com.openjiuwen.auto_harness.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code StageResult} in {@code openjiuwen.auto_harness.schema}.
 */
public class StageResult {
    private String status = "success";
    private Map<String, Object> artifacts = new LinkedHashMap<>();
    private List<String> messages = new ArrayList<>();
    private Map<String, Object> metrics = new LinkedHashMap<>();
    private String error = "";

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Map<String, Object> getArtifacts() { return artifacts; }
    public void setArtifacts(Map<String, Object> artifacts) { this.artifacts = artifacts != null ? new LinkedHashMap<>(artifacts) : new LinkedHashMap<>(); }
    public List<String> getMessages() { return messages; }
    public void setMessages(List<String> messages) { this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>(); }
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics != null ? new LinkedHashMap<>(metrics) : new LinkedHashMap<>(); }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
