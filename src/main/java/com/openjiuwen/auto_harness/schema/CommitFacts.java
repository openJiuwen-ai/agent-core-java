package com.openjiuwen.auto_harness.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of facts produced by the commit stage.
 *
 * <p>Mirrors Python's {@code CommitFacts} in
 * {@code openjiuwen.auto_harness.schema}.</p>
 */
public class CommitFacts {
    private String branchName = "";
    private List<String> taskDeclaredFiles = new ArrayList<>();
    private List<String> preexistingDirtyFiles = new ArrayList<>();
    private List<String> currentDirtyFiles = new ArrayList<>();
    private List<String> trackedModifiedFiles = new ArrayList<>();
    private List<String> untrackedFiles = new ArrayList<>();
    private List<String> editedFiles = new ArrayList<>();
    private List<String> allowedFiles = new ArrayList<>();
    private List<String> derivedTestFiles = new ArrayList<>();
    private List<String> legacyRelatedTestFiles = new ArrayList<>();
    private List<String> verifyRelatedFiles = new ArrayList<>();
    private String diffStat = "";

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName != null ? branchName : "";
    }

    public List<String> getTaskDeclaredFiles() {
        return taskDeclaredFiles;
    }

    public void setTaskDeclaredFiles(List<String> taskDeclaredFiles) {
        this.taskDeclaredFiles = copy(taskDeclaredFiles);
    }

    public List<String> getPreexistingDirtyFiles() {
        return preexistingDirtyFiles;
    }

    public void setPreexistingDirtyFiles(List<String> preexistingDirtyFiles) {
        this.preexistingDirtyFiles = copy(preexistingDirtyFiles);
    }

    public List<String> getCurrentDirtyFiles() {
        return currentDirtyFiles;
    }

    public void setCurrentDirtyFiles(List<String> currentDirtyFiles) {
        this.currentDirtyFiles = copy(currentDirtyFiles);
    }

    public List<String> getTrackedModifiedFiles() {
        return trackedModifiedFiles;
    }

    public void setTrackedModifiedFiles(List<String> trackedModifiedFiles) {
        this.trackedModifiedFiles = copy(trackedModifiedFiles);
    }

    public List<String> getUntrackedFiles() {
        return untrackedFiles;
    }

    public void setUntrackedFiles(List<String> untrackedFiles) {
        this.untrackedFiles = copy(untrackedFiles);
    }

    public List<String> getEditedFiles() {
        return editedFiles;
    }

    public void setEditedFiles(List<String> editedFiles) {
        this.editedFiles = copy(editedFiles);
    }

    public List<String> getAllowedFiles() {
        return allowedFiles;
    }

    public void setAllowedFiles(List<String> allowedFiles) {
        this.allowedFiles = copy(allowedFiles);
    }

    public List<String> getDerivedTestFiles() {
        return derivedTestFiles;
    }

    public void setDerivedTestFiles(List<String> derivedTestFiles) {
        this.derivedTestFiles = copy(derivedTestFiles);
    }

    public List<String> getLegacyRelatedTestFiles() {
        return legacyRelatedTestFiles;
    }

    public void setLegacyRelatedTestFiles(List<String> legacyRelatedTestFiles) {
        this.legacyRelatedTestFiles = copy(legacyRelatedTestFiles);
    }

    public List<String> getVerifyRelatedFiles() {
        return verifyRelatedFiles;
    }

    public void setVerifyRelatedFiles(List<String> verifyRelatedFiles) {
        this.verifyRelatedFiles = copy(verifyRelatedFiles);
    }

    public String getDiffStat() {
        return diffStat;
    }

    public void setDiffStat(String diffStat) {
        this.diffStat = diffStat != null ? diffStat : "";
    }

    private static List<String> copy(List<String> values) {
        return values != null ? new ArrayList<>(values) : new ArrayList<>();
    }
}
