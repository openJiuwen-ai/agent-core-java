// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.schema;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a content part in an artifact or message.
 * 
 * <p>This is a placeholder class. The Python source file does not exist,
 * but it is referenced by agent_result.py. Structure is inferred from usage.
 * 
 * <p>A Part typically contains:
 * <ul>
 *   <li>{@code type} - The content type (e.g., "text", "image", "file")</li>
 *   <li>{@code content} - The actual content</li>
 *   <li>{@code metadata} - Additional metadata</li>
 * </ul>
 */
public class Part {
    
    /**
     * The content type (e.g., "text", "image", "file").
     */
    private String type;
    
    /**
     * The actual content.
     */
    private Object content;
    
    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;
    
    /**
     * Creates an empty part.
     */
    public Part() {
        this.type = "";
        this.content = null;
        this.metadata = new HashMap<>();
    }
    
    /**
     * Creates a part with the specified type and content.
     *
     * @param type the content type
     * @param content the actual content
     */
    public Part(String type, Object content) {
        this.type = type != null ? type : "";
        this.content = content;
        this.metadata = new HashMap<>();
    }
    
    /**
     * Creates a part with full details.
     *
     * @param type the content type
     * @param content the actual content
     * @param metadata additional metadata
     */
    public Part(String type, Object content, Map<String, Object> metadata) {
        this.type = type != null ? type : "";
        this.content = content;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    // Getters and Setters
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type != null ? type : "";
    }
    
    public Object getContent() {
        return content;
    }
    
    public void setContent(Object content) {
        this.content = content;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    /**
     * Adds a metadata entry.
     *
     * @param key the metadata key
     * @param value the metadata value
     * @return this part for chaining
     */
    public Part addMetadata(String key, Object value) {
        if (key != null) {
            this.metadata.put(key, value);
        }
        return this;
    }
    
    @Override
    public String toString() {
        return String.format("Part{type='%s', content=%s}", type, content);
    }
}

