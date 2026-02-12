// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.schema;

import com.openjiuwen.core.common.schema.Part;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an artifact produced by an agent.
 * 
 * <p>An artifact is a named piece of output with optional description,
 * multiple content parts, and metadata.
 * 
 * <p>Python reference: {@code agent-core/openjiuwen/core/single_agent/schema/agent_result.py}
 */
public class Artifact {
    
    /**
     * Unique identifier for this artifact.
     */
    private String artifactId;
    
    /**
     * Semantic name (e.g., "summary", "chart").
     */
    private String name;
    
    /**
     * Description of this artifact.
     */
    private String description;
    
    /**
     * Content parts that make up this artifact.
     */
    private List<Part> parts;
    
    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;
    
    /**
     * Creates an empty artifact.
     */
    public Artifact() {
        this.parts = new ArrayList<>();
        this.metadata = new HashMap<>();
    }
    
    /**
     * Creates an artifact with the specified ID.
     *
     * @param artifactId the artifact ID
     */
    public Artifact(String artifactId) {
        this.artifactId = artifactId;
        this.parts = new ArrayList<>();
        this.metadata = new HashMap<>();
    }
    
    /**
     * Creates an artifact with full details.
     *
     * @param artifactId the artifact ID
     * @param name the semantic name
     * @param description the description
     */
    public Artifact(String artifactId, String name, String description) {
        this.artifactId = artifactId;
        this.name = name;
        this.description = description;
        this.parts = new ArrayList<>();
        this.metadata = new HashMap<>();
    }
    
    // Getters and Setters
    
    public String getArtifactId() {
        return artifactId;
    }
    
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<Part> getParts() {
        return parts;
    }
    
    public void setParts(List<Part> parts) {
        this.parts = parts != null ? new ArrayList<>(parts) : new ArrayList<>();
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    /**
     * Adds a part to this artifact.
     *
     * @param part the part to add
     * @return this artifact for chaining
     */
    public Artifact addPart(Part part) {
        if (part != null) {
            this.parts.add(part);
        }
        return this;
    }
    
    /**
     * Adds a metadata entry.
     *
     * @param key the metadata key
     * @param value the metadata value
     * @return this artifact for chaining
     */
    public Artifact addMetadata(String key, Object value) {
        if (key != null) {
            this.metadata.put(key, value);
        }
        return this;
    }
    
    @Override
    public String toString() {
        return String.format("Artifact{artifactId='%s', name='%s', parts=%d}", 
            artifactId, name, parts != null ? parts.size() : 0);
    }
}

