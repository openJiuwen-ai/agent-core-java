package com.openjiuwen.auto_harness.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code Gap} in {@code openjiuwen.auto_harness.schema}.
 */
public class Gap {

    private String id = "";
    private String competitor = "";
    private String feature = "";
    private String currentState = "";
    private String gapDescription = "";
    private double impact;
    private double feasibility;
    private String suggestedApproach = "";
    private List<String> targetFiles = new ArrayList<>();

    public double getPriority() {
        return impact * feasibility;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCompetitor() { return competitor; }
    public void setCompetitor(String competitor) { this.competitor = competitor; }
    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }
    public String getCurrentState() { return currentState; }
    public void setCurrentState(String currentState) { this.currentState = currentState; }
    public String getGapDescription() { return gapDescription; }
    public void setGapDescription(String gapDescription) { this.gapDescription = gapDescription; }
    public double getImpact() { return impact; }
    public void setImpact(double impact) { this.impact = impact; }
    public double getFeasibility() { return feasibility; }
    public void setFeasibility(double feasibility) { this.feasibility = feasibility; }
    public String getSuggestedApproach() { return suggestedApproach; }
    public void setSuggestedApproach(String suggestedApproach) { this.suggestedApproach = suggestedApproach; }
    public List<String> getTargetFiles() { return targetFiles; }
    public void setTargetFiles(List<String> targetFiles) { this.targetFiles = targetFiles != null ? new ArrayList<>(targetFiles) : new ArrayList<>(); }
}
