package com.openjiuwen.auto_harness.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors Python's {@code Experience} in {@code openjiuwen.auto_harness.schema}.
 */
public class Experience {

    private ExperienceType type = ExperienceType.OPTIMIZATION;
    private String topic = "";
    private String summary = "";
    private String outcome = "";
    private String details = "";
    private String prUrl = "";
    private List<String> filesChanged = new ArrayList<>();
    private final String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final double timestamp = System.currentTimeMillis() / 1000.0;

    public ExperienceType getType() { return type; }
    public void setType(ExperienceType type) { this.type = type; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getPrUrl() { return prUrl; }
    public void setPrUrl(String prUrl) { this.prUrl = prUrl; }
    public List<String> getFilesChanged() { return filesChanged; }
    public void setFilesChanged(List<String> filesChanged) { this.filesChanged = filesChanged != null ? new ArrayList<>(filesChanged) : new ArrayList<>(); }
    public String getId() { return id; }
    public double getTimestamp() { return timestamp; }
}
