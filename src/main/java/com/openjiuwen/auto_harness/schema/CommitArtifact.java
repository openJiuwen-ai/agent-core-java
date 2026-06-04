package com.openjiuwen.auto_harness.schema;

/**
 * Commit stage output artifact.
 *
 * <p>Mirrors Python's {@code CommitArtifact} in
 * {@code openjiuwen.auto_harness.schema}.</p>
 */
public class CommitArtifact {
    private CommitFacts facts;
    private String statusText = "";
    private String lastCommitStat = "";
    private String branchName = "";
    private boolean committed;
    private String error = "";

    public CommitFacts getFacts() {
        return facts;
    }

    public void setFacts(CommitFacts facts) {
        this.facts = facts;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText != null ? statusText : "";
    }

    public String getLastCommitStat() {
        return lastCommitStat;
    }

    public void setLastCommitStat(String lastCommitStat) {
        this.lastCommitStat = lastCommitStat != null ? lastCommitStat : "";
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName != null ? branchName : "";
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error != null ? error : "";
    }
}
