package com.openjiuwen.auto_harness.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Implement-stage output artifact.
 *
 * <p>Mirrors Python's {@code CodeChangeArtifact} in
 * {@code openjiuwen.auto_harness.schema}.</p>
 */
public class CodeChangeArtifact {

    private List<Experience> related = new ArrayList<>();
    private List<String> editedFiles = new ArrayList<>();

    public CodeChangeArtifact() {
    }

    public CodeChangeArtifact(List<Experience> related, List<String> editedFiles) {
        setRelated(related);
        setEditedFiles(editedFiles);
    }

    public List<Experience> getRelated() {
        return related;
    }

    public void setRelated(List<Experience> related) {
        this.related = related != null ? new ArrayList<>(related) : new ArrayList<>();
    }

    public List<String> getEditedFiles() {
        return editedFiles;
    }

    public void setEditedFiles(List<String> editedFiles) {
        this.editedFiles = editedFiles != null ? new ArrayList<>(editedFiles) : new ArrayList<>();
    }
}
