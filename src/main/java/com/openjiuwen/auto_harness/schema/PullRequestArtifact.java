package com.openjiuwen.auto_harness.schema;

/**
 * Publish PR stage output artifact.
 *
 * <p>Mirrors Python's {@code PullRequestArtifact} in
 * {@code openjiuwen.auto_harness.schema}.</p>
 */
public class PullRequestArtifact {
    private String prUrl = "";
    private String summary = "";

    public PullRequestArtifact() {
    }

    public PullRequestArtifact(String prUrl, String summary) {
        setPrUrl(prUrl);
        setSummary(summary);
    }

    public String getPrUrl() {
        return prUrl;
    }

    public void setPrUrl(String prUrl) {
        this.prUrl = prUrl != null ? prUrl : "";
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary != null ? summary : "";
    }
}
