package com.openjiuwen.auto_harness.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code ResearchContext} in {@code openjiuwen.auto_harness.schema}.
 */
public class ResearchContext {

    private List<Experience> experiences = new ArrayList<>();
    private Map<String, String> sourceFiles = new LinkedHashMap<>();
    private String gapReport;

    public List<Experience> getExperiences() { return experiences; }
    public void setExperiences(List<Experience> experiences) { this.experiences = experiences != null ? new ArrayList<>(experiences) : new ArrayList<>(); }
    public Map<String, String> getSourceFiles() { return sourceFiles; }
    public void setSourceFiles(Map<String, String> sourceFiles) { this.sourceFiles = sourceFiles != null ? new LinkedHashMap<>(sourceFiles) : new LinkedHashMap<>(); }
    public String getGapReport() { return gapReport; }
    public void setGapReport(String gapReport) { this.gapReport = gapReport; }
}
