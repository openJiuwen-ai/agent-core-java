/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code Triple} in
 * {@code openjiuwen/core/retrieval/common/triple.py}.
 */
public class Triple {

    private String subject;
    private String predicate;
    private String object;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public Triple() {
    }

    public Triple(String subject, String predicate, String object) {
        this(subject, predicate, object, null);
    }

    public Triple(String subject, String predicate, String object, Map<String, Object> metadata) {
        this.subject = requireText(subject, "subject");
        this.predicate = requireText(predicate, "predicate");
        this.object = requireText(object, "object");
        setMetadata(metadata);
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getPredicate() {
        return predicate;
    }

    public void setPredicate(String predicate) {
        this.predicate = predicate;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null) {
            throw RetrievalExceptions.validation(fieldName + " is required");
        }
        return value;
    }
}
