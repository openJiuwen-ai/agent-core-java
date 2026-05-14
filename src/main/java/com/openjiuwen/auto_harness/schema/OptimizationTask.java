package com.openjiuwen.auto_harness.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code OptimizationTask} in {@code openjiuwen.auto_harness.schema}.
 */
public class OptimizationTask {

    private final String topic;
    private String description = "";
    private List<String> files = new ArrayList<>();
    private String issueRef;
    private String expectedEffect = "";
    private String pipelineName = "";
    private TaskStatus status = TaskStatus.PENDING;

    public OptimizationTask(String topic) {
        this.topic = topic;
    }

    public String getTopic() { return topic; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getFiles() { return files; }
    public void setFiles(List<String> files) { this.files = files != null ? new ArrayList<>(files) : new ArrayList<>(); }
    public String getIssueRef() { return issueRef; }
    public void setIssueRef(String issueRef) { this.issueRef = issueRef; }
    public String getExpectedEffect() { return expectedEffect; }
    public void setExpectedEffect(String expectedEffect) { this.expectedEffect = expectedEffect; }
    public String getPipelineName() { return pipelineName; }
    public void setPipelineName(String pipelineName) { this.pipelineName = pipelineName; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
}
