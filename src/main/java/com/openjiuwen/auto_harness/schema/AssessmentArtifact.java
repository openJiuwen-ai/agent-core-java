package com.openjiuwen.auto_harness.schema;

/**
 * Assess-stage structured artifact.
 *
 * <p>Mirrors Python's {@code AssessmentArtifact} in
 * {@code openjiuwen.auto_harness.schema}.</p>
 */
public class AssessmentArtifact {

    private String report = "";

    public AssessmentArtifact() {
    }

    public AssessmentArtifact(String report) {
        this.report = report != null ? report : "";
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report != null ? report : "";
    }
}
